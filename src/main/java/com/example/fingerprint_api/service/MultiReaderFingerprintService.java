package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.*;
import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.repository.UserRepository;
import com.example.fingerprint_api.util.CaptureThread;
import com.example.fingerprint_api.util.FingerprintImageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Servicio multi-lector: captura continua, captura puntual, enrolamiento multi-paso,
 * filtrado de lectores compatibles y modo "Reloj Checador".
 */
@Service
public class MultiReaderFingerprintService {

    private static final Logger logger = Logger.getLogger(MultiReaderFingerprintService.class.getName());

    // Mapa: "readerName" -> Reader
    private final Map<String, Reader> validReaders = new ConcurrentHashMap<>();

    // Mapa: "readerName" -> Tarea de captura continua
    private final Map<String, Future<?>> captureTasks = new ConcurrentHashMap<>();

    // Pool de hilos para capturas concurrentes
    private static final int MAX_CAPTURE_THREADS = 10;
    private final ExecutorService captureExecutor = Executors.newFixedThreadPool(MAX_CAPTURE_THREADS);

    // Mapa: "readerName" -> última huella capturada en Base64
    private final Map<String, String> lastFingerprintData = new ConcurrentHashMap<>();

    // Mapa de "sessionId" -> "EnrollmentSession" para enrolamiento multi-paso
    private final Map<String, EnrollmentSession> enrollmentSessions = new ConcurrentHashMap<>();

    // Set para marcar lectores "en uso" y que no se muestren como disponibles en la selección
    private final Set<String> inUseReaders = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Map<String, String> sessionReservations = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    public UserService userService; // Para identificar usuario en el "checador"

    @Autowired
    private UserRepository userRepository; // Si necesitas datos del usuario en el checador

    @PostConstruct
    public void initService() {
        logger.info("Inicializando MultiReaderFingerprintService con enrolamiento y checador.");
        try {
            refreshConnectedReaders();
            // Programar verificación cada 30 segundos
            scheduler.scheduleAtFixedRate(this::checkReaders, 30, 30, TimeUnit.SECONDS);
        } catch (UareUException e) {
            logger.severe("Error al inicializar lectores: " + e.getMessage());
        }
    }

    private void checkReaders() {
        try {
            List<String> currentReaders = refreshConnectedReaders();
            /*
            // Detener tareas de lectores que ya no están presentes
            for (String readerName : new ArrayList<>(captureTasks.keySet())) {
                if (!currentReaders.contains(readerName)) {
                    logger.info("Lector desconectado detectado: " + readerName);
                    stopCaptureForReader(readerName);
                    validReaders.remove(readerName); // Eliminar de la lista de lectores válidos
                }
            }*/
        } catch (UareUException e) {
            logger.severe("Error al verificar lectores: " + e.getMessage());
        }
    }
    /**
     * Refresca los lectores del sistema y abre sólo aquellos que sean:
     *  1) De DigitalPersona (U.are.U)
     *  2) No estén ya marcados como "in use"
     *  3) Tengan la tecnología/capabilities adecuadas.
     */
    public synchronized List<String> refreshConnectedReaders() throws UareUException {
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders(); // actualizamos la lista del SDK

        List<String> currentlyConnected = new ArrayList<>();

        for (Reader r : readers) {
            String name = r.GetDescription().name;

            // 1) Verificar compatibilidad
            if (!isCompatibleReader(r)) {
                logger.info("Omitiendo lector NO compatible: " + name);
                continue;
            }

            // 2) Si no lo teníamos en validReaders, lo abrimos y agregamos
            if (!validReaders.containsKey(name)) {
                try {
                    // Primero cerramos por si estaba en un estado previo
                    r.Close();
                } catch (Exception ignore) {}

                try {
                    r.Open(Reader.Priority.EXCLUSIVE);
                    validReaders.put(name, r);
                    logger.info("Lector abierto y agregado: " + name);
                } catch (UareUException e) {
                    logger.warning("No se pudo abrir lector: " + name + " - " + e.getMessage());
                    continue;
                }
            }

            currentlyConnected.add(name);
        }

        // 3) Detectar desconexiones físicas (los que ya no aparecieron en el SDK)
        for (String knownName : new ArrayList<>(validReaders.keySet())) {
            if (!currentlyConnected.contains(knownName)) {
                logger.info("Lector desconectado detectado: " + knownName);
                // Paramos captura y lo removemos
                stopCaptureForReader(knownName);
                try {
                    validReaders.get(knownName).Close();
                } catch (Exception ignore) {}
                validReaders.remove(knownName);
                // Importante: también sacarlo de "inUseReaders" si estaba ahí
                inUseReaders.remove(knownName);
            }
        }

        logger.info("Lectores VÁLIDOS actuales: " + validReaders.keySet());
        logger.info("Lectores OCUPADOS actuales: " + inUseReaders.toString());
        return currentlyConnected;
    }

