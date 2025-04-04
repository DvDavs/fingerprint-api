package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.service.MultiReaderFingerprintService;
import org.slf4j.Logger; // *** CAMBIO: Usar SLF4J Logger ***
import org.slf4j.LoggerFactory; // *** CAMBIO: Usar SLF4J LoggerFactory ***
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Necesario para error
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64; // Eliminar si ya no se usa aquí
import java.util.List;
import java.util.Map;
import java.util.Set;
// import java.util.logging.Logger; // ELIMINAR

@RestController
@RequestMapping("/api/v1/multi-fingerprint")
public class MultiReaderController {

    // *** CAMBIO: Usar SLF4J Logger ***
    private static final Logger logger = LoggerFactory.getLogger(MultiReaderController.class);

    @Autowired
    private MultiReaderFingerprintService multiService;

    // --- Endpoints de Gestión de Lectores y Captura (sin cambios lógicos, solo logging) ---
    @GetMapping("/auto-select")
    public ResponseEntity<?> autoSelect() {
        try {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Solicitando auto-selección de lectores...");
            List<String> readers = multiService.refreshConnectedReaders();
            return ResponseEntity.ok(readers);
        } catch (UareUException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error UareU en /auto-select: Código {}, Mensaje {}", e.getCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al refrescar lectores: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inesperado en /auto-select", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error inesperado.");
        }
    }

    @GetMapping("/readers")
    public ResponseEntity<?> getReaders() {
        // *** CAMBIO: Logging SLF4J ***
        logger.debug("Solicitando lista de lectores disponibles...");
        Set<String> readers = multiService.getAvailableReaderNames();
        return ResponseEntity.ok(readers);
    }

