package com.example.fingerprint_api.util;

import com.digitalpersona.uareu.Fid;
import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.UareUException;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

public class CaptureThread extends Thread {
    private static final Logger logger = Logger.getLogger(CaptureThread.class.getName());
    public static final String ACT_CAPTURE = "capture_thread_captured";

    // Se cambia el tipo de excepción a Exception para poder almacenar cualquier tipo (UareUException o InterruptedException)
    public class CaptureEvent extends ActionEvent {
        private static final long serialVersionUID = 101;
        public Reader.CaptureResult capture_result;
        public Reader.Status reader_status;
        public Exception exception;

        public CaptureEvent(Object source, String action, Reader.CaptureResult cr, Reader.Status st, Exception ex) {
            super(source, ActionEvent.ACTION_PERFORMED, action);
            this.capture_result = cr;
            this.reader_status = st;
            this.exception = ex;
        }
    }

    private ActionListener m_listener;
    private boolean m_bCancel;
    private Reader m_reader;
    private boolean m_bStream;
    private Fid.Format m_format;
    private Reader.ImageProcessing m_proc;
    private CaptureEvent m_last_capture;
    private int resolution;
    private int timeout;

    // Constructor que ahora recibe resolución y timeout
    public CaptureThread(Reader reader, boolean bStream, Fid.Format img_format, Reader.ImageProcessing img_proc, int resolution, int timeout) {
        this.m_bCancel = false;
        this.m_reader = reader;
        this.m_bStream = bStream;
        this.m_format = img_format;
        this.m_proc = img_proc;
        this.resolution = resolution;
        this.timeout = timeout;
        logger.info("CaptureThread initialized - Stream mode: " + bStream);
    }

    public void start(ActionListener listener) {
        this.m_listener = listener;
        logger.info("Starting capture thread with listener");
        super.start();
    }



    /**
     * Devuelve el último evento de captura. Si en dicho evento se produjo una excepción,
     * ésta se lanza para que pueda ser capturada por el GlobalExceptionHandler.
     */
    public CaptureEvent getLastCaptureEvent() throws UareUException {
        if (m_last_capture != null && m_last_capture.exception != null) {
            if (m_last_capture.exception instanceof UareUException) {
                throw (UareUException) m_last_capture.exception;
            } else {
                // Si no es UareUException, lo envolvemos en una UareUException con código genérico.
                throw new UareUException(96075787);
            }
        }
        return m_last_capture;
    }

