package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Entidad que representa una sucursal (sede física) del hospital. Cada
 * médico atiende en exactamente una sucursal, y cada cita se agenda en
 * una sucursal específica.
 */
@Entity
@Table(name = "sucursales")
public class Sucursal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    /** false = sucursal dada de baja (eliminación lógica), no aparece en los formularios. */
    @Column(nullable = false)
    private boolean activo = true;

    public Sucursal() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}