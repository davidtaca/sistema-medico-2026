package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Entidad que representa una especialidad médica (ej. Medicina General,
 * Pediatría). Cada médico ejerce exactamente una especialidad, y cada cita
 * se agenda para una especialidad específica.
 */
@Entity
@Table(name = "especialidades")
public class Especialidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    /** false = especialidad dada de baja (eliminación lógica), no aparece en los formularios. */
    @Column(nullable = false)
    private boolean activo = true;

    public Especialidad() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}