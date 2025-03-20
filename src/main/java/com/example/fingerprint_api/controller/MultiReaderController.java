package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.service.MultiReaderFingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Controlador para manejar endpoints de multi-lectores.
 */
@RestController
@RequestMapping("/api/v1/multi-fingerprint")
public class MultiReaderController {

    private static final Logger logger = Logger.getLogger(MultiReaderController.class.getName());

    @Autowired
    private MultiReaderFingerprintService multiReaderFingerprintService;

    /**
     * 1) Auto-selecciona y abre todos los lectores encontrados
     */
    @GetMapping("/auto-select")
    public ResponseEntity<?> autoSelect() {
        try {
            List<String> readers = multiReaderFingerprintService.autoSelectReaders();
            return ResponseEntity.ok("Lectores válidos: " + readers);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error seleccionando lectores: " + e.getMessage());
        }
    }

    /**
     * 2) Inicia captura continua en TODOS los lectores válidos
     */
    @PostMapping("/capture/start")
    public ResponseEntity<?> startCaptureForAll() {
        multiReaderFingerprintService.startContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua iniciada para todos los lectores válidos.");
    }

    /**
     * 3) Detener captura en TODOS
     */
    @PostMapping("/capture/stop")
    public ResponseEntity<?> stopCaptureForAll() {
        multiReaderFingerprintService.stopContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua detenida para todos los lectores.");
    }

    /**
     * 4) Inicia captura continua sólo para UN lector
     */
    @PostMapping("/capture/start/{readerName}")
    public ResponseEntity<?> startCaptureForOne(@PathVariable String readerName) {
        boolean result = multiReaderFingerprintService.startContinuousCaptureForReader(readerName);
        if (result) {
            return ResponseEntity.ok("Captura continua iniciada para el lector: " + readerName);
        } else {
            return ResponseEntity.badRequest().body("No se pudo iniciar captura para lector: " + readerName);
        }
    }

    /**
     * 5) Detener la captura para un lector específico
     */
    @PostMapping("/capture/stop/{readerName}")
    public ResponseEntity<?> stopCaptureForReader(@PathVariable("readerName") String readerName) {
        multiReaderFingerprintService.stopCaptureForReader(readerName);
        return ResponseEntity.ok("Captura detenida para lector: " + readerName);
    }

    /**
     * 6) Ver si la captura está activa en un lector
     */
    @GetMapping("/capture/status/{readerName}")
    public ResponseEntity<?> isActive(@PathVariable("readerName") String readerName) {
        boolean active = multiReaderFingerprintService.isCaptureActive(readerName);
        return ResponseEntity.ok("Captura activa para " + readerName + ": " + active);
    }

    /**
     * 7) Ver la última huella (base64) capturada de un lector
     */
    @GetMapping("/last/{readerName}")
    public ResponseEntity<?> getLastFingerprint(@PathVariable("readerName") String readerName) {
        String base64 = multiReaderFingerprintService.getLastCapturedFingerprint(readerName);
        if (base64 == null) {
            return ResponseEntity.ok("No hay huella reciente para el lector: " + readerName);
        }
        return ResponseEntity.ok(base64);
    }

    /**
     * 8) Lista los lectores válidos actualmente registrados
     */
    @GetMapping("/readers")
    public ResponseEntity<Set<String>> getValidReaders() {
        return ResponseEntity.ok(multiReaderFingerprintService.getValidReaderNames());
    }

}

