package com.example.fingerprint_api.dto;

import jakarta.validation.constraints.NotEmpty; // Para validaciones si usas spring-boot-starter-validation

public class AddHuellaRequestDto {

    @NotEmpty(message = "El nombre del dedo no puede estar vacío")
    private String nombreDedo;

    @NotEmpty(message = "El template de la huella (Base64) no puede estar vacío")
    private String templateBase64; // Recibimos el template como Base64 desde el front

    // Getters y Setters (O usa Lombok @Getter @Setter @NoArgsConstructor)
    public String getNombreDedo() {
        return nombreDedo;
    }

    public void setNombreDedo(String nombreDedo) {
        this.nombreDedo = nombreDedo;
    }

    public String getTemplateBase64() {
        return templateBase64;
    }

    public void setTemplateBase64(String templateBase64) {
        this.templateBase64 = templateBase64;
    }
}