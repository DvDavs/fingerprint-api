package com.example.fingerprint_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rh_personal")
public class Empleado {

    @Id
    @Column(name = "id")
    // Si tu ID no es autoincremental en la BD y lo gestionas tú (ej. desde rh_personal), quita @GeneratedValue
     @GeneratedValue(strategy = GenerationType.IDENTITY) // Asegúrate si tu ID es autoincremental o no.
    private Integer id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "rfc", length = 14)
    private String rfc;

    @Column(name = "curp", length = 21)
    private String curp;

    @Column(name = "primer_nombre", length = 100)
    private String primerNombre;

    @Column(name = "segundo_nombre", length = 100)
    private String segundoNombre;

    @Column(name = "primer_apellido", length = 100)
    private String primerApellido;

    @Column(name = "segundo_apellido", length = 100)
    private String segundoApellido;

    @Column(name = "departamento_academico") // FK a rh_departamentos
    private Integer departamentoAcademicoId;

    @Column(name = "departamento_administrativo") // FK a rh_departamentos
    private Integer departamentoAdministrativoId;

    @Column(name = "tipo_nombramiento_principal", length = 20)
    private String tipoNombramientoPrincipal;

    @Column(name = "tipo_nombramiento_secundario", length = 20)
    private String tipoNombramientoSecundario;

    @Column(name = "correo_institucional", length = 50)
    private String correoInstitucional;

    @Column(name = "estatus") // Columna numérica (ID)
    private Integer estatusId;

    // *** MAPEADO DIRECTO PARA estatus_nombre ***
    @Column(name = "estatus_nombre", length = 150) // Verifica la longitud real en tu BD
    private String estatusNombre;

    // Timestamps (gestionados por la BD o JPA)
    @Column(name = "createdAt", updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime updatedAt;

    // Constructores
    public Empleado() {}

    // Getters y Setters (Generados o con Lombok)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getRfc() { return rfc; }
    public void setRfc(String rfc) { this.rfc = rfc; }
    public String getCurp() { return curp; }
    public void setCurp(String curp) { this.curp = curp; }
    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }
    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String segundoNombre) { this.segundoNombre = segundoNombre; }
    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }
    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String segundoApellido) { this.segundoApellido = segundoApellido; }
    public Integer getDepartamentoAcademicoId() { return departamentoAcademicoId; }
    public void setDepartamentoAcademicoId(Integer departamentoAcademicoId) { this.departamentoAcademicoId = departamentoAcademicoId; }
    public Integer getDepartamentoAdministrativoId() { return departamentoAdministrativoId; }
    public void setDepartamentoAdministrativoId(Integer departamentoAdministrativoId) { this.departamentoAdministrativoId = departamentoAdministrativoId; }
    public String getTipoNombramientoPrincipal() { return tipoNombramientoPrincipal; }
    public void setTipoNombramientoPrincipal(String tipoNombramientoPrincipal) { this.tipoNombramientoPrincipal = tipoNombramientoPrincipal; }
    public String getTipoNombramientoSecundario() { return tipoNombramientoSecundario; }
    public void setTipoNombramientoSecundario(String tipoNombramientoSecundario) { this.tipoNombramientoSecundario = tipoNombramientoSecundario; }
    public String getCorreoInstitucional() { return correoInstitucional; }
    public void setCorreoInstitucional(String correoInstitucional) { this.correoInstitucional = correoInstitucional; }
    public Integer getEstatusId() { return estatusId; }
    public void setEstatusId(Integer estatusId) { this.estatusId = estatusId; }

    // *** Getter/Setter para estatusNombre ***
    public String getEstatusNombre() { return estatusNombre; }
    public void setEstatusNombre(String estatusNombre) { this.estatusNombre = estatusNombre; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // No setter para createdAt si es gestionado por JPA/DB
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // No setter para updatedAt si es gestionado por JPA/DB

    // Método útil para obtener nombre completo
    @Transient // No es una columna de la BD
    public String getNombreCompleto() {
        StringBuilder sb = new StringBuilder();
        if (primerNombre != null) sb.append(primerNombre).append(" ");
        if (segundoNombre != null) sb.append(segundoNombre).append(" ");
        if (primerApellido != null) sb.append(primerApellido).append(" ");
        if (segundoApellido != null) sb.append(segundoApellido);
        String fullName = sb.toString().trim();
        return fullName.isEmpty() ? "Nombre no disponible" : fullName;
    }

    // Métodos PrePersist y PreUpdate para manejar timestamps si no usas @CreationTimestamp/@UpdateTimestamp de Hibernate
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now(); // También al crear
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString(); // Generar UUID si es nulo
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}