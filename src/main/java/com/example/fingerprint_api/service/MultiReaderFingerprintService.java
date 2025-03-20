package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.*;
import com.example.fingerprint_api.util.CaptureThread;
import com.example.fingerprint_api.util.FingerprintImageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Servicio para manejar múltiples lectores simultáneamente, utilizando un pool de threads
 * para la captura continua sin necesidad de una clase separada como ContinuousCaptureThread.
 */
@Service
public class MultiReaderFingerprintService {

    private static final Logger logger = Logger.getLogger(MultiReaderFingerprintService.class.getName());

    // Mapa: "readerName" -> "Reader" (lectores válidos)
    private final Map<String, Reader> validReaders = new ConcurrentHashMap<>();

    // Mapa: "readerName" -> Tarea de captura continua (Future para control)
    private final Map<String, Future<?>> captureTasks = new ConcurrentHashMap<>();

    // Pool de hilos para capturas concurrentes
    private static final int MAX_CAPTURE_THREADS = 10;
    private final ExecutorService captureExecutor = Executors.newFixedThreadPool(MAX_CAPTURE_THREADS);

    // Mapa: "readerName" -> última huella capturada en Base64
    private final Map<String, String> lastFingerprintData = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public MultiReaderFingerprintService() {
        logger.info("Inicializando MultiReaderFingerprintService.");
    }

    /**
     * Revisa todos los lectores del sistema, intenta abrirlos y los agrega a validReaders.
     */
    public synchronized List<String> autoSelectReaders() throws UareUException {
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders();
        List<String> resultNames = new ArrayList<>();
        for (Reader r : readers) {
            String name = r.GetDescription().name;
            if (validReaders.containsKey(name)) {
                resultNames.add(name);
                continue;
            }
            try {
                r.Close();
            } catch (Exception ignore) {}
            try {
                r.Open(Reader.Priority.EXCLUSIVE);
                validReaders.put(name, r);
                resultNames.add(name);
            } catch (UareUException e) {
                logger.warning("No se pudo abrir lector: " + name);
            }
        }
        return resultNames;
    }

    /**
     * Inicia la captura continua en TODOS los lectores válidos que no tengan tarea activa.
     */
    public synchronized void startContinuousCaptureForAll() {
        logger.info("[startContinuousCaptureForAll] Iniciando captura para todos los lectores...");
        for (Map.Entry<String, Reader> entry : validReaders.entrySet()) {
            String readerName = entry.getKey();
            if (!captureTasks.containsKey(readerName)) {
                startTaskForReader(readerName, entry.getValue());
            }
        }
    }

    /**
     * Inicia la captura continua para un lector específico.
     */
    public synchronized boolean startContinuousCaptureForReader(String readerName) {
        logger.info("[startContinuousCaptureForReader] Iniciando para: " + readerName);
        Reader existing = validReaders.get(readerName);
        if (existing == null) {
            logger.info("Lector " + readerName + " no encontrado. Refrescando lista...");
            try {
                autoSelectReaders();
            } catch (UareUException e) {
                logger.severe("Error al refrescar lectores: " + e.getMessage());
                return false;
            }
            existing = validReaders.get(readerName);
            if (existing == null) {
                logger.warning("Lector " + readerName + " no disponible.");
                return false;
            }
        }
        if (captureTasks.containsKey(readerName)) {
            logger.info("Ya existe tarea para " + readerName);
            return true;
        }
        startTaskForReader(readerName, existing);
        return true;
    }

    /**
     * Lanza una tarea de captura continua para un lector.
     */
    private void startTaskForReader(String readerName, Reader reader) {
        logger.info("[startTaskForReader] Creando tarea para: " + readerName);
        Runnable captureTask = () -> {
            logger.info("Iniciando captura continua en: " + readerName);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String base64Image = captureOnce(reader);
                    if (base64Image != null && !base64Image.isEmpty()) {
                        lastFingerprintData.put(readerName, base64Image);
                        publishFingerprintEvent(readerName, base64Image);
                    }
                    Thread.sleep(500); // Delay ajustable entre capturas
                } catch (InterruptedException e) {
                    logger.info("Captura interrumpida en: " + readerName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.severe("Error en captura continua de " + readerName + ": " + e.getMessage());
                }
            }
            logger.info("Tarea de captura finalizada para: " + readerName);
        };

        Future<?> future = captureExecutor.submit(captureTask);
        captureTasks.put(readerName, future);
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
            logger.info("No había tarea para: " + readerName);
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
     * Devuelve la lista de nombres de lectores válidos.
     */
    public synchronized Set<String> getValidReaderNames() {
        return validReaders.keySet();
    }

    /**
     * Publica la huella capturada vía WebSocket.
     */
    private void publishFingerprintEvent(String readerName, String base64Fingerprint) {
        var payload = new FingerprintEvent(readerName, base64Fingerprint);
        String destination = "/topic/fingerprints/" + readerName;
        messagingTemplate.convertAndSend(destination, payload);
    }

    /**
     * Realiza una captura única y convierte la imagen a Base64.
     */
    private String captureOnce(Reader reader) throws Exception {
        CaptureThread capture = new CaptureThread(
                reader,
                false,
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT,
                500,
                15000
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
     * Clase interna para encapsular el evento de huella.
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
}