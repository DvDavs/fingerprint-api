package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.*;
import com.example.fingerprint_api.model.Empleado;
import com.example.fingerprint_api.util.CaptureThread;
import com.example.fingerprint_api.util.FingerprintImageUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger; // *** CAMBIO: Importar SLF4J Logger ***
import org.slf4j.LoggerFactory; // *** CAMBIO: Importar SLF4J LoggerFactory ***
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
// import java.util.logging.Level; // ELIMINAR
// import java.util.logging.Logger; // ELIMINAR

@Service
public class MultiReaderFingerprintService {

    // *** CAMBIO: Usar SLF4J Logger ***
    private static final Logger logger = LoggerFactory.getLogger(MultiReaderFingerprintService.class);

    // --- Variables de instancia (sin cambios) ---
    private final Map<String, Reader> validReaders = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> captureTasks = new ConcurrentHashMap<>();
    private final ExecutorService captureExecutor = Executors.newFixedThreadPool(10);
    private final Map<String, String> lastFingerprintData = new ConcurrentHashMap<>();
    private final Map<String, EnrollmentSession> enrollmentSessions = new ConcurrentHashMap<>();
    private final Set<String> inUseReaders = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, String> sessionReservations = new ConcurrentHashMap<>();

    // --- Inyecciones (sin cambios) ---
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private UserService userService;

    // --- Métodos (con logging actualizado) ---