    @PostMapping("/reserve/{readerName}")
    public ResponseEntity<?> reserveReader(@PathVariable String readerName, @RequestParam String sessionId) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Intentando reservar lector {} para sesión {}", readerName, sessionId);
        boolean ok = multiService.reserveReader(readerName, sessionId);
        if (ok) {
            return ResponseEntity.ok("Lector reservado: " + readerName + " para sesión: " + sessionId);
        } else {
            // El servicio ya loguea el warning
            return ResponseEntity.badRequest().body("No se pudo reservar lector: " + readerName + " (ya en uso o inválido)");
        }
    }

    @PostMapping("/release/{readerName}")
    public ResponseEntity<?> releaseReader(@PathVariable String readerName, @RequestParam(required = false) String sessionId) {
        if (sessionId != null) {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Intentando liberar lector por sesión {}", sessionId);
            multiService.releaseReaderBySession(sessionId);
            return ResponseEntity.ok("Lector liberado por sesión: " + sessionId);
        } else {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Intentando liberar lector manualmente: {}", readerName);
            multiService.releaseReader(readerName);
            return ResponseEntity.ok("Lector liberado: " + readerName);
        }
    }

    @PostMapping("/capture/start")
    public ResponseEntity<?> startCaptureForAll() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para iniciar captura en todos los lectores disponibles");
        multiService.startContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua iniciada en lectores disponibles.");
    }

    @PostMapping("/capture/start/{readerName}")
    public ResponseEntity<?> startCaptureOne(@PathVariable String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para iniciar captura continua en {}", readerName);
        boolean ok = multiService.startContinuousCaptureForReader(readerName);
        if (ok) {
            return ResponseEntity.ok("Captura continua iniciada para " + readerName);
        } else {
            // El servicio ya loguea el warning
            return ResponseEntity.badRequest().body("No se pudo iniciar captura en " + readerName + " (inválido o reservado).");
        }
    }

    @PostMapping("/capture/stop")
    public ResponseEntity<?> stopAll() {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para detener todas las capturas continuas.");
        multiService.stopContinuousCaptureForAll();
        return ResponseEntity.ok("Captura continua detenida en TODOS.");
    }

    @PostMapping("/capture/stop/{readerName}")
    public ResponseEntity<?> stopOne(@PathVariable String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para detener captura continua en {}", readerName);
        multiService.stopCaptureForReader(readerName);
        return ResponseEntity.ok("Captura detenida en " + readerName);
    }

    @GetMapping("/capture/status/{readerName}")
    public ResponseEntity<?> isActive(@PathVariable String readerName) {
        boolean active = multiService.isCaptureActive(readerName);
        // *** CAMBIO: Logging SLF4J ***
        logger.debug("Verificando estado de captura para {}: {}", readerName, active);
        return ResponseEntity.ok(Map.of("readerName", readerName, "isActive", active)); // Devolver JSON es más útil
    }

    @GetMapping("/last/{readerName}")
    public ResponseEntity<?> getLast(@PathVariable String readerName) {
        String base64 = multiService.getLastCapturedFingerprint(readerName);
        if (base64 == null) {
            // *** CAMBIO: Logging SLF4J ***
            logger.debug("No hay imagen reciente para lector {}", readerName);
            return ResponseEntity.ok(Map.of("message", "No hay huella reciente para: " + readerName));
        }
        // Devolver la imagen como JSON
        return ResponseEntity.ok(Map.of("readerName", readerName, "base64Image", base64));
    }


    // --- Endpoints Modo Checador (sin cambios lógicos, solo logging) ---
    @PostMapping("/checador/start/{readerName}")
    public ResponseEntity<?> startChecador(@PathVariable String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para iniciar modo checador en {}", readerName);
        boolean ok = multiService.startChecadorForReader(readerName);
        if (ok) {
            return ResponseEntity.ok("Checador iniciado en " + readerName);
        } else {
            // El servicio ya loguea el warning
            return ResponseEntity.badRequest().body("No se pudo iniciar checador en " + readerName + " (inválido o ya en uso).");
        }
    }

    @PostMapping("/checador/stop/{readerName}")
    public ResponseEntity<?> stopChecador(@PathVariable String readerName) {
        // *** CAMBIO: Logging SLF4J ***
        logger.info("Solicitud para detener modo checador en {}", readerName);
        multiService.stopCaptureForReader(readerName); // Usa el mismo método stop
        return ResponseEntity.ok("Checador detenido en " + readerName);
    }


    // --- Endpoints Enrolamiento --- MODIFICADO ---
    @PostMapping("/enroll/start/{readerName}")
    public ResponseEntity<?> startEnrollment(@PathVariable String readerName) {
        try {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Solicitud para iniciar enrolamiento en {}", readerName);
            String sessionId = multiService.startEnrollment(readerName);
            return ResponseEntity.ok(Map.of("sessionId", sessionId)); // Devolver JSON
        } catch (UareUException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error UareU al iniciar enrolamiento en {}: Código {}, Mensaje {}", readerName, e.getCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al iniciar enrolamiento: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error inesperado al iniciar enrolamiento en {}", readerName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error inesperado.");
        }
    }

    /**
     * Captura una huella para una sesión de enrolamiento existente.
     * Si el enrolamiento se completa, devuelve el template en Base64.
     * YA NO GUARDA AUTOMÁTICAMENTE EN LA BASE DE DATOS.
     * El cliente debe tomar el 'template' devuelto y llamar a POST /api/empleados/{id}/huellas.
     */
    @PostMapping("/enroll/capture/{readerName}/{sessionId}")
    public ResponseEntity<?> captureEnrollment(
            @PathVariable String readerName,
            @PathVariable String sessionId
            // *** CAMBIO: ELIMINAR @RequestParam userId ***
    ) {
        try {
            // *** CAMBIO: Logging SLF4J ***
            logger.info("Solicitud de captura para enrolamiento en {}, sesión {}", readerName, sessionId);
            // multiService ya maneja excepciones UareU y de Interrupción
            Map<String, Object> result = multiService.captureForEnrollment(readerName, sessionId);

            // *** CAMBIO: ELIMINAR EL BLOQUE DE GUARDADO AUTOMÁTICO ***
            /*
            if (Boolean.TRUE.equals(result.get("complete")) && userId != null) {
                String base64Template = (String) result.get("template");
                byte[] fmdBytes = Base64.getDecoder().decode(base64Template);
                // multiService.userService.saveFingerprintTemplate(userId, fmdBytes); // YA NO SE HACE AQUÍ
            }
            */

            return ResponseEntity.ok(result); // Devuelve {complete: boolean, remaining?: int, template?: string}
        } catch (UareUException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error UareU durante captura de enrolamiento en {}, sesión {}: Código {}, Mensaje {}",
                    readerName, sessionId, e.getCode(), e.getMessage(), e);
            // Devolver error específico de UareU si es necesario
            if (e.getCode() == 96075796 /* Sesión inválida */ || e.getCode() == 96075788 /* Timeout/Fallo captura */) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en captura de enrolamiento: " + e.toString());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error en captura de enrolamiento: " + e.getMessage());
        } catch (InterruptedException e) {
            // *** CAMBIO: Logging SLF4J ***
            logger.warn("Captura de enrolamiento interrumpida en {}, sesión {}", readerName, sessionId);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Captura interrumpida."); // 409 Conflict podría ser
        } catch (Exception e) { // Otros errores (ej. Base64 al devolver template)
            // *** CAMBIO: Logging SLF4J ***
            logger.error("Error inesperado durante captura de enrolamiento en {}, sesión {}: {}", readerName, sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error inesperado en enrolamiento.");
        }
    }
}