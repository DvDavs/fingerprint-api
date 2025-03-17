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
            logger.info("Initializing readers.");
            readers = UareUGlobal.GetReaderCollection();
            readers.GetReaders();
            logger.info("Readers initialized successfully.");
        } catch (UareUException e) {
            logger.severe("Error initializing readers: code " + e.getCode() + " - " + e.toString());
            throw new RuntimeException("Failed to initialize readers", e);
        }
    }

    public List<String> getReaders() throws UareUException {
        logger.info("Attempting to retrieve readers list.");
        List<String> names = new ArrayList<>();
        readers.GetReaders();
        for (Reader r : readers) {
            names.add(r.GetDescription().name);
        }
        logger.info("Retrieved " + names.size() + " readers.");
        return names;
    }

    public String selectReader(String name) throws UareUException {
        logger.info("Attempting to select reader: " + name);
        for (Reader r : readers) {
            if (r.GetDescription().name.equals(name)) {
                if (selectedReader != null) {
                    logger.info("Closing previously selected reader.");
                    selectedReader.Close();
                }
                selectedReader = r;
                selectedReader.Open(Reader.Priority.EXCLUSIVE);
                logger.info("Reader selected: " + name);
                return "Reader selected: " + name;
            }
        }
        logger.severe("Reader with name " + name + " not found.");
        throw new UareUException(96075797);
    }

    public String captureFingerprint() throws UareUException, IOException, InterruptedException {
        logger.info("Starting fingerprint capture.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        Reader.Status status = selectedReader.GetStatus();
        if (status.status != Reader.ReaderStatus.READY && status.status != Reader.ReaderStatus.NEED_CALIBRATION) {
            logger.severe("Reader not ready. Status: " + status.status);
            throw new UareUException(96075807);
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(15000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null) {
            if (event.capture_result != null && event.capture_result.image != null) {
                BufferedImage img = convertFidToImage(event.capture_result.image);
                logger.info("Fingerprint captured successfully.");
                return convertImageToBase64(img);
            } else if (event.capture_result != null && event.capture_result.quality == Reader.CaptureQuality.TIMED_OUT) {
                logger.warning("Capture timed out: No finger detected.");
                return "{\"status\":\"timeout\",\"message\":\"No finger detected.\"}";
            } else if (event.reader_status != null) {
                logger.severe("Reader failure during capture.");
                throw new UareUException(96075807);
            }
            logger.severe("Capture failed: No image received.");
            throw new UareUException(96075787);
        }
        logger.severe("Capture failed: No event received.");
        throw new UareUException(96075787);
    }

    public Map<String, Object> getCapabilities() throws UareUException {
        logger.info("Retrieving reader capabilities.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
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
        logger.info("Reader capabilities retrieved.");
        return caps;
    }

    public String startEnrollment() throws UareUException {
        logger.info("Starting enrollment session.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        String sessionId = UUID.randomUUID().toString();
        enrollmentSessions.put(sessionId, new EnrollmentSession(sessionId));
        logger.info("Enrollment session started with session ID: " + sessionId);
        return sessionId;
    }

    public String captureForEnrollment(String sessionId) throws UareUException, InterruptedException {
        logger.info("Capturing fingerprint for enrollment, session ID: " + sessionId);
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            logger.severe("Invalid session: " + sessionId);
            throw new UareUException(96075796);
        }
        Reader.Status status = selectedReader.GetStatus();
        if (status.status != Reader.ReaderStatus.READY) {
            logger.severe("Reader not ready. Status: " + status.status);
            throw new UareUException(96075807);
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event == null || event.capture_result == null || event.capture_result.image == null) {
            logger.severe("No fingerprint data available for enrollment.");
            throw new UareUException(96075788);
        }
        if (event.capture_result.quality != Reader.CaptureQuality.GOOD) {
            logger.severe("Image quality issue during enrollment capture.");
            throw new UareUException(96076127);
        }
        Fid fid = event.capture_result.image;
        if (fid.getViews().length == 0 || fid.getViews()[0].getImageData().length == 0) {
            logger.severe("Invalid FID captured.");
            throw new UareUException(96075877);
        }
        Engine engine = UareUGlobal.GetEngine();
        Fmd fmd = engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
        if (fmd == null) {
            logger.severe("Failed to create FMD from capture.");
            throw new UareUException(96075977);
        }
        boolean complete = session.addCapture(fmd);
        if (complete) {
            Fmd enrollmentFmd = session.createEnrollmentFmd();
            enrollmentSessions.remove(sessionId);
            logger.info("Enrollment complete. Template size: " + enrollmentFmd.getData().length);
            return "Enrollment complete. Template size: " + enrollmentFmd.getData().length;
        }
        logger.info("Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining.");
        return "Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining";
    }

    public String startVerification() throws UareUException {
        logger.info("Starting verification process.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        firstVerificationFmd = null;
        return "Verification started";
    }

    public String captureForVerification() throws UareUException, InterruptedException {
        logger.info("Capturing fingerprint for verification.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
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
                logger.info("First fingerprint captured for verification.");
                return "First fingerprint captured. Please capture the second fingerprint.";
            } else {
                int score = engine.Compare(firstVerificationFmd, 0, fmd, 0);
                firstVerificationFmd = null;
                logger.info("Verification comparison completed. Score: " + score);
                return score < Engine.PROBABILITY_ONE / 100000
                        ? "Fingerprints match (score: " + score + ")"
                        : "Fingerprints do not match (score: " + score + ")";
            }
        }
        logger.severe("Verification capture failed: No data available.");
        throw new UareUException(96075788);
    }

    public String enrollForIdentification() throws UareUException, InterruptedException {
        logger.info("Enrolling fingerprint for identification.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
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
            logger.info("Fingerprint enrolled for identification. Total enrolled: " + identificationFmds.size());
            return "Fingerprint enrolled for identification (" + identificationFmds.size() + " total)";
        }
        logger.severe("Enrollment for identification failed: No data available.");
        throw new UareUException(96075788);
    }

    public String identifyFingerprint() throws UareUException, InterruptedException {
        logger.info("Starting fingerprint identification.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        if (identificationFmds.isEmpty()) {
            logger.severe("No fingerprints enrolled for identification.");
            throw new UareUException(96075788);
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
            String result = candidates.length > 0 ? "Identified as fingerprint " + candidates[0].fmd_index : "No match found";
            logger.info("Identification result: " + result);
            return result;
        }
        logger.severe("Identification failed: No data captured.");
        throw new UareUException(96075788);
    }

    public String getReaderStatus() throws UareUException {
        logger.info("Retrieving reader status.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        Reader.Status status = selectedReader.GetStatus();
        String result = "Reader status: " + status.status;
        logger.info(result);
        return result;
    }

    public Fmd captureFingerprintFmd() throws UareUException, InterruptedException {
        logger.info("Capturing fingerprint to create FMD.");
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(15000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
            logger.info("FMD captured successfully.");
            return fmd;
        }
        logger.severe("Failed to capture fingerprint FMD.");
        throw new UareUException(96075787);
    }

    public Fmd importFmdFromByteArray(byte[] data) throws UareUException {
        logger.info("Importing FMD from byte array.");
        Fmd fmd = UareUGlobal.GetImporter().ImportFmd(data, Fmd.Format.ANSI_378_2004, Fmd.Format.ANSI_378_2004);
        logger.info("FMD imported successfully.");
        return fmd;
    }

    public int compareFingerprints(Fmd fmd1, Fmd fmd2) throws UareUException {
        logger.info("Comparing two FMDs.");
        Engine engine = UareUGlobal.GetEngine();
        int score = engine.Compare(fmd1, 0, fmd2, 0);
        logger.info("Comparison score: " + score);
        return score;
    }

    public Map<String, Object> captureForEnrollmentResponse(String sessionId) throws UareUException, InterruptedException {
        logger.info("Capture for enrollment response, session ID: " + sessionId);
        Map<String, Object> response = new HashMap<>();
        if (selectedReader == null) {
            logger.severe("No reader selected.");
            throw new UareUException(96075797);
        }
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            logger.severe("Invalid enrollment session: " + sessionId);
            throw new UareUException(96075796);
        }
        Reader.Status status = selectedReader.GetStatus();
        if (status.status != Reader.ReaderStatus.READY) {
            logger.severe("Reader not ready. Status: " + status.status);
            throw new UareUException(96075807);
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event == null || event.capture_result == null || event.capture_result.image == null) {
            logger.severe("No image retrieved during enrollment capture.");
            throw new UareUException(96075788);
        }
        if (event.capture_result.quality != Reader.CaptureQuality.GOOD) {
            logger.severe("Image quality insufficient during enrollment capture.");
            throw new UareUException(96076127);
        }
        Fid fid = event.capture_result.image;
        if (fid.getViews().length == 0 || fid.getViews()[0].getImageData().length == 0) {
            logger.severe("Invalid FID detected during enrollment capture.");
            throw new UareUException(96075877);
        }
        Engine engine = UareUGlobal.GetEngine();
        Fmd fmd = engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
        boolean complete = session.addCapture(fmd);
        if (complete) {
            Fmd enrollmentFmd = session.createEnrollmentFmd();
            enrollmentSessions.remove(sessionId);
            response.put("complete", true);
            response.put("template", Base64.getEncoder().encodeToString(enrollmentFmd.getData()));
            logger.info("Enrollment session complete. Sending response.");
        } else {
            response.put("complete", false);
            response.put("message", "Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining");
            logger.info("Enrollment capture successful: " + (4 - session.getCaptureCount()) + " captures remaining.");
        }
        return response;
    }

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

    private class EnrollmentSession {
        private final List<Fmd> fmds = new ArrayList<>();
        private final int required = 4;
        private final String id;

        EnrollmentSession(String id) {
            this.id = id;
        }

        boolean addCapture(Fmd fmd) {
            fmds.add(fmd);
            logger.info("Capture added to enrollment session " + id + ". Count: " + fmds.size());
            return fmds.size() >= required;
        }

        int getCaptureCount() {
            return fmds.size();
        }

        Fmd createEnrollmentFmd() throws UareUException {
            logger.info("Creating enrollment FMD from session " + id);
            Engine engine = UareUGlobal.GetEngine();
            Fmd enrollmentFmd = engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, new EnrollmentCallbackImpl(fmds));
            logger.info("Enrollment FMD created successfully.");
            return enrollmentFmd;
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