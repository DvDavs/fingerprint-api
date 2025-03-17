package com.example.fingerprint_api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    // Se almacenará la plantilla de huella en formato binario (FMD) en la BD
    @Lob
    private byte[] fingerprintTemplate;

    // Constructores
    public User() {
    }

    public User(String name, String email, byte[] fingerprintTemplate) {
        this.name = name;
        this.email = email;
        this.fingerprintTemplate = fingerprintTemplate;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public byte[] getFingerprintTemplate() {
        return fingerprintTemplate;
    }

    public void setFingerprintTemplate(byte[] fingerprintTemplate) {
        this.fingerprintTemplate = fingerprintTemplate;
    }
}
