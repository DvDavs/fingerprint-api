package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.service.MultiReaderFingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/multi-fingerprint")
public class MultiReaderController {

    private static final Logger logger = Logger.getLogger(MultiReaderController.class.getName());

    @Autowired
    private MultiReaderFingerprintService multiService;

    /**
     * 1) Auto-selecciona y abre lectores compatibles (DigitalPersona), retorna los nombres disponibles
     */
    @GetMapping("/auto-select")
    public ResponseEntity<?> autoSelect() {
        try {
            List<String> readers = multiService.refreshConnectedReaders();
            return ResponseEntity.ok(readers);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * 2) Obtiene la lista de lectores que ya tenemos en "validReaders".
     *    (Podrías filtrar para no mostrar los que estén en inUseReaders, si así lo prefieres)
     */
    @GetMapping("/readers")
    public ResponseEntity<?> getReaders() {
        Set<String> readers = multiService.getAvailableReaderNames();
        return ResponseEntity.ok(readers);
    }

    /**
     * 3) Reservar un lector para que no lo use otra pantalla.
     */
    @PostMapping("/reserve/{readerName}")
    public ResponseEntity<?> reserveReader(@PathVariable String readerName, @RequestParam String sessionId) {
        boolean ok = multiService.reserveReader(readerName, sessionId);
        if (ok) {

            return ResponseEntity.ok("Lector reservado: " + readerName + " para sesión: " + sessionId);
        } else {
            return ResponseEntity.badRequest().body("No se pudo reservar lector: " + readerName );
        }
    }

    /**
     * 4) Liberar un lector que estaba en uso (para que otra pantalla pueda seleccionarlo).
     */
    @PostMapping("/release/{readerName}")
    public ResponseEntity<?> releaseReader(@PathVariable String readerName, @RequestParam(required = false) String sessionId) {
        if (sessionId != null) {
            multiService.releaseReaderBySession(sessionId);
            return ResponseEntity.ok("Lector liberado por sesión: " + sessionId);
        } else {
            multiService.releaseReader(readerName);
            return ResponseEntity.ok("Lector liberado: " + readerName);
        }
    }

    /**
     * 5) Inicia captura continua en TODOS los lectores válidos.
     */
    @PostMapping("/capture/start")
    public ResponseEntity<?> startCaptureForAll() {
        multiService.startContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua iniciada en TODOS");
    }

    /**
     * 6) Inicia captura continua para un lector específico (modo normal).
     */
    @PostMapping("/capture/start/{readerName}")
    public ResponseEntity<?> startCaptureOne(@PathVariable String readerName) {
        boolean ok = multiService.startContinuousCaptureForReader(readerName);
        if (ok) {
            return ResponseEntity.ok("Captura continua iniciada para " + readerName);
        } else {
            return ResponseEntity.badRequest().body("No se pudo iniciar en " + readerName);
        }
    }

    /**
     * 7) Detener captura (continua) en TODOS
     */
    @PostMapping("/capture/stop")
    public ResponseEntity<?> stopAll() {
        multiService.stopContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua detenida en TODOS");
    }

    /**
     * 8) Detener captura para un lector específico
     */
    @PostMapping("/capture/stop/{readerName}")
    public ResponseEntity<?> stopOne(@PathVariable String readerName) {
        multiService.stopCaptureForReader(readerName);
        return ResponseEntity.ok("Captura detenida en " + readerName);
    }

    /**
     * 9) Estado de si la captura está activa
     */
    @GetMapping("/capture/status/{readerName}")
    public ResponseEntity<?> isActive(@PathVariable String readerName) {
        boolean active = multiService.isCaptureActive(readerName);
        return ResponseEntity.ok("Captura activa para " + readerName + ": " + active);
    }

    /**
     * 10) Muestra la última huella (base64) capturada
     */
    @GetMapping("/last/{readerName}")
    public ResponseEntity<?> getLast(@PathVariable String readerName) {
        String base64 = multiService.getLastCapturedFingerprint(readerName);
        if (base64 == null) {
            return ResponseEntity.ok("No hay huella reciente para: " + readerName);
        }
        return ResponseEntity.ok(base64);
    }

    /**
     * ==============================================
     *   SECCIÓN DE RELOJ CHECADOR
     * ==============================================
     */

    /**
     * Inicia captura continua MODO CHECADOR en un lector (identifica y notifica al front).
     */
    @PostMapping("/checador/start/{readerName}")
    public ResponseEntity<?> startChecador(@PathVariable String readerName) {
        boolean ok = multiService.startChecadorForReader(readerName);
        if (ok) {
            return ResponseEntity.ok("Checador iniciado en " + readerName);
        } else {
            return ResponseEntity.badRequest().body("No se pudo iniciar checador en " + readerName);
        }
    }

    /**
     * Detiene la captura modo checador (en la práctica, es lo mismo que stopCaptureForReader).
     */
    @PostMapping("/checador/stop/{readerName}")
    public ResponseEntity<?> stopChecador(@PathVariable String readerName) {
        multiService.stopCaptureForReader(readerName);
        return ResponseEntity.ok("Checador detenido en " + readerName);
    }

    /**
     * ==============================================
     *   SECCIÓN DE ENROLAMIENTO
     * ==============================================
     */

    /**
     * a) Inicia sesión de enrolamiento, retorna sessionId
     */
    @PostMapping("/enroll/start/{readerName}")
    public ResponseEntity<?> startEnrollment(@PathVariable String readerName) {
        try {
            String sessionId = multiService.startEnrollment(readerName);
            return ResponseEntity.ok(sessionId);
        } catch (UareUException e) {
            return ResponseEntity.status(500).body("Error al iniciar enrolamiento: " + e.getMessage());
        }
    }

    /**
     * b) Captura y añade a la sesión. Si se completa, retorna {complete:true,template=...}
     */
    @PostMapping("/enroll/capture/{readerName}/{sessionId}")
    public ResponseEntity<?> captureEnrollment(
            @PathVariable String readerName,
            @PathVariable String sessionId,
            @RequestParam(required = false) Long userId
    ) {
        try {
            Map<String, Object> result = multiService.captureForEnrollment(readerName, sessionId);
            // Si se completó, y userId != null, guardar en BD:
            if (Boolean.TRUE.equals(result.get("complete")) && userId != null) {
                String base64Template = (String) result.get("template");
                byte[] fmdBytes = Base64.getDecoder().decode(base64Template);
                // Guardar la huella en la BD
                // (Esto es opcional, depende tu flujo).
                multiService.userService.saveFingerprintTemplate(userId, fmdBytes);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.severe("Error en enrolamiento: " + e.getMessage());
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
