package com.example.fingerprint_api.dto;

import jakarta.validation.constraints.Size;

// Puedes usar Lombok @Data o @Getter/@Setter
public class EmpleadoUpdateDto {

    // No se suele permitir cambiar RFC/CURP fácilmente, pero si lo haces, valida
    @Size(min = 10, max = 13, message = "RFC debe tener entre 10 y 13 caracteres")
    private String rfc;

    @Size(min = 18, max = 18, message = "CURP debe tener 18 caracteres")
    private String curp;

    @Size(max = 100, message = "Primer nombre no puede exceder 100 caracteres")
    private String primerNombre;

    @Size(max = 100, message = "Segundo nombre no puede exceder 100 caracteres")
    private String segundoNombre;

    @Size(max = 100, message = "Primer apellido no puede exceder 100 caracteres")
    private String primerApellido;

    @Size(max = 100, message = "Segundo apellido no puede exceder 100 caracteres")
    private String segundoApellido;

    // IDs (validados en servicio si existen o no)
    private Integer departamentoAcademicoId;
    private Integer departamentoAdministrativoId;

    @Size(max = 20, message = "Nombramiento principal no puede exceder 20 caracteres")
    private String tipoNombramientoPrincipal;

    @Size(max = 20, message = "Nombramiento secundario no puede exceder 20 caracteres")
    private String tipoNombramientoSecundario;

    // --- Campos Añadidos (Opcional) ---
    // private Integer estatusId; // Si quieres poder cambiar el estatus

    // @Size(max = 50, message = "Correo no puede exceder 50 caracteres")
    // @Email(message = "Formato de correo inválido")
    // private String correoInstitucional; // Si quieres poder cambiar el correo

    // --- Getters y Setters ---
    // (Generados manualmente, con Lombok o Records)
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

    // public Integer getEstatusId() { return estatusId; }
    // public void setEstatusId(Integer estatusId) { this.estatusId = estatusId; }
    // public String getCorreoInstitucional() { return correoInstitucional; }
    // public void setCorreoInstitucional(String correoInstitucional) { this.correoInstitucional = correoInstitucional; }
}