    @PostConstruct
    public void initService() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Inicializando MultiReaderFingerprintService con enrolamiento y checador.");
        try {
            refreshConnectedReaders();
            scheduler.scheduleAtFixedRate(this::checkReaders, 30, 30, TimeUnit.SECONDS);
        } catch (UareUException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error UareU al inicializar lectores. Código: {}, Mensaje: {}", e.getCode(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error inesperado al inicializar lectores.", e);
        }
    }

    private void checkReaders() {
        try {
            refreshConnectedReaders();
        } catch (UareUException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error UareU al verificar lectores. Código: {}, Mensaje: {}", e.getCode(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error inesperado al verificar lectores.", e);
        }
    }

    public synchronized List<String> refreshConnectedReaders() throws UareUException {
        logger.debug("Refrescando lista de lectores conectados...");
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders(); // Actualiza la lista interna del SDK

        List<String> currentlyConnectedNames = new ArrayList<>();
        if (readers.isEmpty()) {
            logger.warn("No se encontraron lectores conectados por el SDK.");
        }

        // Usamos esta lista para saber cuáles de los 'validReaders' previos ya no están conectados
        List<String> readersToRemove = new ArrayList<>(validReaders.keySet());

        for (Reader r : readers) {
            String readerName = "[Error al obtener nombre]"; // Default
            Reader.Description desc = null; // Guardamos la descripción

            try {
                desc = r.GetDescription();
                if (desc == null) {
                    logger.warn("No se pudo obtener la descripción de un lector detectado.");
                    continue; // Saltar este lector si no hay descripción
                }
                readerName = desc.name;

                readersToRemove.remove(readerName); // Si está conectado, no lo eliminamos de los válidos

                if (validReaders.containsKey(readerName)) {
                    // Ya lo teníamos y sigue conectado.
                    currentlyConnectedNames.add(readerName);
                    logger.trace("Lector {} ya validado y sigue conectado.", readerName);
                    continue; // Procesar siguiente lector
                }

                // --- Es un lector nuevo o reconectado ---
                logger.debug("Nuevo lector detectado: {}. Intentando abrir y verificar...", readerName);
                Reader readerToUse = null; // Referencia al lector abierto si tiene éxito
                boolean openedSuccessfully = false;
                try {
                    // 1. Intentar Abrir PRIMERO
                    r.Open(Reader.Priority.EXCLUSIVE);
                    openedSuccessfully = true;
                    readerToUse = r; // Usar la misma instancia 'r' que abrimos
                    logger.debug("Lector {} abierto correctamente.", readerName);

                    // 2. Verificar compatibilidad (AHORA con lector abierto)
                    if (isCompatibleReader(readerToUse, desc)) { // Pasamos descripción para evitar volver a llamarla
                        logger.info("Lector {} es compatible. Añadiendo a lectores válidos.", readerName);
                        validReaders.put(readerName, readerToUse); // Añadir el lector ABIERTO
                        currentlyConnectedNames.add(readerName);
                    } else {
                        // No es compatible, cerrar el lector que abrimos
                        logger.warn("Lector {} abierto pero NO compatible. Cerrando.", readerName);
                        readerToUse.Close();
                    }
                } catch (UareUException e) {
                    // Error al abrir o durante isCompatibleReader
                    logger.warn("No se pudo abrir o verificar lector {}: Código {}, Mensaje: {}", readerName, e.getCode(), e.getMessage());
                    // Asegurarse de cerrar si se abrió pero falló después
                    if (openedSuccessfully && readerToUse != null) {
                        try { readerToUse.Close(); } catch (Exception ignore) {}
                    }
                    // Si falló Open(), openedSuccessfully será false, no hay nada que cerrar
                } catch (Exception ex) {
                    logger.error("Error inesperado procesando lector {}: {}", readerName, ex.getMessage(), ex);
                    // Intentar cerrar si se llegó a abrir
                    if (openedSuccessfully && readerToUse != null) {
                        try { readerToUse.Close(); } catch (Exception ignore) {}
                    }
                }

            } catch (Exception ex) {
                logger.error("Error inesperado al obtener descripción inicial del lector: {}", ex.getMessage(), ex);
            }
        } // Fin del bucle for

        // Detectar y manejar desconexiones (lectores que estaban en validReaders pero no en la lista actual)
        for (String readerNameToRemove : readersToRemove) {
            logger.info("Lector desconectado detectado: {}", readerNameToRemove);
            stopCaptureForReader(readerNameToRemove); // Detener tareas si las hay
            try {
                Reader removedReader = validReaders.remove(readerNameToRemove); // Remover de válidos
                if (removedReader != null) removedReader.Close(); // Cerrar
            } catch (Exception ignore) {}
            inUseReaders.remove(readerNameToRemove); // Remover de ocupados
            sessionReservations.values().remove(readerNameToRemove); // Remover reservas
        }

        logger.info("Lectores VÁLIDOS actuales: {}", validReaders.keySet());
        logger.info("Lectores OCUPADOS actuales: {}", inUseReaders);
        // Devolver los nombres de los lectores que están actualmente en el mapa de válidos
        return new ArrayList<>(validReaders.keySet());
    }

    public synchronized boolean reserveReader(String readerName, String sessionId) {
        if (sessionReservations.containsKey(sessionId)) {
            String previousReader = sessionReservations.get(sessionId);
            inUseReaders.remove(previousReader);
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Liberando lector previo: {} para sesión: {}", previousReader, sessionId);
        }
        if (validReaders.containsKey(readerName) && !inUseReaders.contains(readerName)) {
            inUseReaders.add(readerName);
            sessionReservations.put(sessionId, readerName);
            // *** CAMBIO: Logging SLF4J ***
            logger.info("[Lector reservado]: [{}] para sesión: {}", readerName, sessionId);
            return true;
        }
        // *** CAMBIO: Logging SLF4J ***
        logger.warn("No se pudo reservar lector: {} para sesión: {} (No válido o en uso)", readerName, sessionId);
        return false;
    }

    public synchronized void releaseReaderBySession(String sessionId) {
        String readerName = sessionReservations.remove(sessionId);
        if (readerName != null) {
            inUseReaders.remove(readerName);
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Lector liberado: {} por desconexión de sesión: {}", readerName, sessionId);
        }
    }

    public synchronized void releaseReader(String readerName) {
        inUseReaders.remove(readerName);
        sessionReservations.entrySet().removeIf(entry -> entry.getValue().equals(readerName));
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Lector liberado manualmente: {}", readerName);
    }

    private boolean isCompatibleReader(Reader r, Reader.Description desc) {
        // Asume que 'r' ya está abierto y 'desc' no es null
        String readerName = (desc != null && desc.name != null) ? desc.name : "[Lector Abierto]";
        try {
            // 1. Verificar Vendor (puede ser redundante pero es una doble verificación)
            String vendor = desc.id.vendor_name.toLowerCase();
            if (!vendor.contains("digitalpersona") && !vendor.contains("hid global")) {
                logger.warn("Lector {} (abierto) descartado por vendor: {}", readerName, vendor);
                return false;
            }

            // 2. Verificar Tecnología (puede ser redundante)
            if (desc.technology != Reader.Technology.HW_TECHNOLOGY_OPTICAL &&
                    desc.technology != Reader.Technology.HW_TECHNOLOGY_CAPACITIVE) {
                logger.warn("Lector {} (abierto) descartado por tecnología: {}", readerName, desc.technology);
                return false;
            }

            // 3. Verificar Capacidades (¡Ahora debería funcionar!)
            Reader.Capabilities cap = r.GetCapabilities();
            if (cap == null) {
                // Esto NO debería pasar si el lector se abrió correctamente, pero lo manejamos por si acaso
                logger.error("¡Error crítico! No se pudieron obtener las capacidades para el lector ABIERTO: {}.", readerName);
                return false;
            }

            // *** CAMBIO IMPORTANTE: Solo verificar 'can_capture' ***
            // Porque la extracción (can_extract_features) la hacemos en el servidor.
            if (!cap.can_capture) {
                logger.warn("Lector {} (abierto) no tiene la capacidad de CAPTURAR imagen requerida (can_capture: {}).",
                        readerName, cap.can_capture);
                return false;
            }

            // Log informativo si no puede extraer en hardware, pero no lo descartamos
            if (!cap.can_extract_features) {
                logger.info("Nota: Lector {} (abierto) no soporta extracción de características en hardware. La extracción se hará en software (esto es normal para U.are.U 4500).", readerName);
            }

            // Pasó todas las verificaciones necesarias
            logger.debug("Lector {} (abierto) verificado como compatible.", readerName);
            return true;

        } catch (Exception e) {
            logger.error("Error inesperado verificando compatibilidad del lector abierto {}.", readerName, e);
            return false;
        }
    }

    public synchronized void startContinuousCaptureForAll() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Intentando iniciar captura continua en todos los lectores válidos y disponibles...");
        for (Map.Entry<String, Reader> entry : validReaders.entrySet()) {
            String readerName = entry.getKey();
            if (!captureTasks.containsKey(readerName) && !inUseReaders.contains(readerName)) {
                startTaskForReader(readerName, entry.getValue(), false);
            } else {
                logger.debug("Omitiendo inicio en lector {} (ya activo o reservado).", readerName);
            }
        }
    }

    public synchronized boolean startContinuousCaptureForReader(String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Intentando iniciar captura continua en: {}", readerName);
        Reader existing = validReaders.get(readerName);
        if (existing == null) {
            logger.warn("Lector {} no encontrado en lectores válidos.", readerName);
            return false;
        }
        if (inUseReaders.contains(readerName)) {
            logger.warn("No se puede iniciar captura normal en lector {} porque está reservado.", readerName);
            return false;
        }
        if (captureTasks.containsKey(readerName)) {
            logger.info("Ya existe tarea de captura para {}", readerName);
            return true;
        }
        startTaskForReader(readerName, existing, false);
        return true;
    }

    public synchronized boolean startChecadorForReader(String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Intentando iniciar modo checador en: {}", readerName);
        Reader existing = validReaders.get(readerName);
        if (existing == null) {
            logger.warn("Lector {} no encontrado en lectores válidos.", readerName);
            return false;
        }
        String checadorSessionId = "checador_" + readerName;
        if (!reserveReader(readerName, checadorSessionId)) {
            // reserveReader ya loguea la razón
            return false;
        }

        if (captureTasks.containsKey(readerName)) {
            logger.info("Ya existe tarea de captura (checador) para {}. Asegurando reserva.", readerName);
            // La reserva ya se hizo o se reafirmó arriba
            return true;
        }
        logger.info("Iniciando tarea modo checador para lector: {}", readerName);
        startTaskForReader(readerName, existing, true);
        return true;
    }

    // --- startTaskForReader (Logging Interno Actualizado) ---
    private void startTaskForReader(String readerName, Reader reader, boolean modoChecador) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Creando tarea para: {}. Checador={}", readerName, modoChecador);
        // ... (resto del método con llamadas a logger.info/warn/error/debug como se mostró antes) ...
        final int MAX_CONSECUTIVE_ERRORS = 5;
        AtomicInteger consecutiveErrors = new AtomicInteger(0);

        Runnable captureLoop = () -> {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Iniciando bucle de captura continua en: {}", readerName);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Reader.CaptureResult cr = reader.Capture(
                            Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, -1);

                    if (cr != null && cr.image != null && cr.quality == Reader.CaptureQuality.GOOD) {
                        String base64Image = FingerprintImageUtils.convertFidToBase64(cr.image);
                        lastFingerprintData.put(readerName, base64Image);
                        String reservationId = sessionReservations.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(readerName))
                                .map(Map.Entry::getKey).findFirst().orElse("");
                        publishFingerprintEvent(readerName, reservationId, base64Image);

                        if (modoChecador) {
                            Engine engine = UareUGlobal.GetEngine();
                            Fmd fmd = engine.CreateFmd(cr.image, Fmd.Format.ANSI_378_2004);
                            Optional<Empleado> empleadoOpt = userService.identifyUser(fmd);
                            String encodedReaderName = URLEncoder.encode(readerName, StandardCharsets.UTF_8.toString());
                            String topic = "/topic/checador/" + encodedReaderName;

                            if (empleadoOpt.isPresent()) {
                                Empleado empleado = empleadoOpt.get();
                                ChecadorEvent evt = new ChecadorEvent(readerName, empleado);
                                logger.info("Empleado identificado: ID {}, Lector: {}. Enviando a topic: {}", empleado.getId(), readerName, topic);
                                messagingTemplate.convertAndSend(topic, evt);
                            } else {
                                // *** CAMBIO: Logging SLF4J ***
                                logger.debug("Huella no identificada en modo checador (Lector: {})", readerName);
                                // Enviar evento "no identificado"
                                messagingTemplate.convertAndSend(topic, new ChecadorEvent(readerName)); // Usa constructor para no identificado
                            }
                        }
                        consecutiveErrors.set(0);
                    } else {
                        if(cr != null && cr.quality == Reader.CaptureQuality.CANCELED) {
                            // *** CAMBIO: Logging SLF4J ***
                            logger.info("Captura cancelada en {}", readerName);
                            break;
                        }
                        consecutiveErrors.incrementAndGet();
                        // *** CAMBIO: Logging SLF4J ***
                        logger.warn("Captura fallida o sin dedo en {} - Calidad: {} (Error #{})",
                                readerName, (cr != null ? cr.quality : "NULL"), consecutiveErrors.get());
                    }

                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
                        // *** CAMBIO: Logging SLF4J ***
                        logger.error("Demasiados errores ({}) consecutivos en {}. Deteniendo tarea.", MAX_CONSECUTIVE_ERRORS, readerName);
                        break;
                    }
                    Thread.sleep(100);

                } catch (InterruptedException ie) {
                    // *** CAMBIO: Logging SLF4J ***
                    logger.info("Captura interrumpida en: {}", readerName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (UareUException ue) {
                    // *** CAMBIO: Logging SLF4J ***
                    logger.error("Error UareU en {}: Código {}, Mensaje: {}", readerName, ue.getCode(), ue.getMessage(), ue);
                    consecutiveErrors.incrementAndGet();
                    if(ue.getCode() == 96075807 /*URU_E_DEVICE_FAILURE*/) {
                        logger.error("Fallo de dispositivo detectado en {}. Deteniendo tarea.", readerName);
                        break;
                    }
                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) break;
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                } catch (Exception e) {
                    // *** CAMBIO: Logging SLF4J ***
                    logger.error("Error inesperado en captura de {}: {}", readerName, e.getMessage(), e);
                    consecutiveErrors.incrementAndGet();
                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) break;
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Tarea de captura finalizada para: {}", readerName);
            captureTasks.remove(readerName);
            if (modoChecador) {
                releaseReader(readerName); // Libera la reserva "checador_..."
            }
        };

        Future<?> future = captureExecutor.submit(captureLoop);
        captureTasks.put(readerName, future);
    }

    // --- doIdentifyUserChecador (Logging Actualizado) ---
    private void doIdentifyUserChecador(String readerName) { // Mantener si se necesita identificación puntual
        try {
            Fmd fmd = captureSingleFingerprintFmd(readerName);
            Optional<Empleado> empleadoOpt = userService.identifyUser(fmd);
            String encodedReaderName = URLEncoder.encode(readerName, StandardCharsets.UTF_8.toString());
            String destination = "/topic/checador/" + encodedReaderName;
            ChecadorEvent evt;

            if (empleadoOpt.isPresent()) {
                Empleado empleado = empleadoOpt.get();
                evt = new ChecadorEvent(readerName, empleado);
                // *** CAMBIO: Logging SLF4J ***
                logger.info("Usuario identificado (puntual): ID {}, Lector: {}", empleado.getId(), readerName);
            } else {
                evt = new ChecadorEvent(readerName); // Evento no identificado
                // *** CAMBIO: Logging SLF4J ***
                logger.info("Usuario no identificado (puntual) en checador (Lector: {})", readerName);
            }
            messagingTemplate.convertAndSend(destination, evt);

        } catch (UareUException | InterruptedException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.warn("Error al capturar/identificar (puntual) en checador {}: {}", readerName, e.getMessage(), e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } catch (Exception ex){
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error inesperado en identificación puntual de checador {}: {}", readerName, ex.getMessage(), ex);
        }
    }

    // --- stopContinuousCaptureForAll (Logging Actualizado) ---
    public synchronized void stopContinuousCaptureForAll() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Intentando detener todas las capturas continuas...");
        captureTasks.values().forEach(task -> task.cancel(true));
        for (Map.Entry<String, Reader> entry : validReaders.entrySet()) {
            try {
                entry.getValue().CancelCapture();
            } catch (UareUException e) {
                if(e.getCode() != 15302914) { // Loguear solo si no es error esperado al cancelar
                    // *** CAMBIO: Logging SLF4J ***
                    logger.warn("Error al cancelar captura SDK en {}: Código {}, Mensaje {}", entry.getKey(), e.getCode(), e.getMessage());
                }
            } catch (Exception e) {
                logger.warn("Error inesperado al cancelar captura SDK en {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        captureTasks.clear();
        sessionReservations.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("checador_"))
                .map(Map.Entry::getValue).distinct()
                .forEach(this::releaseReader);
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Todas las capturas continuas detenidas.");
    }

    // --- stopCaptureForReader (Logging Actualizado) ---
    public synchronized void stopCaptureForReader(String readerName) {
        Future<?> task = captureTasks.remove(readerName);
        if (task != null) {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Deteniendo tarea de captura para: {}", readerName);
            task.cancel(true);
            Reader reader = validReaders.get(readerName);
            if (reader != null) {
                try {
                    reader.CancelCapture();
                } catch (UareUException e) {
                    if(e.getCode() != 15302914) {
                        // *** CAMBIO: Logging SLF4J ***
                        logger.warn("Error al cancelar captura SDK en {}: Código {}, Mensaje {}", readerName, e.getCode(), e.getMessage());
                    }
                } catch (Exception e) {
                    logger.warn("Error inesperado al cancelar captura SDK en {}: {}", readerName, e.getMessage(), e);
                }
            }
            String checadorSessionId = "checador_" + readerName;
            if (sessionReservations.containsKey(checadorSessionId)) {
                releaseReader(readerName);
            }
        } else {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("No había tarea de captura activa para: {}", readerName);
        }
    }

    // --- captureSingleFingerprintFmd (Logging Actualizado) ---
    public Fmd captureSingleFingerprintFmd(String readerName) throws UareUException, InterruptedException {
        // ... (lógica igual, pero los logs internos usarán SLF4J) ...
        Reader reader = validReaders.get(readerName);
        if (reader == null) {
            logger.warn("Intento de captura puntual en lector no válido/encontrado: {}", readerName);
            throw new UareUException(96075797);
        }
        // ... (verificación de reserva igual) ...
        boolean reservedByOther = sessionReservations.entrySet().stream()
                .anyMatch(entry -> entry.getValue().equals(readerName) && !entry.getKey().startsWith("checador_"));
        /*if (inUseReaders.contains(readerName) && reservedByOther) {
            logger.warn("Intento de captura puntual en lector {} que está reservado por otra sesión.", readerName);
            throw new UareUException(15302914);
        }*/

        logger.debug("Realizando captura puntual en: {}", readerName);
        CaptureThread capture = new CaptureThread(reader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 8000 );
        capture.start(null);
        capture.join(9000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();

        if (event != null && event.capture_result != null && event.capture_result.image != null && event.capture_result.quality == Reader.CaptureQuality.GOOD) {
            Engine engine = UareUGlobal.GetEngine();
            return engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
        } else {
            String reason = "Razón desconocida";
            // ... (código para determinar la razón) ...
            if (event == null) reason = "Evento nulo";
            else if (event.capture_result == null) reason = "CaptureResult nulo";
            else if (event.capture_result.image == null) reason = "Imagen nula";
            else if (event.capture_result.quality != Reader.CaptureQuality.GOOD) reason = "Calidad insuficiente: " + event.capture_result.quality;
            else if (event.exception != null) reason = "Excepción: " + event.exception.getMessage();

            // *** CAMBIO: Logging SLF4J ***
            logger.warn("Captura puntual fallida en {}: {}", readerName, reason);
            // Lanzar excepción UareU apropiada o genérica si event.exception es null
            if(event != null && event.exception instanceof UareUException) throw (UareUException) event.exception;
            if(event != null && event.capture_result != null && event.capture_result.quality == Reader.CaptureQuality.TIMED_OUT) throw new UareUException(96075788); // TIMEOUT (ejemplo)
            throw new UareUException(96075787); // FAILURE genérico
        }
    }

    // --- startEnrollment (Logging Actualizado) ---
    public synchronized String startEnrollment(String readerName) throws UareUException {
        // ... (verificaciones iguales) ...
        if (!validReaders.containsKey(readerName)) {
            logger.warn("Intento de iniciar enrolamiento en lector no válido: {}", readerName);
            throw new UareUException(96075797); // READER_NOT_FOUND
        }
        boolean reservedByOther = sessionReservations.entrySet().stream()
                .anyMatch(entry -> entry.getValue().equals(readerName) && !entry.getKey().startsWith("checador_"));
        /*if (inUseReaders.contains(readerName) && reservedByOther) {
            logger.warn("Intento de iniciar enrolamiento en lector {} que está reservado.", readerName);
            throw new UareUException(15302914); // UAREU_E_INVALID_OPERATION
        }*/

        String sessionId = UUID.randomUUID().toString();
        EnrollmentSession session = new EnrollmentSession(sessionId);
        enrollmentSessions.put(sessionId, session);
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Enrolamiento iniciado. SessionId: {} Lector: {}", sessionId, readerName);
        return sessionId;
    }

    // --- captureForEnrollment (Logging Actualizado) ---
    public Map<String, Object> captureForEnrollment(String readerName, String sessionId) throws UareUException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            // *** CAMBIO: Logging SLF4J ***
            logger.warn("Intento de captura para sesión de enrolamiento inválida: {}", sessionId);
            throw new UareUException(96075796);
        }

        Fmd fmd = captureSingleFingerprintFmd(readerName);
        boolean complete = session.addCapture(fmd);

        if (complete) {
            Fmd enrollmentFmd = session.createEnrollmentFmd();
            enrollmentSessions.remove(sessionId);
            response.put("complete", true);
            response.put("template", Base64.getEncoder().encodeToString(enrollmentFmd.getData()));
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Enrolamiento completado para sesión {}. Template listo.", sessionId);
        } else {
            int remaining = session.getRequired() - session.getCaptureCount();
            response.put("complete", false);
            response.put("remaining", remaining);
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Enrolamiento en progreso para sesión {}. Faltan {} capturas.", sessionId, remaining);
        }
        return response;
    }

    // --- EnrollmentSession / EnrollmentCallbackImpl (Logging Interno Actualizado) ---
    private class EnrollmentSession {
        // ... (campos iguales) ...
        private final List<Fmd> fmds = new ArrayList<>();
        private final int required = 4;
        private final String id;

        EnrollmentSession(String id) { this.id = id; }
        boolean addCapture(Fmd fmd) {
            fmds.add(fmd);
            // *** CAMBIO: Logging SLF4J ***
            logger.debug("Captura añadida a sesión {}, Total={}", id, fmds.size()); // Cambiado a debug
            return fmds.size() >= required;
        }
        // ... (getters iguales) ...
        int getCaptureCount() { return fmds.size(); }
        int getRequired() { return required; }
        Fmd createEnrollmentFmd() throws UareUException {
            // *** CAMBIO: Logging SLF4J ***
            logger.debug("Creando FMD final de enrolamiento para sesión {}", id); // Cambiado a debug
            Engine engine = UareUGlobal.GetEngine();
            return engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, new EnrollmentCallbackImpl(fmds));
        }
    }
    private class EnrollmentCallbackImpl implements Engine.EnrollmentCallback {
        // ... (código igual, sin logs internos) ...
        private final Iterator<Fmd> iterator;
        EnrollmentCallbackImpl(List<Fmd> fmds) { this.iterator = fmds.iterator(); }
        @Override
        public Engine.PreEnrollmentFmd GetFmd(Fmd.Format format) {
            if (iterator.hasNext()) {
                Fmd fmd = iterator.next();
                Engine.PreEnrollmentFmd pre = new Engine.PreEnrollmentFmd();
                pre.fmd = fmd;
                pre.view_index = 0;
                return pre;
            }
            return null;
        }
    }

    // --- shutdown (Logging Actualizado) ---
    public void shutdown() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Iniciando apagado de MultiReaderFingerprintService...");
        stopContinuousCaptureForAll();
        scheduler.shutdown();
        for (Reader reader : validReaders.values()) {
            try { reader.Close(); } catch (UareUException e) {
                // *** CAMBIO: Logging SLF4J ***
                logger.warn("Error al cerrar lector durante apagado: Código {}, Mensaje {}", e.getCode(), e.getMessage());
            } catch (Exception e) {
                logger.warn("Error inesperado al cerrar lector durante apagado.", e);
            }
        }
        validReaders.clear();
        captureExecutor.shutdown();
        try {
            if (!captureExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                captureExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            captureExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // *** CAMBIO: Logging SLF4J ***
        logger.info("MultiReaderFingerprintService apagado.");
    }


    public static class ChecadorEvent { /* ... código actualizado previamente ... */
        public String readerName;
        public boolean identificado;
        public Integer empleadoId;
        public String nombreCompleto;
        public String rfc;
        // Constructor para éxito
        public ChecadorEvent(String readerName, Empleado empleado) {
            this.readerName = readerName;
            if (empleado != null) {
                this.identificado = true;
                this.empleadoId = empleado.getId();
                this.nombreCompleto = empleado.getNombreCompleto();
                this.rfc = empleado.getRfc();
            } else { // Seguridad por si acaso
                this.identificado = false;
            }
        }
        // Constructor para no identificado
        public ChecadorEvent(String readerName) {
            this.readerName = readerName;
            this.identificado = false;
            this.empleadoId = null;
            this.nombreCompleto = "No Identificado";
            this.rfc = null;
        }
    }

    // --- Métodos restantes (isCaptureActive, getLastCapturedFingerprint, getValidReaderNames, getAvailableReaderNames): Sin cambios lógicos ---
    // ... (código sin cambios lógicos, ya usan logging SLF4J si aplica) ...
    public synchronized boolean isCaptureActive(String readerName) {
        Future<?> task = captureTasks.get(readerName);
        return task != null && !task.isDone();
    }
    public synchronized String getLastCapturedFingerprint(String readerName) {
        return lastFingerprintData.get(readerName);
    }
    public synchronized Set<String> getValidReaderNames() {
        return new HashSet<>(validReaders.keySet());
    }
    public synchronized Set<String> getAvailableReaderNames() {
        Set<String> result = new HashSet<>(validReaders.keySet());
        result.removeAll(inUseReaders);
        return result;
    }

    private void publishFingerprintEvent(String readerName, String reservationId, String base64Fingerprint) {
        // Asegurarse que reservationId no sea null para el path, usar "" si es null o vacío
        String safeReservationId = (reservationId == null || reservationId.isEmpty()) ? "_" : reservationId; // Usar "_" u otro placeholder si está vacío

        // Destino: /topic/fingerprints/{reservationId}/{readerName}
        // Ejemplo: /topic/fingerprints/session123/LectorHuellas01
        // Ejemplo: /topic/fingerprints/_/LectorHuellas01 (si no hay reserva)
        String destination = "/topic/fingerprints/" + safeReservationId + "/" + readerName;

        // Crear el payload usando la clase interna actualizada
        var payload = new FingerprintImageEvent(readerName, safeReservationId, base64Fingerprint);

        // Enviar por WebSocket
        messagingTemplate.convertAndSend(destination, payload);
        logger.debug("Evento de imagen enviado a {}", destination); // Log opcional
    }

    // Asegúrate que la clase interna FingerprintImageEvent esté definida (ya la incluí antes):
    public static class FingerprintImageEvent {
        public String readerName;
        public String reservationId;
        public String base64Image;

        public FingerprintImageEvent(String readerName, String reservationId, String base64Image) {
            this.readerName = readerName;
            this.reservationId = reservationId;
            this.base64Image = base64Image;
        }
    }

} // Fin de la clase MultiReaderFingerprintService