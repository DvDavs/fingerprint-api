package com.example.fingerprint_api.dto;

// Puedes usar Lombok @Data o @Getter/@Setter
public class EmpleadoUpdateDto {
    private String rfc;
    private String curp;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private Integer departamentoAcademicoId;
    private Integer departamentoAdministrativoId;
    private String tipoNombramientoPrincipal; // Usar String o Enum si defines Enums aquí también
    private String tipoNombramientoSecundario;

    // Getters y Setters
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
}