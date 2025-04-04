// src/main/java/com/example/fingerprint_api/model/RhEstatus.java
package com.example.fingerprint_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rh_estatus")
public class RhEstatus {

    // Asumiendo que 'id' es la clave primaria en rh_estatus según tu consulta
    @Id
    @Column(name = "id")
    private Integer id;

    // Mapear también la clave textual si la usas/necesitas
    // @Column(name = "clave")
    // private String clave;

    @Column(name = "nombre")
    private String nombre;

    // Getters (y Setters si son necesarios)
    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}