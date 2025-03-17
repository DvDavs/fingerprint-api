package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.*;
import com.example.fingerprint_api.util.CaptureThread;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Base64;
import java.util.logging.Logger;

@Service
public class FingerprintService {

    private static final Logger logger = Logger.getLogger(FingerprintService.class.getName());
    private ReaderCollection readers;
    private Reader selectedReader;
    private Map<String, EnrollmentSession> enrollmentSessions = new HashMap<>();
    private List<Fmd> identificationFmds = new ArrayList<>();
    private Fmd firstVerificationFmd = null;

    public FingerprintService() {
        try {
            readers = UareUGlobal.GetReaderCollection();
            readers.GetReaders();
        } catch (UareUException e) {
            logger.severe("Error initializing readers: code " + e.getCode() + " - " + e.toString());
            // Si ocurre un error crítico en la inicialización, lanzamos una excepción en tiempo de ejecución.
            throw new RuntimeException("Failed to initialize readers", e);
        }
    }

    public List<String> getReaders() throws UareUException {
        List<String> names = new ArrayList<>();
        readers.GetReaders();
        for (Reader r : readers) {
            names.add(r.GetDescription().name);
        }
        return names;
    }

    public String selectReader(String name) throws UareUException {
        for (Reader r : readers) {
            if (r.GetDescription().name.equals(name)) {
                if (selectedReader != null) {
                    selectedReader.Close();
                }
                selectedReader = r;
                selectedReader.Open(Reader.Priority.EXCLUSIVE);
                return "Reader selected: " + name;
            }
        }
        // Código 96075797: "Reader handle is not valid" (se utiliza para indicar que no se encontró el lector)
        throw new UareUException(96075797);
    }

    public String captureFingerprint() throws UareUException, IOException, InterruptedException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        Reader.Status status = selectedReader.GetStatus();
        if (status.status != Reader.ReaderStatus.READY && status.status != Reader.ReaderStatus.NEED_CALIBRATION) {
            throw new UareUException(96075807); // Reader failure / not ready
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(15000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null) {
            if (event.capture_result != null && event.capture_result.image != null) {
                BufferedImage img = convertFidToImage(event.capture_result.image);
                return convertImageToBase64(img);
            } else if (event.capture_result != null && event.capture_result.quality == Reader.CaptureQuality.TIMED_OUT) {
                return "{\"status\":\"timeout\",\"message\":\"No finger detected.\"}";
            } else if (event.reader_status != null) {
                throw new UareUException(96075807); // Reader failure
            }
            throw new UareUException(96075787); // Generic failure: no image received
        }
        throw new UareUException(96075787); // Generic failure: no event received
    }

