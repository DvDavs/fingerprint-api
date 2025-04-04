package com.example.fingerprint_api.dto;

import com.example.fingerprint_api.model.Huella; // Importar si necesitas un factory method
import java.time.LocalDateTime;

// Puedes usar Lombok @Data o @Getter/@Setter
public class HuellaDto {
    private Integer id;
    private Integer empleadoId;
    private String nombreDedo;
    private String uuid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor, Getters, Setters...

    // Factory method útil para convertir Entidad a DTO
    public static HuellaDto fromEntity(Huella huella) {
        if (huella == null) return null;
        HuellaDto dto = new HuellaDto();
        dto.setId(huella.getId());
        dto.setEmpleadoId(huella.getEmpleado() != null ? huella.getEmpleado().getId() : null);
        dto.setNombreDedo(huella.getNombreDedo());
        dto.setUuid(huella.getUuid());
        dto.setCreatedAt(huella.getCreatedAt());
        dto.setUpdatedAt(huella.getUpdatedAt());
        return dto;
    }
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }
    public String getNombreDedo() { return nombreDedo; }
    public void setNombreDedo(String nombreDedo) { this.nombreDedo = nombreDedo; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}