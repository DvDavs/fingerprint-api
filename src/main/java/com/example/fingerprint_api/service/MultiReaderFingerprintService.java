package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.ReaderCollection;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.util.ContinuousCaptureThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Servicio para manejar múltiples lectores simultáneamente,
 * con lectura continua y notificaciones vía WebSocket.
 */
@Service
public class MultiReaderFingerprintService {

    private static final Logger logger = Logger.getLogger(MultiReaderFingerprintService.class.getName());

    /**
     * Mapa: "readerName" -> "Reader"
     * Se llena al llamar autoSelectReaders()
     */
    private final Map<String, Reader> validReaders = new ConcurrentHashMap<>();

    /**
     * Mapa: "readerName" -> hilo de captura continua
     */
    private final Map<String, ContinuousCaptureThread> captureThreads = new ConcurrentHashMap<>();

    /**
     * Mapa: "readerName" -> última huella en base64
     */
    private final Map<String, String> lastFingerprintData = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public MultiReaderFingerprintService() {
        logger.info("Inicializando MultiReaderFingerprintService.");
    }

    /**
     * Revisa todos los lectores del sistema, intenta abrirlos,
     * y los agrega a validReaders si no hay error.
     */
    public synchronized List<String> autoSelectReaders() throws UareUException {
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders();
        List<String> resultNames = new ArrayList<>();

        for (Reader r : readers) {
            String name = r.GetDescription().name;
            if (validReaders.containsKey(name) && captureThreads.containsKey(name)) {
                resultNames.add(name); // Mantener lector activo
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
     * Inicia la captura continua en TODOS los lectores válidos
     * que todavía no tengan un hilo de captura corriendo.
     */
    public synchronized void startContinuousCaptureForAll() {
        logger.info("[startContinuousCaptureForAll] Iniciando hilos de captura para TODOS los lectores...");
        for (Map.Entry<String, Reader> entry : validReaders.entrySet()) {
            String readerName = entry.getKey();
            if (!captureThreads.containsKey(readerName)) {
                startThreadForReader(readerName, entry.getValue());
            }
        }
    }

    /**
     * Inicia la captura continua para UN lector específico.
     * @return true si se pudo iniciar, false si no se encontró
     */
    public synchronized boolean startContinuousCaptureForReader(String readerName) {
        logger.info("[startContinuousCaptureForReader] solicitando lector: " + readerName);

        // Verificamos si ya existe
        Reader existing = validReaders.get(readerName);
        if (existing == null) {
            // => reintentar autoSelect (por si no estaba en la lista)
            logger.info("No se encontró lector " + readerName + " en el mapa. Reintentando autoSelect...");
            try {
                autoSelectReaders(); // refresca validReaders
            } catch (UareUException e) {
                logger.severe("Error re-intentando autoSelect: " + e.getMessage());
                return false;
            }
            existing = validReaders.get(readerName);
            if (existing == null) {
                logger.warning("[startContinuousCaptureForReader] Aún no se encuentra lector: " + readerName);
                return false;
            }
        }

        // Si ya existe hilo, no lo duplicamos:
        if (captureThreads.containsKey(readerName)) {
            logger.info("Ya existe hilo de captura para el lector: " + readerName);
            return true;
        }

        // Iniciamos el hilo
        startThreadForReader(readerName, existing);
        return true;
    }

    /**
     * Lógica interna para crear el hilo de captura continua
     * y almacenarlo en el mapa "captureThreads".
     */
    private void startThreadForReader(String readerName, Reader r) {
        logger.info("[startThreadForReader] Iniciando hilo para: " + readerName);
        ContinuousCaptureThread thread = new ContinuousCaptureThread(
                r,
                (base64Fingerprint) -> {
                    lastFingerprintData.put(readerName, base64Fingerprint);
                    publishFingerprintEvent(readerName, base64Fingerprint);
                }
        );
        captureThreads.put(readerName, thread);
        thread.start();
    }

    /**
     * Detiene la captura de TODOS los lectores.
     */
    public synchronized void stopContinuousCaptureForAll() {
        logger.info("[stopContinuousCaptureForAll] Deteniendo TODOS los hilos de captura...");
        for (ContinuousCaptureThread t : captureThreads.values()) {
            t.cancelCapture();
        }
        captureThreads.clear();
    }

    /**
     * Detiene la captura de un lector en particular.
     */
    public synchronized void stopCaptureForReader(String readerName) {
        ContinuousCaptureThread thread = captureThreads.remove(readerName);
        if (thread != null) {
            logger.info("[stopCaptureForReader] Cancelando hilo de: " + readerName);
            thread.cancelCapture();
        } else {
            logger.info("[stopCaptureForReader] No había hilo para: " + readerName);
        }
    }

    /**
     * Indica si un lector tiene hilo de captura corriendo.
     */
    public synchronized boolean isCaptureActive(String readerName) {
        return captureThreads.containsKey(readerName);
    }

    /**
     * Devuelve la última huella capturada (Base64) para un lector
     */
    public synchronized String getLastCapturedFingerprint(String readerName) {
        return lastFingerprintData.get(readerName);
    }

    /**
     * Devuelve la lista de lectores “válidos” (los que se abrieron bien)
     */
    public synchronized Set<String> getValidReaderNames() {
        return validReaders.keySet();
    }

    /**
     * Publicamos la huella capturada en /topic/fingerprints/{readerName},
     * para que SOLO clientes suscritos a ese lector lo reciban.
     */
    private void publishFingerprintEvent(String readerName, String base64Fingerprint) {
        var payload = new FingerprintEvent(readerName, base64Fingerprint);
        String destination = "/topic/fingerprints/" + readerName;
        messagingTemplate.convertAndSend(destination, payload);
    }

    /**
     * Clase interna para enviar el evento de huella
     */
    public static class FingerprintEvent {
        public String readerName;
        public String base64;

        public FingerprintEvent(String readerName, String base64) {
            this.readerName = readerName;
            this.base64 = base64;
        }
    }

}
