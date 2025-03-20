package com.example.fingerprint_api.util;

import com.digitalpersona.uareu.Fid;
import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.UareUException;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hilo que hace capturas consecutivas en bucle.
 */
public class ContinuousCaptureThread extends Thread {

    private static final Logger logger = Logger.getLogger(ContinuousCaptureThread.class.getName());

    private final Reader reader;
    private final Consumer<String> onFingerprintCaptured;
    private volatile boolean running = true;

    public ContinuousCaptureThread(Reader reader, Consumer<String> onFingerprintCaptured) {
        this.reader = reader;
        this.onFingerprintCaptured = onFingerprintCaptured;
        setName("ContinuousCaptureThread-" + reader.GetDescription().name);
    }

    @Override
    public void run() {
        logger.info("Iniciando captura continua en: " + reader.GetDescription().name);
        while (running) {
            try {
                String base64Image = captureOnce(reader);
                if (base64Image != null && !base64Image.isEmpty()) {
                    // Notificamos a la lambda
                    onFingerprintCaptured.accept(base64Image);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error en captura continua: " + e.getMessage(), e);
            }

            try {
                Thread.sleep(500); // Ajusta delay a tus necesidades
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Hilo de captura continua finalizado para: " + reader.GetDescription().name);
    }

    public void cancelCapture() {
        logger.info("Cancelando captura continua en: " + reader.GetDescription().name);
        running = false;
        try {
            reader.CancelCapture();
        } catch (UareUException e) {
            logger.warning("Error al cancelar captura: " + e.getMessage());
        }
    }

    private String captureOnce(Reader rd) throws Exception {
        CaptureThread capture = new CaptureThread(
                rd,
                false,
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT,
                500,
                5000
        );
        capture.start(null);
        capture.join(8000); // Esperamofeature/multi-reader-fingerprint-broadcasts 8 segs

        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            // Convertimos a base64
            return FingerprintImageUtils.convertFidToBase64(event.capture_result.image);
        }
        return null;
    }
}