    public Map<String, Object> getCapabilities() throws UareUException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        Map<String, Object> caps = new HashMap<>();
        Reader.Description desc = selectedReader.GetDescription();
        Reader.Capabilities cap = selectedReader.GetCapabilities();
        caps.put("vendor_name", desc.id.vendor_name);
        caps.put("product_name", desc.id.product_name);
        caps.put("serial_number", desc.serial_number);
        caps.put("can_capture", cap.can_capture);
        caps.put("can_stream", cap.can_stream);
        caps.put("can_extract_features", cap.can_extract_features);
        caps.put("can_match", cap.can_match);
        caps.put("can_identify", cap.can_identify);
        caps.put("has_fingerprint_storage", cap.has_fingerprint_storage);
        caps.put("indicator_type", cap.indicator_type);
        caps.put("has_power_management", cap.has_power_management);
        caps.put("has_calibration", cap.has_calibration);
        caps.put("piv_compliant", cap.piv_compliant);
        caps.put("resolutions", cap.resolutions);
        return caps;
    }

    public String startEnrollment() throws UareUException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        String sessionId = UUID.randomUUID().toString();
        enrollmentSessions.put(sessionId, new EnrollmentSession(sessionId));
        return sessionId;
    }

    public String captureForEnrollment(String sessionId) throws UareUException, InterruptedException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            throw new UareUException(96075796); // Invalid parameter (session)
        }
        Reader.Status status = selectedReader.GetStatus();
        if (status.status != Reader.ReaderStatus.READY) {
            throw new UareUException(96075807); // Reader not ready
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event == null || event.capture_result == null || event.capture_result.image == null) {
            throw new UareUException(96075788); // No data available
        }
        if (event.capture_result.quality != Reader.CaptureQuality.GOOD) {
            throw new UareUException(96076127); // Image quality issue: too few minutia detected
        }
        Fid fid = event.capture_result.image;
        if (fid.getViews().length == 0 || fid.getViews()[0].getImageData().length == 0) {
            throw new UareUException(96075877); // Invalid FID
        }
        Engine engine = UareUGlobal.GetEngine();
        Fmd fmd = engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
        if (fmd == null) {
            throw new UareUException(96075977); // Invalid FMD
        }
        boolean complete = session.addCapture(fmd);
        if (complete) {
            Fmd enrollmentFmd = session.createEnrollmentFmd();
            enrollmentSessions.remove(sessionId);
            return "Enrollment complete. Template size: " + enrollmentFmd.getData().length;
        }
        return "Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining";
    }

    public String startVerification() throws UareUException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        firstVerificationFmd = null;
        return "Verification started";
    }

    public String captureForVerification() throws UareUException, InterruptedException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
            if (firstVerificationFmd == null) {
                firstVerificationFmd = fmd;
                return "First fingerprint captured. Please capture the second fingerprint.";
            } else {
                int score = engine.Compare(firstVerificationFmd, 0, fmd, 0);
                firstVerificationFmd = null;
                return score < Engine.PROBABILITY_ONE / 100000
                        ? "Fingerprints match (score: " + score + ")"
                        : "Fingerprints do not match (score: " + score + ")";
            }
        }
        throw new UareUException(96075788); // No data / capture failed
    }

    public String enrollForIdentification() throws UareUException, InterruptedException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
            identificationFmds.add(fmd);
            return "Fingerprint enrolled for identification (" + identificationFmds.size() + " total)";
        }
        throw new UareUException(96075788); // No data available
    }

    public String identifyFingerprint() throws UareUException, InterruptedException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        if (identificationFmds.isEmpty()) {
            throw new UareUException(96075788); // No fingerprints enrolled
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
            int rate = Engine.PROBABILITY_ONE / 100000;
            Engine.Candidate[] candidates = engine.Identify(fmd, 0, identificationFmds.toArray(new Fmd[0]), rate, 1);
            return candidates.length > 0 ? "Identified as fingerprint " + candidates[0].fmd_index : "No match found";
        }
        throw new UareUException(96075788); // Capture failed
    }

    public String getReaderStatus() throws UareUException {
        if (selectedReader == null) {
            throw new UareUException(96075797); // No reader selected
        }
        Reader.Status status = selectedReader.GetStatus();
        return "Reader status: " + status.status;
    }

    // Métodos de ayuda

    private BufferedImage convertFidToImage(Fid fid) {
        Fid.Fiv view = fid.getViews()[0];
        BufferedImage img = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setDataElements(0, 0, view.getWidth(), view.getHeight(), view.getImageData());
        return img;
    }

    private String convertImageToBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // Clases internas para el proceso de enrolamiento

    private class EnrollmentSession {
        private final List<Fmd> fmds = new ArrayList<>();
        private final int required = 4;
        private final String id;

        EnrollmentSession(String id) {
            this.id = id;
        }

        boolean addCapture(Fmd fmd) {
            fmds.add(fmd);
            return fmds.size() >= required;
        }

        int getCaptureCount() {
            return fmds.size();
        }

        Fmd createEnrollmentFmd() throws UareUException {
            Engine engine = UareUGlobal.GetEngine();
            return engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, new EnrollmentCallbackImpl(fmds));
        }
    }

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
}
