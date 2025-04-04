package com.example.fingerprint_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(name = "rh_personal_huellas")
public class Huella {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // Relación con Empleado (dueño de la huella)
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // optional=false -> NOT NULL
    @JoinColumn(name = "rh_personal_id", nullable = false)
    private Empleado empleado;

    @Column(name = "nombre_dedo", length = 50) // Permitimos NULL según tu DDL
    private String nombreDedo;

    @Lob // Para datos binarios largos
    @Column(name = "template_fmd", columnDefinition="LONGBLOB")
    private byte[] templateFmd;

    @Column(name = "uuid", length = 50)
    private String uuid;

    @CreationTimestamp // Gestionado por Hibernate/JPA
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp // Gestionado por Hibernate/JPA
    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    // Constructores
    public Huella() {}

    public Huella(Empleado empleado, String nombreDedo, byte[] templateFmd, String uuid) {
        this.empleado = empleado;
        this.nombreDedo = nombreDedo;
        this.templateFmd = templateFmd;
        this.uuid = uuid; // Podrías generarlo aquí si es null
    }

    // Getters y Setters (O usa Lombok: @Getter @Setter @NoArgsConstructor)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }
    public String getNombreDedo() { return nombreDedo; }
    public void setNombreDedo(String nombreDedo) { this.nombreDedo = nombreDedo; }
    public byte[] getTemplateFmd() { return templateFmd; }
    public void setTemplateFmd(byte[] templateFmd) { this.templateFmd = templateFmd; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    // No setter for createdAt
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // No setter for updatedAt
}