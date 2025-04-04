package com.example.fingerprint_api.service;

import com.example.fingerprint_api.dto.EmpleadoCreateDto; // Importar nuevo DTO
import com.example.fingerprint_api.dto.EmpleadoUpdateDto;
import com.example.fingerprint_api.exception.ApiException; // Para error de conflicto
import com.example.fingerprint_api.exception.ResourceNotFoundException;
import com.example.fingerprint_api.model.Empleado;
import com.example.fingerprint_api.model.Huella;
import com.example.fingerprint_api.repository.EmpleadoRepository;
import com.example.fingerprint_api.repository.HuellaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Para ApiException
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID; // Para generar UUID
import java.util.Optional;


@Service
public class EmpleadoService {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoService.class);

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private HuellaRepository huellaRepository;

    @Autowired
    private UserService userService;

    @Transactional(readOnly = true)
    public List<Empleado> getAllEmpleados() {
        logger.debug("Solicitando todos los empleados");
        List<Empleado> empleados = empleadoRepository.findAll();
        logger.info("Se encontraron {} empleados en la BD.", empleados.size()); // Log para verificar
        return empleados;
    }

    @Transactional(readOnly = true)
    public Empleado getEmpleadoById(Integer empleadoId) {
        logger.debug("Solicitando empleado con ID: {}", empleadoId);
        return empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + empleadoId));
    }

    /**
     * Crea un nuevo registro de empleado en la base de datos.
     */
    @Transactional
    public Empleado createEmpleado(EmpleadoCreateDto createDto) {
        // Validación Opcional: Verificar si ya existe RFC o CURP
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
        // Mapear datos del DTO a la entidad
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
        nuevoEmpleado.setEstatusId(createDto.getEstatusId() != null ? createDto.getEstatusId() : 1);
        nuevoEmpleado.setUuid(UUID.randomUUID().toString());

        // Guardar en la BD (el ID se autogenera aquí si es AUTO_INCREMENT)
        Empleado savedEmpleado = empleadoRepository.save(nuevoEmpleado);
        logger.info("Empleado creado con ID: {}", savedEmpleado.getId());
        return savedEmpleado;
    }

    /**
     * Actualiza los datos permitidos de un empleado existente.
     */
    @Transactional
    public Empleado updateEmpleado(Integer empleadoId, EmpleadoUpdateDto updateDto) {
        Empleado empleado = getEmpleadoById(empleadoId);

        logger.info("Actualizando empleado con ID: {}", empleadoId);

        // Actualizar campos del DTO (solo si no son null en el DTO, para permitir actualizaciones parciales)
        // Nota: Si un campo debe poder ponerse a NULL, la lógica debería ser diferente.
        if (updateDto.getRfc() != null) empleado.setRfc(updateDto.getRfc().toUpperCase());
        if (updateDto.getCurp() != null) empleado.setCurp(updateDto.getCurp().toUpperCase());
        if (updateDto.getPrimerNombre() != null) empleado.setPrimerNombre(updateDto.getPrimerNombre());
        if (updateDto.getSegundoNombre() != null) empleado.setSegundoNombre(updateDto.getSegundoNombre());
        if (updateDto.getPrimerApellido() != null) empleado.setPrimerApellido(updateDto.getPrimerApellido());
        if (updateDto.getSegundoApellido() != null) empleado.setSegundoApellido(updateDto.getSegundoApellido());
        if (updateDto.getDepartamentoAcademicoId() != null) empleado.setDepartamentoAcademicoId(updateDto.getDepartamentoAcademicoId());
        if (updateDto.getDepartamentoAdministrativoId() != null) empleado.setDepartamentoAdministrativoId(updateDto.getDepartamentoAdministrativoId());
        if (updateDto.getTipoNombramientoPrincipal() != null) empleado.setTipoNombramientoPrincipal(updateDto.getTipoNombramientoPrincipal());
        if (updateDto.getTipoNombramientoSecundario() != null) empleado.setTipoNombramientoSecundario(updateDto.getTipoNombramientoSecundario());
        // Considera si el estatus se puede actualizar aquí

        return empleadoRepository.save(empleado);
    }

    /**
     * Añade una nueva huella a un empleado existente.
     */
    @Transactional
    public Huella addHuellaToEmpleado(Integer empleadoId, String nombreDedo, byte[] fmdBytes) throws Exception {
        logger.info("Añadiendo huella para empleado ID: {}, Dedo: {}", empleadoId, nombreDedo);
        // Delegar a UserService que maneja la BD y la caché en memoria
        return userService.saveNewHuella(empleadoId, nombreDedo, fmdBytes);
    }

    /**
     * Obtiene la lista de huellas registradas para un empleado.
     */
    @Transactional(readOnly = true)
    public List<Huella> getHuellasByEmpleadoId(Integer empleadoId) {
        if (!empleadoRepository.existsById(empleadoId)) {
            throw new ResourceNotFoundException("Empleado no encontrado con ID: " + empleadoId);
        }
        logger.debug("Buscando huellas para empleado ID: {}", empleadoId);
        return huellaRepository.findByEmpleadoId(empleadoId);
    }

    /**
     * Elimina una huella específica de un empleado.
     */
    @Transactional
    public void deleteHuellaFromEmpleado(Integer empleadoId, Integer huellaId) {
        logger.warn("Intentando eliminar huella ID: {} para empleado ID: {}", huellaId, empleadoId);

        Huella huella = huellaRepository.findById(huellaId)
                .orElseThrow(() -> new ResourceNotFoundException("Huella no encontrada con ID: " + huellaId));

        if (!huella.getEmpleado().getId().equals(empleadoId)) {
            throw new ApiException("La huella ID " + huellaId + " no pertenece al empleado ID " + empleadoId, HttpStatus.BAD_REQUEST);
        }

        huellaRepository.delete(huella); // Borrar por entidad

        // Notificar a UserService para limpiar la memoria
        userService.removeFmdDataFromMemory(huellaId);
        logger.info("Huella ID: {} eliminada para empleado ID: {}", huellaId, empleadoId);
    }

    // Podrías añadir deleteEmpleado aquí si es necesario
}