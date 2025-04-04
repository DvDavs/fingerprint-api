package com.example.fingerprint_api.repository;

import com.example.fingerprint_api.model.Huella;
import com.example.fingerprint_api.model.Empleado; // Necesario si usas el método findByEmpleado
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HuellaRepository extends JpaRepository<Huella, Integer> {
    // Métodos CRUD básicos heredados

    // Buscar todas las huellas de un empleado por su ID
    List<Huella> findByEmpleadoId(Integer empleadoId);

    // Alternativa si prefieres pasar el objeto Empleado
    // List<Huella> findByEmpleado(Empleado empleado);

    // Contar huellas por empleado
    long countByEmpleadoId(Integer empleadoId);

    // Borrar una huella específica (útil para la API)
    void deleteByIdAndEmpleadoId(Integer huellaId, Integer empleadoId);

    // Podrías necesitar buscar por dedo específico de un empleado
    // Optional<Huella> findByEmpleadoIdAndNombreDedo(Integer empleadoId, String nombreDedo);
}