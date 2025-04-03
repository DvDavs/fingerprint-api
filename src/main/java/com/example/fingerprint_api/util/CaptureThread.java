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

    public CaptureThread(Reader reader, boolean bStream, Fid.Format img_format,
                         Reader.ImageProcessing img_proc, int resolution, int timeout) {
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

    public CaptureEvent getLastCaptureEvent() throws UareUException {
        if (m_last_capture != null && m_last_capture.exception != null) {
            if (m_last_capture.exception instanceof UareUException) {
                throw (UareUException) m_last_capture.exception;
            } else {
                UareUException ex = new UareUException(96075787);
                ex.initCause(m_last_capture.exception);
                throw ex;
            }
        }
        return m_last_capture;
    }

    private void Capture() {
        logger.info("=========== STARTING FINGERPRINT CAPTURE OPERATION ===========");
        try {
            // Verificar el estado del lector
            Reader.Status rs = m_reader.GetStatus();
            logger.info("➡️ Estado del lector: " + rs.status);

            // Si el lector no está listo o ha fallado, detener la captura
            if (rs.status == Reader.ReaderStatus.FAILURE || rs.status == null) {
                logger.severe("❌ Lector no disponible o en estado de fallo.");
                m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, rs, new UareUException(96075787));
                return;
            }

            if (rs.status != Reader.ReaderStatus.READY && rs.status != Reader.ReaderStatus.NEED_CALIBRATION) {
                logger.warning("⚠️ Lector no está listo: " + rs.status);
                m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, rs, null);
                return;
            }

            // Proceder con la captura si el lector está listo
            logger.info("✅ Lector listo para captura.");
            logger.info("📸 Capturando huella... (resolution: " + resolution + " DPI, timeout: " + timeout + "ms)");
            Reader.CaptureResult cr = m_reader.Capture(m_format, m_proc, resolution, timeout);

            if (cr == null) {
                logger.severe("⚠️ ERROR: CaptureResult es null.");
                m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, new UareUException(96075787));
            } else {
                logger.info("✅ CaptureResult recibido. Calidad: " + cr.quality);
                m_last_capture = new CaptureEvent(this, ACT_CAPTURE, cr, null, null);
            }
        } catch (UareUException e) {
            logger.severe("❌ Excepción en la captura: " + e.getMessage());
            m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, e);
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "❌ Excepción inesperada en Capture", e);
            UareUException ex = new UareUException(96075787);
            ex.initCause(e);
            m_last_capture = new CaptureEvent(this, ACT_CAPTURE, null, null, ex);
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
                    Thread.sleep(100);
                } else if (Reader.ReaderStatus.READY == rs.status || Reader.ReaderStatus.NEED_CALIBRATION == rs.status) {
                    bReady = true;
                    break;
                } else {
                    logger.warning("Reader failure: " + rs.status);
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
            logger.log(java.util.logging.Level.SEVERE, "Streaming interrupted", e);
            UareUException ex = new UareUException(96075787);
            ex.initCause(e);
            NotifyListener(ACT_CAPTURE, null, null, ex);
            Thread.currentThread().interrupt();
        }
        if (m_bCancel) {
            logger.info("Streaming canceled");
            Reader.CaptureResult cr = new Reader.CaptureResult();
            cr.quality = Reader.CaptureQuality.CANCELED;
            NotifyListener(ACT_CAPTURE, cr, null, null);
        }
    }

    private void NotifyListener(String action, Reader.CaptureResult cr, Reader.Status st, UareUException ex) {
        final CaptureEvent evt = new CaptureEvent(this, action, cr, st, ex);
        logger.info("Capture event: " + action +
                (cr != null ? " - Quality: " + cr.quality : "") +
                (st != null ? " - Status: " + st.status : "") +
                (ex != null ? " - Exception: " + ex.getMessage() : ""));
        m_last_capture = evt;
        if (m_listener == null || action == null || action.isEmpty()) return;
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
        try {
            if (m_reader != null && m_reader.GetStatus().status != Reader.ReaderStatus.FAILURE && m_reader.GetStatus().status != null) {
                Capture();
            } else {
                logger.severe("Reader not ready for capture");
            }
        } catch (UareUException e) {
            logger.severe("Failed to get reader status: " + e.getMessage());
        }
    }
}