    /**
     * Marca un lector como "en uso" para que no aparezca en la lista de autoSelectReaders().
     */
    public synchronized boolean reserveReader(String readerName, String sessionId) {
        if (validReaders.containsKey(readerName) && !inUseReaders.contains(readerName)) {
            inUseReaders.add(readerName);
            sessionReservations.put(sessionId, readerName);
            logger.info("[Lector reservado]: [" + readerName + "] para sesión: " + sessionId);
            logger.info("info lector");
            return true;
        }
        return false;
    }


    public synchronized void releaseReaderBySession(String sessionId) {
        String readerName = sessionReservations.remove(sessionId);
        if (readerName != null) {
            inUseReaders.remove(readerName);
            logger.info("Lector liberado: " + readerName + " por desconexión de sesión: " + sessionId);
        }
    }


    /**
     * Liberar un lector que estaba en uso, para que pueda mostrarse de nuevo.
     */
    public synchronized void releaseReader(String readerName) {
        inUseReaders.remove(readerName);
        logger.info("Lector liberado: " + readerName);
    }

    /**
     * Lógica para filtrar los lectores de DigitalPersona U.are.U
     * Ejemplo: checamos vendor_name contenga "DigitalPersona" o technology = TOUCH
     */
    private boolean isCompatibleReader(Reader r) {
        Reader.Description desc = r.GetDescription();
        if (desc == null) return false;

        // Ejemplo 1: Comprobar vendor_name contenga "DigitalPersona" o "HID" (según tu hardware).
        String vendor = desc.id.vendor_name.toLowerCase();
        if (!vendor.contains("digitalpersona")) {
            // Podrías también aceptar "hid" si es un lector HID-DigitalPersona
            if (!vendor.contains("hid")) {
                return false;
            }
        }

        // Ejemplo 2: Checar technology
        if (desc.technology != Reader.Technology.HW_TECHNOLOGY_OPTICAL) {
            // si no es TOUCH, lo descartamos
            return false;
        }

        // Podrías checar más cosas, p.e.:
        // Reader.Capabilities cap = r.GetCapabilities();
        // if (!cap.can_capture || !cap.can_extract_features) return false;

        return true;
    }

    /**
     * Inicia captura continua en TODOS los lectores válidos (y no en uso).
     */
    public synchronized void startContinuousCaptureForAll() {
        logger.info("[startContinuousCaptureForAll] Iniciando captura en todos...");
        for (Map.Entry<String, Reader> entry : validReaders.entrySet()) {
            String readerName = entry.getKey();
            if (!captureTasks.containsKey(readerName)) {
                startTaskForReader(readerName, entry.getValue(), false);
            }
        }
    }

    /**
     * Inicia captura continua para un lector específico (modo "normal", sin checador).
     */
public synchronized boolean startContinuousCaptureForReader(String readerName) {
    logger.info("[startContinuousCaptureForReader] Iniciando en: " + readerName);
    Reader existing = validReaders.get(readerName);
    if (existing == null) {
        logger.info("Lector " + readerName + " no encontrado. Se refresca la lista...");
        try {
            refreshConnectedReaders();
        } catch (UareUException e) {
            logger.severe("Error al refrescar lectores: " + e.getMessage());
            return false;
        }
        existing = validReaders.get(readerName);
        if (existing == null) {
            logger.warning("Lector " + readerName + " no disponible tras refrescar.");
            return false;
        }
    }
    if (captureTasks.containsKey(readerName)) {
        logger.info("Ya existe tarea de captura para " + readerName);
        return true;
    }
    startTaskForReader(readerName, existing, false);
    return true;
}

