package com.example.fingerprint_api;
import com.digitalpersona.uareu.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import java.util.function.Supplier;
import java.util.logging.Logger;
@RestController
@RequestMapping("/api/fingerprint")
public class FingerprintController {

    private static final Logger logger = Logger.getLogger(CaptureThread.class.getName());
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
        List<String> readerNames = new ArrayList<>();
        try {
            readers.GetReaders();
            for (Reader reader : readers) {
                readerNames.add(reader.GetDescription().name);
            }
            return ResponseEntity.ok(readerNames);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/select")
    public ResponseEntity<String> selectReader(@RequestParam("readerName") String readerName) {
        try {
            for (Reader reader : readers) {
                if (reader.GetDescription().name.equals(readerName)) {
                    if (selectedReader != null) {
                        selectedReader.Close();
                    }
                    selectedReader = reader;
                    selectedReader.Open(Reader.Priority.EXCLUSIVE);
                    return ResponseEntity.ok("Reader selected: " + readerName);
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
            // Establecer el tamaño esperado de imagen a 140000 bytes
            try {
                Field field = selectedReader.getClass().getDeclaredField("m_nImageSize");
                field.setAccessible(true);
                field.setInt(selectedReader, 140000);
            } catch(Exception e) {
                System.out.println("Warning: could not set m_nImageSize: " + e.getMessage());
            }
            // Crear CaptureThread con resolución 500 DPI y timeout 5000ms
            CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
            capture.start(null);
            capture.join(15000); // Esperar hasta 15 segundos
            CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
            logger.info("event: " + event );
            if (event != null) {
                if (event.capture_result != null && event.capture_result.image != null) {
                    BufferedImage img = convertFidToImage(event.capture_result.image);
                    String base64Image = convertImageToBase64(img);
                    // Captura exitosa: se retorna la imagen en base64
                    return ResponseEntity.ok(base64Image);
                } else if (event.capture_result != null && event.capture_result.quality == Reader.CaptureQuality.TIMED_OUT) {
                    // No se detectó dedo: se retorna un JSON con mensaje amigable
                    String json = "{\"status\":\"timeout\",\"message\":\"No se detectó dedo. Intente de nuevo y mantenga el dedo en el lector.\"}";
                    return ResponseEntity.ok(json);
                } else if (event.reader_status != null) {
                    return ResponseEntity.status(500).body("Capture failed: Reader status " + event.reader_status.status);
                } else {
                    return ResponseEntity.status(500).body("Capture failed: No image received");
                }
            }
            return ResponseEntity.status(500).body("Capture failed: No event received");
        } catch (UareUException | IOException e) {
            return ResponseEntity.status(500).body("Error during capture: " + e.getMessage());
        }
    }

    private BufferedImage convertFidToImage(Fid fid) {
        Fid.Fiv view = fid.getViews()[0];
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setDataElements(0, 0, view.getWidth(), view.getHeight(), view.getImageData());
        return image;
    }

    private String convertImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // Iniciar enrolamiento
    @PostMapping("/enroll/start")
    public ResponseEntity<String> startEnrollment() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        String sessionId = UUID.randomUUID().toString();
        EnrollmentSession session = new EnrollmentSession(sessionId);
        enrollmentSessions.put(sessionId, session);
        return ResponseEntity.ok(sessionId);
    }

    // Capturar huella para enrolamiento
    @PostMapping("/enroll/capture/{sessionId}")
    public ResponseEntity<String> captureForEnrollment(@PathVariable String sessionId) throws IOException {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        EnrollmentSession session = enrollmentSessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.badRequest().body("Invalid session");
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            try {
                Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
                boolean isComplete = session.addCapture(fmd);
                if (isComplete) {
                    Fmd enrollmentFmd = session.createEnrollmentFmd();
                    // Aquí podrías guardar el FMD en una base de datos o devolverlo
                    enrollmentSessions.remove(sessionId); // Limpiar sesión
                    return ResponseEntity.ok("Enrollment complete");
                } else {
                    return ResponseEntity.ok("Capture successful, " + (4 - session.getCaptureCount()) + " captures remaining");
                }
            } catch (UareUException e) {
                return ResponseEntity.status(500).body("Failed to create FMD: " + e.getMessage());
            }
        }
        return ResponseEntity.status(500).body("Capture failed");
    }

    // Iniciar verificación
    @PostMapping("/verify/start")
    public ResponseEntity<String> startVerification() {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        firstVerificationFmd = null;
        return ResponseEntity.ok("Verification started");
    }

    // Capturar huella para verificación
    @PostMapping("/verify/capture")
    public ResponseEntity<String> captureForVerification() throws IOException {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);

        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            try {
                Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
                if (firstVerificationFmd == null) {
                    firstVerificationFmd = fmd;
                    return ResponseEntity.ok("First fingerprint captured, capture second");
                } else {
                    int score = engine.Compare(firstVerificationFmd, 0, fmd, 0);
                    firstVerificationFmd = null; // Resetear para la próxima verificación
                    if (score < Engine.PROBABILITY_ONE / 100000) {
                        return ResponseEntity.ok("Fingerprints match (score: " + score + ")");
                    } else {
                        return ResponseEntity.ok("Fingerprints do not match (score: " + score + ")");
                    }
                }
            } catch (UareUException e) {
                return ResponseEntity.status(500).body("Failed to process fingerprint: " + e.getMessage());
            }
        }
        return ResponseEntity.status(500).body("Capture failed");
    }

    // Enrolar huella para identificación
    @PostMapping("/identify/enroll")
    public ResponseEntity<String> enrollForIdentification() throws IOException {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            try {
                Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
                identificationFmds.add(fmd);
                return ResponseEntity.ok("Fingerprint enrolled for identification (" + identificationFmds.size() + " total)");
            } catch (UareUException e) {
                return ResponseEntity.status(500).body("Failed to enroll fingerprint: " + e.getMessage());
            }
        }
        return ResponseEntity.status(500).body("Capture failed");
    }

    // Identificar una huella
    @PostMapping("/identify")
    public ResponseEntity<String> identifyFingerprint() throws IOException {
        if (selectedReader == null) {
            return ResponseEntity.badRequest().body("No reader selected");
        }
        if (identificationFmds.isEmpty()) {
            return ResponseEntity.badRequest().body("No fingerprints enrolled for identification");
        }
        CaptureThread capture = new CaptureThread(selectedReader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT, 500, 5000);
        capture.start(null);
        capture.join(5000);
        CaptureThread.CaptureEvent event = capture.getLastCaptureEvent();
        if (event != null && event.capture_result != null && event.capture_result.image != null) {
            Engine engine = UareUGlobal.GetEngine();
            try {
                Fmd fmd = engine.CreateFmd(event.capture_result.image, Fmd.Format.ANSI_378_2004);
                int falsepositive_rate = Engine.PROBABILITY_ONE / 100000;
                Engine.Candidate[] candidates = engine.Identify(fmd, 0, identificationFmds.toArray(new Fmd[0]), falsepositive_rate, 1);
                if (candidates.length > 0) {
                    return ResponseEntity.ok("Identified as fingerprint " + candidates[0].fmd_index);
                } else {
                    return ResponseEntity.ok("No match found");
                }
            } catch (UareUException e) {
                return ResponseEntity.status(500).body("Identification failed: " + e.getMessage());
            }
        }
        return ResponseEntity.status(500).body("Capture failed");
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



    private class EnrollmentCallbackImpl implements Engine.EnrollmentCallback {
        private Iterator<Fmd> fmdIterator;

        // Constructor que recibe la lista de Fmd
        public EnrollmentCallbackImpl(List<Fmd> fmds) {
            this.fmdIterator = fmds.iterator();
        }

        @Override
        public Engine.PreEnrollmentFmd GetFmd(Fmd.Format format) {
            if (fmdIterator.hasNext()) {
                // Obtener la siguiente huella de la lista
                Fmd fmd = fmdIterator.next();
                // Crear un objeto PreEnrollmentFmd con la huella
                Engine.PreEnrollmentFmd preFmd = new Engine.PreEnrollmentFmd();
                preFmd.fmd = fmd;
                preFmd.view_index = 0; // Índice de vista, ajusta si es necesario
                return preFmd;
            } else {
                // Devolver null si no hay más huellas
                return null;
            }
        }
    }

    // Clase auxiliar para manejar sesiones de enrolamiento
    private class EnrollmentSession {
        private List<Fmd> fmds = new ArrayList<>();
        private final int requiredCaptures = 4;
        private final String sessionId;

        public EnrollmentSession(String sessionId) {
            this.sessionId = sessionId;
        }

        public boolean addCapture(Fmd fmd) {
            fmds.add(fmd);
            return fmds.size() >= requiredCaptures;
        }

        public Fmd createEnrollmentFmd() throws UareUException {
            Engine engine = UareUGlobal.GetEngine();
            EnrollmentCallbackImpl callback = new EnrollmentCallbackImpl(fmds);
            return engine.CreateEnrollmentFmd(Fmd.Format.ANSI_378_2004, callback);
        }

        public int getCaptureCount() {
            return fmds.size();
        }
    }
}