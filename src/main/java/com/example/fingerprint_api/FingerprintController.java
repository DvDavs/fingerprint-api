package com.example.fingerprint_api;

import com.digitalpersona.uareu.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/fingerprint")
public class FingerprintController {

    private static final Logger logger = Logger.getLogger(FingerprintController.class.getName());
    private ReaderCollection readers;
    private Reader selectedReader;
    private Map<String, EnrollmentSession> enrollmentSessions = new HashMap<>();
    private List<Fmd> identificationFmds = new ArrayList<>();
    private Fmd firstVerificationFmd = null;

    public FingerprintController() {
        try {
            readers = UareUGlobal.GetReaderCollection();
            readers.GetReaders();
        } catch (UareUException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/readers")
    public ResponseEntity<List<String>> getReaders() {
        List<String> names = new ArrayList<>();
        try {
            readers.GetReaders();
            for (Reader r : readers) {
                names.add(r.GetDescription().name);
            }
            return ResponseEntity.ok(names);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/select")
    public ResponseEntity<String> selectReader(@RequestParam("readerName") String name) {
        try {
            for (Reader r : readers) {
                if (r.GetDescription().name.equals(name)) {
                    if (selectedReader != null) {
                        selectedReader.Close();
                    }
                    selectedReader = r;
                    selectedReader.Open(Reader.Priority.EXCLUSIVE);
                    return ResponseEntity.ok("Reader selected: " + name);
                }
            }
            return ResponseEntity.badRequest().body("Reader not found");
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error selecting reader: " + e.getMessage());
        }
    }

    @GetMapping("/capture")
    public ResponseEntity<String> captureFingerprint() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        try {
            Reader.Status status = selectedReader.GetStatus();
            if (status.status != Reader.ReaderStatus.READY && status.status != Reader.ReaderStatus.NEED_CALIBRATION) {
                return ResponseEntity.status(500).body("Capture failed: Reader not ready. Status: " + status.status);
            }
            CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                    Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
            capture.start(null);
            capture.join(15000);
            CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
            if (event != null) {
                if (event.capture_result != null && event.capture_result.image != null) {
                    BufferedImage img = convertFidToImage(event.capture_result.image);
                    return ResponseEntity.ok(convertImageToBase64(img));
                } else if (event.capture_result != null && event.capture_result.quality == Reader.CaptureQuality.TIMED_OUT) {
                    return ResponseEntity.ok("{\"status\":\"timeout\",\"message\":\"No finger detected.\"}");
                } else if (event.reader_status != null) {
                    return ResponseEntity.status(500).body("Capture failed: Reader status " + event.reader_status.status);
                }
                return ResponseEntity.status(500).body("Capture failed: No image received");
            }
            return ResponseEntity.status(500).body("Capture failed: No event received");
        } catch (UareUException | IOException e) {
            return ResponseEntity.status(500).body("Error during capture: " + e.getMessage());
        }
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getCapabilities() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body(null);
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
        return ResponseEntity.ok(caps);
    }

    @PostMapping("/enroll/start")
    public ResponseEntity<String> startEnrollment() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        String sessionId = UUID.randomUUID().toString();
        enrollmentSessions.put(sessionId, new EnrollmentSession(sessionId));
        return ResponseEntity.ok(sessionId);
    }

    @PostMapping("/enroll/capture/{sessionId}")
    public ResponseEntity<String> captureForEnrollment(@PathVariable String sessionId) {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.badRequest().body("Invalid session");
        }
        try {
            Reader.Status status = selectedReader.GetStatus();
            if (status.status != Reader.ReaderStatus.READY) {
                return ResponseEntity.status(500).body("Reader not ready. Status: " + status.status);
            }
            CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                    Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
            capture.start(null);
            capture.join(5000);
            CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
            if (event == null || event.capture_result == null || event.capture_result.image == null) {
                return ResponseEntity.status(500).body("Capture failed");
            }
            if (event.capture_result.quality != Reader.CaptureQuality.GOOD) {
                return ResponseEntity.status(500).body("Image quality not good: " + event.capture_result.quality);
            }
            Fid fid = event.capture_result.image;
            if (fid.getViews().length == 0 || fid.getViews()[0].getImageData().length == 0) {
                return ResponseEntity.status(500).body("Invalid or empty FID");
            }
            Engine engine = UareUGlobal.GetEngine();
            Fmd fmd = engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
            if (fmd == null) {
                return ResponseEntity.status(500).body("FMD creation returned null");
            }
            boolean complete = session.addCapture(fmd);
            if (complete) {
                Fmd enrollmentFmd = session.createEnrollmentFmd();
                enrollmentSessions.remove(sessionId);
                return ResponseEntity.ok("Enrollment complete. Template size: " + enrollmentFmd.getData().length);
            }
            return ResponseEntity.ok("Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/verify/start")
    public ResponseEntity<String> startVerification() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        firstVerificationFmd = null;
        return ResponseEntity.ok("Verification started");
    }

    @PostMapping("/verify/capture")
    public ResponseEntity<String> captureForVerification() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        try {
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
                    return ResponseEntity.ok("First fingerprint captured. Please capture the second fingerprint.");
                } else {
                    int score = engine.Compare(firstVerificationFmd, 0, fmd, 0);
                    firstVerificationFmd = null;
                    return ResponseEntity.ok(score < Engine.PROBABILITY_ONE / 100000
                            ? "Fingerprints match (score: " + score + ")"
                            : "Fingerprints do not match (score: " + score + ")");
                }
            }
            return ResponseEntity.status(500).body("Verification capture failed");
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error during verification capture: " + e.getMessage());
        }
    }

    @PostMapping("/identify/enroll")
    public ResponseEntity<String> enrollForIdentification() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        try {
            CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004,
                    Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
            capture.start(null);
            capture.join(5000);
            CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
            if (event != null && event.capture_result != null && event.capture_result.image != null) {
                Engine engine = UareUGlobal.GetEngine();
                Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
                identificationFmds.add(fmd);
                return ResponseEntity.ok("Fingerprint enrolled for identification (" + identificationFmds.size() + " total)");
            }
            return ResponseEntity.status(500).body("Identification enrollment capture failed");
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error during identification enrollment: " + e.getMessage());
        }
    }

    @PostMapping("/identify")
    public ResponseEntity<String> identifyFingerprint() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        if (identificationFmds.isEmpty()) {
            return ResponseEntity.badRequest().body("No fingerprints enrolled for identification");
        }
        try {
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
                return ResponseEntity.ok(candidates.length > 0 ? "Identified as fingerprint " + candidates[0].fmd_index : "No match found");
            }
            return ResponseEntity.status(500).body("Identification capture failed");
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error during identification: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> getReaderStatus() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        try {
            Reader.Status status = selectedReader.GetStatus();
            return ResponseEntity.ok("Reader status: " + status.status);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error getting status: " + e.getMessage());
        }
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
        EnrollmentSession(String id) { this.id = id; }
        boolean addCapture(Fmd fmd) { fmds.add(fmd); return fmds.size() >= required; }
        int getCaptureCount() { return fmds.size(); }
        Fmd createEnrollmentFmd() throws UareUException {
            Engine engine = UareUGlobal.GetEngine();
            return engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, new EnrollmentCallbackImpl(fmds));
        }
    }

    private static class EnrollmentCallbackImpl implements Engine.EnrollmentCallback {
        private final Iterator<Fmd> iterator;
        EnrollmentCallbackImpl(List<Fmd> fmds) { iterator = fmds.iterator(); }
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