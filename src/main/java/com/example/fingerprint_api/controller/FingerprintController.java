package com.example.fingerprint_api.controller;

import com.example.fingerprint_api.service.FingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fingerprint")
public class FingerprintController {

    @Autowired
    private FingerprintService fingerprintService;

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

    @GetMapping("/capture")
    public ResponseEntity<String> captureFingerprint() throws Exception {
        String result = fingerprintService.captureFingerprint();
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

    @PostMapping("/enroll/capture/{sessionId}")
    public ResponseEntity<Map<String, Object>> captureForEnrollment(@PathVariable String sessionId) throws Exception {
        Map<String, Object> response = fingerprintService.captureForEnrollmentResponse(sessionId);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/verify/start")
    public ResponseEntity<String> startVerification() throws Exception {
        String result = fingerprintService.startVerification();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify/capture")
    public ResponseEntity<String> captureForVerification() throws Exception {
        String result = fingerprintService.captureForVerification();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/identify/enroll")
    public ResponseEntity<String> enrollForIdentification() throws Exception {
        String result = fingerprintService.enrollForIdentification();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/identify")
    public ResponseEntity<String> identifyFingerprint() throws Exception {
        String result = fingerprintService.identifyFingerprint();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<String> getReaderStatus() throws Exception {
        String status = fingerprintService.getReaderStatus();
        return ResponseEntity.ok(status);
    }
}
