package com.example.fingerprint_api.controller;

import com.example.fingerprint_api.dto.AddHuellaRequestDto;
import com.example.fingerprint_api.dto.EmpleadoCreateDto; // Importar DTO
import com.example.fingerprint_api.dto.EmpleadoUpdateDto;
import com.example.fingerprint_api.dto.HuellaDto;
import com.example.fingerprint_api.exception.ApiException;
import com.example.fingerprint_api.exception.ResourceNotFoundException;
import com.example.fingerprint_api.model.Empleado;
import com.example.fingerprint_api.model.Huella;
import com.example.fingerprint_api.service.EmpleadoService;
import jakarta.validation.Valid; // Para validar DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map; // Para devolver JSON simple
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoController.class);

    @Autowired
    private EmpleadoService empleadoService;

    @GetMapping
    public ResponseEntity<List<Empleado>> getAllEmpleados() {
        logger.info("GET /api/empleados solicitado"); // Log para verificar llamada
        List<Empleado> empleados = empleadoService.getAllEmpleados();
        // Log si la lista está vacía
        if (empleados.isEmpty()) {
            logger.info("GET /api/empleados devolvió una lista vacía.");
        } else {
            logger.info("GET /api/empleados devolvió {} empleados.", empleados.size());
        }
        return ResponseEntity.ok(empleados);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Empleado> getEmpleadoById(@PathVariable Integer id) {
        Empleado empleado = empleadoService.getEmpleadoById(id);
        return ResponseEntity.ok(empleado);
    }

    /**
     * Crea un nuevo empleado.
     * Se activa con @Valid para usar las validaciones del DTO.
     */
    @PostMapping
    public ResponseEntity<Empleado> createEmpleado(@Valid @RequestBody EmpleadoCreateDto createDto) {
        // Si @Valid falla, GlobalExceptionHandler (o un handler específico de MethodArgumentNotValidException)
        // debería devolver un 400 Bad Request automáticamente.
        Empleado nuevoEmpleado = empleadoService.createEmpleado(createDto); // El servicio maneja conflicto RFC/CURP
        logger.info("POST /api/empleados - Empleado creado con ID: {}", nuevoEmpleado.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoEmpleado);
        // Considera devolver DTO
    }

    @PutMapping("/{id}")
    public ResponseEntity<Empleado> updateEmpleado(@PathVariable Integer id, @Valid @RequestBody EmpleadoUpdateDto updateDto) {
        // @Valid también puede aplicar aquí si defines validaciones en EmpleadoUpdateDto
        Empleado updatedEmpleado = empleadoService.updateEmpleado(id, updateDto);
        logger.info("PUT /api/empleados/{} - Empleado actualizado.", id);
        return ResponseEntity.ok(updatedEmpleado);
        // Considera devolver DTO
    }

    @GetMapping("/{empleadoId}/huellas")
    public ResponseEntity<List<HuellaDto>> getHuellasByEmpleadoId(@PathVariable Integer empleadoId) {
        List<Huella> huellas = empleadoService.getHuellasByEmpleadoId(empleadoId);
        List<HuellaDto> dtos = huellas.stream()
                .map(HuellaDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{empleadoId}/huellas")
    public ResponseEntity<?> addHuella( // Cambiado a ResponseEntity<?> para más flexibilidad en errores
                                        @PathVariable Integer empleadoId,
                                        @Valid @RequestBody AddHuellaRequestDto requestDto) { // Añadir @Valid
        try {
            // La validación @NotEmpty se maneja antes si se usa @Valid
            byte[] fmdBytes = Base64.getDecoder().decode(requestDto.getTemplateBase64());
            Huella nuevaHuella = empleadoService.addHuellaToEmpleado(
                    empleadoId,
                    requestDto.getNombreDedo().toUpperCase(), // Guardar en mayúsculas consistentemente
                    fmdBytes
            );
            logger.info("POST /{}/huellas - Huella añadida con ID: {}", empleadoId, nuevaHuella.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(HuellaDto.fromEntity(nuevaHuella));

        } catch (IllegalArgumentException e) {
            logger.error("POST /{}/huellas - Error al decodificar Base64: {}", empleadoId, e.getMessage());
            // Devolver error 400
            return ResponseEntity.badRequest().body(Map.of("error", "Formato Base64 inválido para el template."));
        } catch (ResourceNotFoundException e) { // Capturar si el empleado no existe
            logger.warn("POST /{}/huellas - Intento de añadir huella a empleado no existente: {}", empleadoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) { // Otros errores (Ej: Encriptación, BD)
            logger.error("POST /{}/huellas - Error al añadir huella: {}", empleadoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno al guardar la huella."));
        }
    }

    @DeleteMapping("/{empleadoId}/huellas/{huellaId}")
    public ResponseEntity<Void> deleteHuella(
            @PathVariable Integer empleadoId,
            @PathVariable Integer huellaId) {
        // El servicio maneja ResourceNotFoundException si no existe o no coincide
        empleadoService.deleteHuellaFromEmpleado(empleadoId, huellaId);
        logger.info("DELETE /{}/huellas/{} - Huella eliminada.", empleadoId, huellaId);
        return ResponseEntity.noContent().build();
    }
}