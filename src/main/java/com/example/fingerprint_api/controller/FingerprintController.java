package com.example.fingerprint_api.controller;

import com.example.fingerprint_api.service.FingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.fingerprint_api.service.UserService;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fingerprint")
public class FingerprintController {

    @Autowired
    private FingerprintService fingerprintService;

    @Autowired
    private UserService userService; // para guardar la huella

    @GetMapping("/readers")
    public ResponseEntity<List<String>> getReaders() throws Exception {
        List<String> readers = fingerprintService.getReaders();
        return ResponseEntity.ok(readers);
    }

    @PostMapping("/select")
    public ResponseEntity<String> selectReader(@RequestParam("readerName") String name) throws Exception {
        String result = fingerprintService.selectReader(name);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getCapabilities() throws Exception {
        Map<String, Object> caps = fingerprintService.getCapabilities();
        return ResponseEntity.ok(caps);
    }

    @PostMapping("/enroll/start")
    public ResponseEntity<String> startEnrollment() throws Exception {
        String sessionId = fingerprintService.startEnrollment();
        return ResponseEntity.ok(sessionId);
    }

    @PostMapping("/enroll/capture/{sessionId}/{userId}")
    public ResponseEntity<Map<String, Object>> captureForEnrollment(
            @PathVariable String sessionId,
            @PathVariable Long userId) throws Exception {

        // Llama al método que captura y retorna (complete, template en Base64, etc.)
        Map<String, Object> response = fingerprintService.captureForEnrollmentResponse(sessionId);

        // Si el enrolamiento ya se completó, guardamos en BD
        if (Boolean.TRUE.equals(response.get("complete"))) {
            String base64Template = (String) response.get("template");
            byte[] fmdBytes = Base64.getDecoder().decode(base64Template);

            // guardar en la BD, asociándolo al usuario con ID "userId"
            userService.saveFingerprintTemplate(userId, fmdBytes);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify/start")
    public ResponseEntity<String> startVerification() throws Exception {
        String result = fingerprintService.startVerification();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/identify/enroll")
    public ResponseEntity<String> enrollForIdentification() throws Exception {
        String result = fingerprintService.enrollForIdentification();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/capture")
    public ResponseEntity<String> captureFingerprint(@RequestParam("mode") String mode) throws Exception {
        String result;
        switch (mode.toLowerCase()) {
            case "register":
                // Se utiliza el método de captura para registro
                result = fingerprintService.captureFingerprint();
                break;
            case "verify":
                // Se utiliza el método de captura para verificación
                result = fingerprintService.captureForVerification();
                break;
            case "identify":
                // Se utiliza el método de captura para identificación
                result = fingerprintService.identifyFingerprint();
                break;
            default:
                throw new IllegalArgumentException("Modo no soportado: " + mode);
        }
        return ResponseEntity.ok(result);
    }
}