    /**
     * Inicia la captura continua en "modo checador":
     *   - Cada vez que se detecta una huella, se intenta identificar con la BD de usuarios
     *   - Si se reconoce, se envía un evento WebSocket con la info del usuario a "/topic/checador"
     */
    public synchronized boolean startChecadorForReader(String readerName) {
        Reader existing = validReaders.get(readerName);
        if (existing == null) {
            try {
                refreshConnectedReaders();
            } catch (UareUException e) {
                logger.severe("Error en autoSelectReaders: " + e.getMessage());
                return false;
            }
            existing = validReaders.get(readerName);
            if (existing == null) {
                logger.warning("Lector " + readerName + " no disponible.");
                return false;
            }
        }
        if (captureTasks.containsKey(readerName)) {
            logger.info("Ya existe tarea de captura (checador) para " + readerName);
            return true;
        }
        // Creamos la tarea con "modoChecador = true"
        logger.info("[startChecadorForReader] Lector: " + readerName);
        // Para el modo checador se reserva sin sessionId (o se podría ampliar)
        reserveReader(readerName, "checador");
        startTaskForReader(readerName, existing, true);
        return true;
    }

    /**
     * Lanza una tarea de captura continua para un lector.
     * @param modoChecador si es true, se hace la identificación en cada captura y se publica /topic/checador
     */
    private void startTaskForReader(String readerName, Reader reader, boolean modoChecador) {
        logger.info("[startTaskForReader] Creando tarea para: " + readerName + ". Checador=" + modoChecador);

        final int MAX_CONSECUTIVE_ERRORS = 5;
        AtomicInteger consecutiveErrors = new AtomicInteger(0);

        Runnable captureLoop = () -> {
            logger.info("Iniciando captura continua en: " + readerName);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Intentamos capturar
                    Reader.CaptureResult cr = reader.Capture(
                            Fid.Format.ANSI_381_2004,
                            Reader.ImageProcessing.IMG_PROC_DEFAULT,
                            500,   // DPI
                            -1   // timeout
                    );

                    if (cr != null && cr.image != null && cr.quality == Reader.CaptureQuality.GOOD) {
                        // Convertir a base64 (notificación de imagen):
                        String base64Image = FingerprintImageUtils.convertFidToBase64(cr.image);
                        lastFingerprintData.put(readerName, base64Image);
                        publishFingerprintEvent(readerName, "", base64Image);

                        if (modoChecador) {
                            // Identificar de una vez
                            Engine engine = UareUGlobal.GetEngine();
                            Fmd fmd = engine.CreateFmd(cr.image, Fmd.Format.ANSI_378_2004);
                            Optional<User> userOpt = userService.identifyUser(fmd);
                            if (userOpt.isPresent()) {
                                User user = userOpt.get();
                                ChecadorEvent evt = new ChecadorEvent(readerName, user);
                                // Codificar el readerName para el tópico
                                String encodedReaderName = URLEncoder.encode(readerName, StandardCharsets.UTF_8.toString());
                                String topic = "/topic/checador/" + encodedReaderName;
                                logger.info("Enviando a topic: " + topic);
                                messagingTemplate.convertAndSend(topic, evt);
                                messagingTemplate.convertAndSend("/topic/checador/" + readerName, evt);
                                logger.info("Usuario identificado en checador: "
                                        + user.getName() + " (reader: " + readerName + ")");

                            }

                        }


                        consecutiveErrors.set(0);
                    } else {
                        // Alguna falla de calidad, timout, etc.
                        consecutiveErrors.incrementAndGet();
                        logger.warning("Captura fallida o sin dedo en " + readerName
                                + " - Calidad: " + (cr != null ? cr.quality : "NULL"));
                    }

                    // Romper si hay demasiados errores seguidos
                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
                        logger.severe("Demasiados errores consecutivos en "
                                + readerName + ". Deteniendo tarea.");
                        break;
                    }

                    // Pequeña pausa antes de la siguiente captura
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    logger.info("Captura interrumpida en: " + readerName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (UareUException ue) {
                    logger.severe("Error UareU en " + readerName + ": " + ue.getMessage());
                    consecutiveErrors.incrementAndGet();
                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
                        break;
                    }
                } catch (Exception e) {
                    logger.severe("Error inesperado en captura de " + readerName + ": " + e.getMessage());
                    consecutiveErrors.incrementAndGet();
                    if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
                        break;
                    }
                }
            }
            // Al salir del while, limpiamos la tarea
            logger.info("Tarea de captura finalizada para: " + readerName);
            captureTasks.remove(readerName);
        };

        // Lanzar en el pool
        Future<?> future = captureExecutor.submit(captureLoop);
        captureTasks.put(readerName, future);
    }

    /**
     * Llama a userService.identifyUser(readerName) en modo puntual.
     * Si encuentra usuario, publica en /topic/checador un ChecadorEvent con la info del usuario.
     */
    private void doIdentifyUserChecador(String readerName) {
        try {
            Fmd fmd = captureSingleFingerprintFmd(readerName);
            Optional<User> userOpt = userService.identifyUser(fmd);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // Publicar info a /topic/checador/<readerName>
                ChecadorEvent evt = new ChecadorEvent(readerName, user);
                String destination = "/topic/checador/" + readerName;
                messagingTemplate.convertAndSend(destination, evt);
                logger.info("Usuario identificado en checador: " + user.getName() + " (reader: " + readerName + ")");
            }
        } catch (Exception ex) {
            logger.warning("No se pudo identificar en checador: " + ex.getMessage());
        }
    }

    /**
     * Detiene la captura continua en TODOS los lectores.
     */
    public synchronized void stopContinuousCaptureForAll() {
        logger.info("[stopContinuousCaptureForAll] Deteniendo todas las capturas...");
        for (Map.Entry<String, Future<?>> entry : captureTasks.entrySet()) {
            String readerName = entry.getKey();
            Future<?> task = entry.getValue();
            task.cancel(true);
            try {
                validReaders.get(readerName).CancelCapture();
            } catch (UareUException e) {
                logger.warning("Error al cancelar captura en " + readerName + ": " + e.getMessage());
            }
        }
        captureTasks.clear();
    }

    /**
     * Detiene la captura continua para un lector específico.
     */
    public synchronized void stopCaptureForReader(String readerName) {
        Future<?> task = captureTasks.remove(readerName);
        if (task != null) {
            logger.info("[stopCaptureForReader] Deteniendo tarea de: " + readerName);
            task.cancel(true);
            try {
                validReaders.get(readerName).CancelCapture();
            } catch (UareUException e) {
                logger.warning("Error al cancelar captura en " + readerName + ": " + e.getMessage());
            }
        } else {
            logger.info("No había tarea de captura para: " + readerName);
        }
    }

    /**
     * Indica si la captura está activa para un lector.
     */
    public synchronized boolean isCaptureActive(String readerName) {
        return captureTasks.containsKey(readerName) && !captureTasks.get(readerName).isDone();
    }

    /**
     * Devuelve la última huella capturada (en Base64) para un lector.
     */
    public synchronized String getLastCapturedFingerprint(String readerName) {
        return lastFingerprintData.get(readerName);
    }

    /**
     * Devuelve la lista de nombres de lectores válidos (incluyendo los que pudieran estar en uso).
     * Si quieres ocultar los que están en uso, filtrarlo desde el Controller o aquí.
     */
    public synchronized Set<String> getValidReaderNames() {
        return validReaders.keySet();
    }

    /**
     * Realiza una sola captura y regresa la FMD resultante (para verificación/identificación puntual).
     */
    public Fmd captureSingleFingerprintFmd(String readerName) throws UareUException, InterruptedException {
        Reader reader = validReaders.get(readerName);
        if (reader == null) {
            refreshConnectedReaders();
            reader = validReaders.get(readerName);
            if (reader == null) {
                throw new UareUException(96075797); // "Reader no encontrado"
            }
        }
        // Hacer una sola captura
        CaptureThread capture = new CaptureThread(
                reader,
                false,
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT,
                500,
                8000
        );
        capture.start(null);
        capture.join(8000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            return engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
        }
        throw new UareUException(96075788); // "No se obtuvo imagen"
    }

    /**
     * Captura un Fid -> lo convierte a Base64. Para la captura continua "normal".
     */
    private String captureOnce(Reader reader) throws Exception {
        CaptureThread capture = new CaptureThread(
                reader,
                false,
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT,
                500,
                8000
        );
        capture.start(null);
        capture.join(8000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            return FingerprintImageUtils.convertFidToBase64(event.capture_result.image);
        }
        return null;
    }

    /**
     * Publica la huella capturada vía WebSocket en /topic/fingerprints/{readerName}.
     */
    private void publishFingerprintEvent(String readerName, String reservationId, String base64Fingerprint) {
        String destination = "/topic/fingerprints/" + readerName + "/" + reservationId;
        var payload = new FingerprintEvent(readerName, base64Fingerprint);
        messagingTemplate.convertAndSend(destination, payload);
    }


    // ==================== SECCIÓN DE ENROLAMIENTO MULTI-PASO ====================

    /**
     * Inicia una sesión de enrolamiento para un lector. Retorna sessionId (UUID).
     */
    public synchronized String startEnrollment(String readerName) throws UareUException {
        if (!validReaders.containsKey(readerName)) {
            refreshConnectedReaders();
            if (!validReaders.containsKey(readerName)) {
                throw new UareUException(96075797);
            }
        }
        String sessionId = UUID.randomUUID().toString();
        EnrollmentSession session = new EnrollmentSession(sessionId);
        enrollmentSessions.put(sessionId, session);
        logger.info("Enrolamiento iniciado. sessionId=" + sessionId + " lector=" + readerName);
        return sessionId;
    }

    /**
     * Captura una huella y la añade a la sesión de enrolamiento. Si finaliza, retorna template en base64.
     */
    public Map<String, Object> captureForEnrollment(String readerName, String sessionId) throws UareUException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            throw new UareUException(96075796); // "Sesión no válida"
        }

        // Capturar FMD
        Fmd fmd = captureSingleFingerprintFmd(readerName);
        boolean complete = session.addCapture(fmd);

        if (complete) {
            Fmd enrollmentFmd = session.createEnrollmentFmd();
            enrollmentSessions.remove(sessionId);
            response.put("complete", true);
            response.put("template", Base64.getEncoder().encodeToString(enrollmentFmd.getData()));
            logger.info("Enrolamiento completado. sessionId=" + sessionId);
        } else {
            int remaining = session.getRequired() - session.getCaptureCount();
            response.put("complete", false);
            response.put("remaining", remaining);
            logger.info("Enrolamiento en progreso. Faltan " + remaining + " capturas. sessionId=" + sessionId);
        }

        return response;
    }

    // Clase interna para administrar la lógica de enroll
    private class EnrollmentSession {
        private final List<Fmd> fmds = new ArrayList<>();
        private final int required = 4; // Por ejemplo, 4 capturas
        private final String id;

        EnrollmentSession(String id) {
            this.id = id;
        }

        boolean addCapture(Fmd fmd) {
            fmds.add(fmd);
            logger.info("Captura añadida a la sesión " + id + ". Tamaño=" + fmds.size());
            return fmds.size() >= required;
        }

        int getCaptureCount() {
            return fmds.size();
        }

        int getRequired() {
            return required;
        }

        Fmd createEnrollmentFmd() throws UareUException {
            logger.info("Creando FMD final de enrolamiento para " + id);
            Engine engine = UareUGlobal.GetEngine();
            Fmd enrollmentFmd = engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, new EnrollmentCallbackImpl(fmds));
            return enrollmentFmd;
        }
    }

    // Clase interna callback de enrolamiento
    private class EnrollmentCallbackImpl implements Engine.EnrollmentCallback {
        private final Iterator<Fmd> iterator;

        EnrollmentCallbackImpl(List<Fmd> fmds) {
            iterator = fmds.iterator();
        }

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

    // ==================== FIN SECCIÓN ENROLAMIENTO ====================

    /**
     * Cierra todos los lectores y el pool de hilos al destruir el servicio.
     */
    public void shutdown() {
        stopContinuousCaptureForAll();
        for (Reader reader : validReaders.values()) {
            try {
                reader.Close();
            } catch (UareUException e) {
                logger.warning("Error al cerrar lector: " + e.getMessage());
            }
        }
        captureExecutor.shutdown();
    }

    /**
     * Evento de huella en base64 para la captura continua normal.
     */
    public static class FingerprintEvent {
        public String readerName;
        public String base64;

        public FingerprintEvent(String readerName, String base64) {
            this.readerName = readerName;
            this.base64 = base64;
        }
    }

    /**
     * Evento de identificación en modo Checador.
     */
    public static class ChecadorEvent {
        public String readerName;
        public Long userId;
        public String userName;
        public String userEmail;

        public ChecadorEvent(String readerName, User user) {
            this.readerName = readerName;
            this.userId = user.getId();
            this.userName = user.getName();
            this.userEmail = user.getEmail();
        }
    }

    public synchronized Set<String> getAvailableReaderNames() {
        Set<String> result = new HashSet<>(validReaders.keySet());
        result.removeAll(inUseReaders);
        return result;
    }
}
