package com.example.fingerprint_api.service;

import com.example.fingerprint_api.dto.EmpleadoCreateDto;
import com.example.fingerprint_api.dto.EmpleadoUpdateDto; // Asegúrate de importar el DTO correcto
import com.example.fingerprint_api.exception.ApiException;
import com.example.fingerprint_api.exception.ResourceNotFoundException;
import com.example.fingerprint_api.model.Empleado;
import com.example.fingerprint_api.model.Huella;
import com.example.fingerprint_api.repository.EmpleadoRepository;
import com.example.fingerprint_api.repository.HuellaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Importante para update

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmpleadoService {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoService.class);

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private HuellaRepository huellaRepository;

    @Autowired
    private UserService userService; // Asumo que existe para manejo de huellas en memoria

    @Transactional(readOnly = true)
    public List<Empleado> getAllEmpleados() {
        logger.debug("Solicitando todos los empleados");
        List<Empleado> empleados = empleadoRepository.findAll();
        logger.info("Se encontraron {} empleados en la BD.", empleados.size());
        return empleados;
    }

    @Transactional(readOnly = true)
    public Empleado getEmpleadoById(Integer empleadoId) {
        logger.debug("Solicitando empleado con ID: {}", empleadoId);
        return empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + empleadoId));
    }

    @Transactional
    public Empleado createEmpleado(EmpleadoCreateDto createDto) {
        // Validación de unicidad RFC/CURP
        empleadoRepository.findByRfc(createDto.getRfc().toUpperCase())
                .ifPresent(existing -> {
                    throw new ApiException("Ya existe un empleado con el RFC: " + createDto.getRfc(), HttpStatus.CONFLICT);
                });
        empleadoRepository.findByCurp(createDto.getCurp().toUpperCase())
                .ifPresent(existing -> {
                    throw new ApiException("Ya existe un empleado con el CURP: " + createDto.getCurp(), HttpStatus.CONFLICT);
                });

        logger.info("Creando nuevo empleado con RFC: {}", createDto.getRfc());

        Empleado nuevoEmpleado = new Empleado();
        nuevoEmpleado.setRfc(createDto.getRfc().toUpperCase());
        nuevoEmpleado.setCurp(createDto.getCurp().toUpperCase());
        nuevoEmpleado.setPrimerNombre(createDto.getPrimerNombre());
        nuevoEmpleado.setSegundoNombre(createDto.getSegundoNombre());
        nuevoEmpleado.setPrimerApellido(createDto.getPrimerApellido());
        nuevoEmpleado.setSegundoApellido(createDto.getSegundoApellido());
        nuevoEmpleado.setDepartamentoAcademicoId(createDto.getDepartamentoAcademicoId());
        nuevoEmpleado.setDepartamentoAdministrativoId(createDto.getDepartamentoAdministrativoId());
        nuevoEmpleado.setTipoNombramientoPrincipal(createDto.getTipoNombramientoPrincipal());
        nuevoEmpleado.setTipoNombramientoSecundario(createDto.getTipoNombramientoSecundario());
        nuevoEmpleado.setEstatusId(createDto.getEstatusId() != null ? createDto.getEstatusId() : 1); // Default estatus activo
        // UUID se genera en @PrePersist

        Empleado savedEmpleado = empleadoRepository.save(nuevoEmpleado);
        logger.info("Empleado creado con ID: {}", savedEmpleado.getId());
        return savedEmpleado;
    }

    /**
     * Actualiza los datos permitidos de un empleado existente.
     * Incluye validación de unicidad para RFC y CURP si se intentan cambiar.
     */
    @Transactional // Asegura que la operación sea atómica
    public Empleado updateEmpleado(Integer empleadoId, EmpleadoUpdateDto updateDto) {
        // 1. Obtener empleado existente (orElseThrow maneja el 404)
        Empleado empleado = getEmpleadoById(empleadoId);

        logger.info("Actualizando empleado con ID: {}", empleadoId);

        // 2. Validar Unicidad y Actualizar RFC (si cambió y el DTO lo trae)
        if (updateDto.getRfc() != null && !updateDto.getRfc().equalsIgnoreCase(empleado.getRfc())) {
            String nuevoRfc = updateDto.getRfc().toUpperCase();
            Optional<Empleado> existingByRfc = empleadoRepository.findByRfc(nuevoRfc);
            if (existingByRfc.isPresent() && !existingByRfc.get().getId().equals(empleadoId)) {
                // Existe otro empleado con ese RFC
                throw new ApiException("Ya existe otro empleado con el RFC: " + nuevoRfc, HttpStatus.CONFLICT);
            }
            empleado.setRfc(nuevoRfc); // Actualizar si pasó la validación o es el mismo
        }

        // 3. Validar Unicidad y Actualizar CURP (si cambió y el DTO lo trae)
        if (updateDto.getCurp() != null && !updateDto.getCurp().equalsIgnoreCase(empleado.getCurp())) {
            String nuevoCurp = updateDto.getCurp().toUpperCase();
            Optional<Empleado> existingByCurp = empleadoRepository.findByCurp(nuevoCurp);
            if (existingByCurp.isPresent() && !existingByCurp.get().getId().equals(empleadoId)) {
                // Existe otro empleado con ese CURP
                throw new ApiException("Ya existe otro empleado con el CURP: " + nuevoCurp, HttpStatus.CONFLICT);
            }
            empleado.setCurp(nuevoCurp); // Actualizar si pasó la validación o es el mismo
        }

        // 4. Actualizar otros campos si vienen en el DTO
        // Usamos Optional para manejar nulls de forma más limpia (alternativa a los 'if != null')
        Optional.ofNullable(updateDto.getPrimerNombre()).ifPresent(empleado::setPrimerNombre);
        Optional.ofNullable(updateDto.getSegundoNombre()).ifPresent(empleado::setSegundoNombre);
        Optional.ofNullable(updateDto.getPrimerApellido()).ifPresent(empleado::setPrimerApellido);
        Optional.ofNullable(updateDto.getSegundoApellido()).ifPresent(empleado::setSegundoApellido);
        Optional.ofNullable(updateDto.getDepartamentoAcademicoId()).ifPresent(empleado::setDepartamentoAcademicoId);
        Optional.ofNullable(updateDto.getDepartamentoAdministrativoId()).ifPresent(empleado::setDepartamentoAdministrativoId);
        Optional.ofNullable(updateDto.getTipoNombramientoPrincipal()).ifPresent(empleado::setTipoNombramientoPrincipal);
        Optional.ofNullable(updateDto.getTipoNombramientoSecundario()).ifPresent(empleado::setTipoNombramientoSecundario);

        // --- Campos Opcionales (Descomentar si los añades al DTO y quieres editarlos) ---
        // Optional.ofNullable(updateDto.getEstatusId()).ifPresent(empleado::setEstatusId);
        // Optional.ofNullable(updateDto.getCorreoInstitucional()).ifPresent(empleado::setCorreoInstitucional);

        // 5. Guardar cambios (Hibernate detecta cambios y ejecuta UPDATE)
        // El @PreUpdate en la entidad Empleado actualizará updatedAt automáticamente
        Empleado updatedEmpleado = empleadoRepository.save(empleado);
        logger.info("Empleado ID: {} actualizado.", empleadoId);
        return updatedEmpleado;
    }

    // --- Métodos de Huellas (Sin cambios relevantes para la edición de empleado) ---

    @Transactional
    public Huella addHuellaToEmpleado(Integer empleadoId, String nombreDedo, byte[] fmdBytes) throws Exception {
        logger.info("Añadiendo huella para empleado ID: {}, Dedo: {}", empleadoId, nombreDedo);
        return userService.saveNewHuella(empleadoId, nombreDedo, fmdBytes);
    }

    @Transactional(readOnly = true)
    public List<Huella> getHuellasByEmpleadoId(Integer empleadoId) {
        if (!empleadoRepository.existsById(empleadoId)) {
            throw new ResourceNotFoundException("Empleado no encontrado con ID: " + empleadoId);
        }
        logger.debug("Buscando huellas para empleado ID: {}", empleadoId);
        return huellaRepository.findByEmpleadoId(empleadoId);
    }

    @Transactional
    public void deleteHuellaFromEmpleado(Integer empleadoId, Integer huellaId) {
        logger.warn("Intentando eliminar huella ID: {} para empleado ID: {}", huellaId, empleadoId);

        Huella huella = huellaRepository.findById(huellaId)
                .orElseThrow(() -> new ResourceNotFoundException("Huella no encontrada con ID: " + huellaId));

        if (!huella.getEmpleado().getId().equals(empleadoId)) {
            throw new ApiException("La huella ID " + huellaId + " no pertenece al empleado ID " + empleadoId, HttpStatus.BAD_REQUEST);
        }

        huellaRepository.delete(huella);
        userService.removeFmdDataFromMemory(huellaId); // Notificar al servicio en memoria
        logger.info("Huella ID: {} eliminada para empleado ID: {}", huellaId, empleadoId);
    }

    // Considera añadir un método @Transactional public void deleteEmpleado(Integer empleadoId) si lo necesitas
}