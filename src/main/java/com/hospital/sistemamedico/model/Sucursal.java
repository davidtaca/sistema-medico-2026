package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Entidad que representa una sucursal (sede física) del hospital. Cada
 * médico atiende en exactamente una sucursal, y cada cita se agenda en
 * una sucursal específica. Los datos de contacto (teléfono, dirección y
 * descripción) se administran en el mantenimiento de catálogos (CU-15).
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

    /** Teléfono de 8 dígitos (opcional). */
    @Column(length = 8)
    private String telefono;

    @Column(length = 500)
    private String direccion;

    @Column(length = 250)
    private String descripcion;

    public Sucursal() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
