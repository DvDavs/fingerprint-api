package com.example.fingerprint_api.repository;

import com.example.fingerprint_api.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {
    // Métodos CRUD básicos heredados

    // Métodos para validación de unicidad
    Optional<Empleado> findByRfc(String rfc);
    Optional<Empleado> findByCurp(String curp);
    Optional<Empleado> findByUuid(String uuid); // Útil si usas UUIDs
}