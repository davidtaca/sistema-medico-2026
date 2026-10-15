package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tarea o recordatorio del médico, que se gestiona en el panel lateral de la
 * agenda (CU-14, RN-CU14-02). Tiene prioridad, fecha límite obligatoria y un
 * indicador de si ya fue completada.
 */
@Entity
@Table(name = "tareas_agenda")
public class TareaAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadTarea prioridad;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    /** false = Pendiente (por defecto), true = Completada. */
    @Column(nullable = false)
    private boolean completada = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public TareaAgenda() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public PrioridadTarea getPrioridad() { return prioridad; }
    public void setPrioridad(PrioridadTarea prioridad) { this.prioridad = prioridad; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