    private void Capture() {
        logger.info("=========== STARTING FINGERPRINT CAPTURE OPERATION ===========");
        try {
            logger.info("Esperando que el lector esté listo...");
            boolean bReady = false;
            int attempts = 0;
            int backoffTime = 100; // tiempo de espera inicial en ms
            while (!bReady && !m_bCancel) {
                Reader.Status rs = m_reader.GetStatus();
                logger.info("➡️ Estado del lector: " + rs.status);
                if (rs.status == Reader.ReaderStatus.BUSY) {
                    attempts++;
                    // Si se han realizado 10 o más intentos consecutivos, se aumenta el tiempo de espera
                    if (attempts % 10 == 0) {
                        backoffTime = 500; // aumentar a 500 ms cada 10 intentos
                    }
                    logger.info("⏳ Lector ocupado, esperando " + backoffTime + "ms (intento #" + attempts + ")");
                    Thread.sleep(backoffTime);
                } else if (rs.status == Reader.ReaderStatus.READY || rs.status == Reader.ReaderStatus.NEED_CALIBRATION) {
                    logger.info("✅ Lector listo para captura.");
                    bReady = true;
                    break;
                } else {
                    logger.warning("⚠️ ERROR: Estado inesperado del lector: " + rs.status);
                    m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, new UareUException(96075787));
                    return;
                }
            }
            if (m_bCancel) {
                logger.info("❌ Captura cancelada por el usuario");
                return;
            }
            if (bReady) {
                logger.info("📸 Capturando huella... (resolution: " + resolution + " DPI, timeout: " + timeout + "ms)");
                // Se usa el timeout finito que se ha configurado
                Reader.CaptureResult cr = m_reader.Capture(m_format, m_proc, resolution, timeout);
                if (cr == null) {
                    logger.severe("⚠️ ERROR: CaptureResult es null. No se obtuvo respuesta del SDK.");
                    m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, new UareUException(96075787));
                } else {
                    logger.info("✅ CaptureResult recibido.");
                    logger.info("➡️ Calidad de la captura: " + cr.quality);
                    if (cr.quality == Reader.CaptureQuality.GOOD) {
                        logger.info("📸 Calidad de la imagen es BUENA.");
                    } else {
                        logger.warning("⚠️ Calidad de la imagen NO es buena: " + cr.quality);
                    }
                    if (cr.image != null) {
                        m_last_capture = new CaptureEvent(this, ACT_CAPTURE, cr, null, null);
                        logger.info("📸 Imagen de la huella capturada correctamente.");
                    } else {
                        logger.warning("⚠️ ERROR: No se recibió imagen de la huella.");
                        m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, new UareUException(96075788));
                    }
                }
            }
        } catch (UareUException e) {
            logger.severe("❌ Excepción en la captura: " + e.getMessage());
            m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, e);
        } catch (InterruptedException e) {
            logger.severe("❌ Excepción de interrupción: " + e.getMessage());
            m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, new UareUException(96075787));
            Thread.currentThread().interrupt();
        }
        logger.info("=========== FINGERPRINT CAPTURE OPERATION COMPLETED ===========");
    }

    private void Stream() {
        logger.info("Starting streaming operation");
        try {
            boolean bReady = false;
            while (!bReady && !m_bCancel) {
                Reader.Status rs = m_reader.GetStatus();
                logger.info("Reader status: " + rs.status);
                if (Reader.ReaderStatus.BUSY == rs.status) {
                    logger.info("Reader is busy, waiting 100ms");
                    Thread.sleep(100);
                } else if (Reader.ReaderStatus.READY == rs.status || Reader.ReaderStatus.NEED_CALIBRATION == rs.status) {
                    logger.info("Reader is ready for streaming");
                    bReady = true;
                    break;
                } else {
                    logger.warning("Reader failure detected: " + rs.status);
                    NotifyListener(ACT_CAPTURE, null, rs, new UareUException(96075787));
                    return;
                }
            }
            if (bReady) {
                logger.info("Starting streaming mode");
                m_reader.StartStreaming();
                int frameCount = 0;
                while (!m_bCancel) {
                    Reader.CaptureResult cr = m_reader.GetStreamImage(m_format, m_proc, 500);
                    frameCount++;
                    logger.info("Stream frame #" + frameCount + " - Quality: " + cr.quality);
                    NotifyListener(ACT_CAPTURE, cr, null, null);
                }
                logger.info("Stopping streaming mode");
                m_reader.StopStreaming();
            }
        } catch (UareUException e) {
            logger.severe("Streaming error: " + e.getMessage());
            NotifyListener(ACT_CAPTURE, null, null, e);
        } catch (InterruptedException e) {
            logger.severe("Streaming interrupted: " + e.getMessage());
            NotifyListener(ACT_CAPTURE, null, null, new UareUException(96075787));
            Thread.currentThread().interrupt();
        }
        if (m_bCancel) {
            logger.info("Streaming canceled by user");
            Reader.CaptureResult cr = new Reader.CaptureResult();
            cr.quality = Reader.CaptureQuality.CANCELED;
            NotifyListener(ACT_CAPTURE, cr, null, null);
        }
    }

    private void NotifyListener(String action, Reader.CaptureResult cr, Reader.Status st, UareUException ex) {
        final CaptureEvent evt = new CaptureEvent(this, action, cr, st, ex);
        StringBuilder sb = new StringBuilder("Capture event: " + action);
        if (cr != null) sb.append(" - Quality: " + cr.quality);
        if (st != null) sb.append(" - Status: " + st.status);
        if (ex != null) sb.append(" - Exception: " + ex.getMessage());
        logger.info(sb.toString());
        m_last_capture = evt;
        if (m_listener == null || action == null || action.isEmpty()) return;
        logger.info("Notifying listener on EDT thread");
        javax.swing.SwingUtilities.invokeLater(() -> m_listener.actionPerformed(evt));
    }

    public void cancel() {
        logger.info("Canceling capture operation");
        m_bCancel = true;
        try {
            if (!m_bStream) {
                logger.info("Calling CancelCapture on reader");
                m_reader.CancelCapture();
            }
        } catch (UareUException e) {
            logger.severe("Error canceling capture: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        if (m_bStream) {
            logger.info("Running in stream mode");
            Stream();
        } else {
            logger.info("Running in single capture mode");
            Capture();
        }
    }
}
