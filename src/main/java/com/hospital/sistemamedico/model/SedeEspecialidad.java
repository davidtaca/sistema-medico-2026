package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Asignación de una especialidad médica a una sede/sucursal (CU-13). Determina
 * qué especialidades puede elegir el paciente al agendar una cita en cada sede
 * (CU-03). No puede existir la misma combinación sede-especialidad más de una
 * vez (índice único).
 */
@Entity
@Table(name = "sede_especialidades",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sucursal_id", "especialidad_id"}))
public class SedeEspecialidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    /** Se asigna como activa automáticamente al crear la asignación. */
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public SedeEspecialidad() {}

    public SedeEspecialidad(Sucursal sucursal, Especialidad especialidad) {
        this.sucursal = sucursal;
        this.especialidad = especialidad;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
