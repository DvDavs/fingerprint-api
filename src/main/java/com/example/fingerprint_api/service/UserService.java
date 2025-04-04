package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.exception.ResourceNotFoundException; // Necesitaremos crear esta excepción
import com.example.fingerprint_api.model.Empleado;
import com.example.fingerprint_api.model.Huella;
import com.example.fingerprint_api.repository.EmpleadoRepository;
import com.example.fingerprint_api.repository.HuellaRepository;
import com.example.fingerprint_api.util.CryptoUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Para operaciones de escritura

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList; // Para seguridad en concurrencia
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Inyectamos los nuevos repositorios
    @Autowired
    private HuellaRepository huellaRepository;
    @Autowired
    private EmpleadoRepository empleadoRepository;

    // Estructura en memoria para identificación rápida [FMD + empleadoId + huellaId]
    // Usamos CopyOnWriteArrayList para seguridad en lecturas concurrentes durante la identificación
    // mientras ocurren escrituras (nuevas huellas/eliminaciones)
    private final List<FmdData> fmdDataList = new CopyOnWriteArrayList<>();

    // Clase interna para almacenar los datos necesarios en memoria
    private static class FmdData {
        final Fmd fmd;
        final Integer empleadoId;
        final Integer huellaId;

        FmdData(Fmd fmd, Integer empleadoId, Integer huellaId) {
            this.fmd = fmd;
            this.empleadoId = empleadoId;
            this.huellaId = huellaId;
        }
    }

    @PostConstruct
    @Transactional(readOnly = true) // Buena práctica para operaciones de solo lectura
    public void init() {
        loadFmdsIntoMemory();
    }

    public void loadFmdsIntoMemory() {
        logger.info("Cargando FMDs de huellas en memoria...");
        fmdDataList.clear(); // Limpiamos antes de recargar
        List<Huella> allHuellas = huellaRepository.findAll();
        int count = 0;
        for (Huella huella : allHuellas) {
            if (huella.getTemplateFmd() != null && huella.getEmpleado() != null) {
                try {
                    byte[] decryptedFmdBytes = CryptoUtils.decrypt(huella.getTemplateFmd());
                    Fmd fmd = UareUGlobal.GetImporter().ImportFmd(
                            decryptedFmdBytes,
                            Fmd.Format.ANSI_378_2004, // Formato estándar para comparación
                            Fmd.Format.ANSI_378_2004
                    );
                    // Añadimos a la lista concurrente
                    fmdDataList.add(new FmdData(fmd, huella.getEmpleado().getId(), huella.getId()));
                    count++;
                } catch (UareUException e) {
                    logger.error("Error UareU al importar FMD para huella ID {}: {}", huella.getId(), e.getMessage());
                } catch (Exception e) {
                    logger.error("Error al desencriptar o importar FMD para huella ID {}: {}", huella.getId(), e.getMessage(), e);
                }
            } else {
                logger.warn("Huella ID {} omitida: template o empleado nulo.", huella.getId());
            }
        }
        logger.info("Se cargaron {} FMDs en memoria.", count);
    }

    /**
     * Identifica un FMD capturado contra las huellas cargadas en memoria.
     * Devuelve el Empleado correspondiente si se encuentra una coincidencia.
     *
     * @param capturedFmd El FMD capturado a identificar.
     * @return Optional<Empleado> con el empleado si se identifica, Optional.empty() si no.
     * @throws UareUException Si ocurre un error en el motor de comparación.
     */
    // No necesita synchronized explícito por usar CopyOnWriteArrayList (optimizado para lecturas)
    public Optional<Empleado> identifyUser(Fmd capturedFmd) throws UareUException {
        if (fmdDataList.isEmpty()) {
            logger.warn("Intento de identificación sin FMDs en memoria.");
            return Optional.empty();
        }

        // Extraer solo los FMDs para la comparación
        Fmd[] fmdsToCompare = fmdDataList.stream().map(data -> data.fmd).toArray(Fmd[]::new);

        Engine engine = UareUGlobal.GetEngine();
        // Ajusta el umbral según sea necesario (más bajo = más estricto)
        int threshold = Engine.PROBABILITY_ONE / 100000; // FAR 1 en 100k

        logger.debug("Iniciando identificación 1:N con {} FMDs en memoria.", fmdsToCompare.length);

        Engine.Candidate[] candidates = engine.Identify(
                capturedFmd,
                0, // first view
                fmdsToCompare,
                threshold,
                1 // Máximo 1 resultado (el mejor)
        );

        if (candidates.length > 0) {
            int bestMatchIndex = candidates[0].fmd_index;
            // Validar índice (aunque CopyOnWriteArrayList es estable durante la iteración)
            if (bestMatchIndex >= 0 && bestMatchIndex < fmdDataList.size()) {
                FmdData matchedData = fmdDataList.get(bestMatchIndex); // Obtener datos del índice coincidente
                Integer empleadoId = matchedData.empleadoId;
                logger.info("Huella identificada! Índice: {}, Huella ID: {}, Empleado ID: {}", bestMatchIndex, matchedData.huellaId, empleadoId);
                // Buscar el empleado completo en la BD
                return empleadoRepository.findById(empleadoId);
            } else {
                logger.error("Índice de candidato inválido ({}) fuera de rango (0-{}).", bestMatchIndex, fmdDataList.size() - 1);
                return Optional.empty(); // Índice inválido
            }
        }

        logger.debug("Identificación 1:N no encontró coincidencias.");
        return Optional.empty();
    }

    /**
     * Guarda una nueva huella para un empleado y actualiza la memoria.
     *
     * @param empleadoId ID del empleado.
     * @param nombreDedo Nombre/identificador del dedo.
     * @param fmdBytes   Bytes del FMD (aún no encriptados).
     * @return La entidad Huella guardada.
     * @throws ResourceNotFoundException Si el empleado no existe.
     * @throws Exception                 Si ocurre un error al encriptar o guardar.
     */
    @Transactional // Operación de escritura
    public Huella saveNewHuella(Integer empleadoId, String nombreDedo, byte[] fmdBytes) throws Exception {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + empleadoId));

        // 1. Encriptar FMD
        byte[] encryptedFmd = CryptoUtils.encrypt(fmdBytes);

        // 2. Crear entidad Huella
        Huella nuevaHuella = new Huella();
        nuevaHuella.setEmpleado(empleado);
        nuevaHuella.setNombreDedo(nombreDedo);
        nuevaHuella.setTemplateFmd(encryptedFmd);
        nuevaHuella.setUuid(UUID.randomUUID().toString()); // Generar UUID

        // 3. Guardar en BD
        Huella savedHuella = huellaRepository.save(nuevaHuella);
        logger.info("Nueva huella guardada en BD con ID: {}", savedHuella.getId());


        // 4. Añadir a la memoria (importante!)
        try {
            Fmd fmdInMemory = UareUGlobal.GetImporter().ImportFmd(
                    fmdBytes, // Usamos los bytes originales (sin encriptar) para la memoria
                    Fmd.Format.ANSI_378_2004,
                    Fmd.Format.ANSI_378_2004
            );
            // Añadir a la lista concurrente
            fmdDataList.add(new FmdData(fmdInMemory, empleadoId, savedHuella.getId()));
            logger.info("FMD para huella ID {} añadido a la memoria.", savedHuella.getId());
        } catch(UareUException e){
            logger.error("Error UareU al importar FMD a memoria para nueva huella ID {}: {}", savedHuella.getId(), e.getMessage());
            // Considerar si lanzar una excepción aquí o solo loggear,
            // dependiendo de si la identificación inmediata es crítica.
        }

        return savedHuella;
    }

    /**
     * Elimina los datos de una huella de la caché en memoria.
     * Debe ser llamado DESPUÉS de que la huella se haya eliminado de la BD.
     *
     * @param huellaId El ID de la Huella que fue eliminada.
     */
    public void removeFmdDataFromMemory(Integer huellaId) {
        boolean removed = fmdDataList.removeIf(data -> data.huellaId.equals(huellaId));
        if (removed) {
            logger.info("FMD para huella ID {} eliminado de la memoria.", huellaId);
        } else {
            logger.warn("No se encontró FMD en memoria para la huella ID {} al intentar eliminar.", huellaId);
            // Podría ser útil recargar todo si esto pasa inesperadamente: loadFmdsIntoMemory();
        }
    }

    // Ya no necesitamos los métodos CRUD de User (createUser, getUser, etc.) aquí.
}