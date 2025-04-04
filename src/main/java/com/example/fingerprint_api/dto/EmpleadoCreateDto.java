package com.example.fingerprint_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmpleadoCreateDto {

    @NotBlank(message = "RFC es requerido")
    @Size(min = 10, max = 13, message = "RFC debe tener entre 10 y 13 caracteres")
    private String rfc;

    @NotBlank(message = "CURP es requerido")
    @Size(min = 18, max = 18, message = "CURP debe tener 18 caracteres")
    private String curp;

    @NotBlank(message = "Primer nombre es requerido")
    private String primerNombre;

    private String segundoNombre; // Opcional

    @NotBlank(message = "Primer apellido es requerido")
    private String primerApellido;

    private String segundoApellido; // Opcional

    // IDs para relaciones (simplificado, asume que los IDs existen en tablas relacionadas)
    private Integer departamentoAcademicoId;
    private Integer departamentoAdministrativoId;

    // Nombramientos (como String por simplicidad)
    private String tipoNombramientoPrincipal; // Ej: "DOCENTE", "ADMINISTRATIVO"
    private String tipoNombramientoSecundario; // Ej: "BASE", "HONORARIOS"

    private Integer estatusId = 1; // Default a 1 (Activo) si no se especifica

    // --- Getters y Setters ---
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
    public Integer getEstatusId() { return estatusId; }
    public void setEstatusId(Integer estatusId) { this.estatusId = estatusId; }
}