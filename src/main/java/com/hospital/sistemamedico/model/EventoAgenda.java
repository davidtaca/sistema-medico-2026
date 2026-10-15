package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Evento personal en la agenda de un médico (CU-14): reunión, descanso,
 * capacitación, etc. Mientras dura, el médico no está disponible: el portal de
 * citas (CU-03) y los seguimientos (CU-12) no ofrecen esos horarios. Si es de
 * "todo el día", "inicio" es el comienzo del primer día y "fin" el final del
 * último día.
 */
@Entity
@Table(name = "eventos_agenda")
public class EventoAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    @Column(name = "todo_el_dia", nullable = false)
    private boolean todoElDia = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public EventoAgenda() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
    public TipoEvento getTipo() { return tipo; }
    public void setTipo(TipoEvento tipo) { this.tipo = tipo; }
    public boolean isTodoElDia() { return todoElDia; }
    public void setTodoElDia(boolean todoElDia) { this.todoElDia = todoElDia; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
