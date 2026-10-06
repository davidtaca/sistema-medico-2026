package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de laboratorio generada por el médico durante o después de la
 * consulta (CU-08, FA01). El paciente es derivado con ella al laboratorio,
 * donde se gestiona en el CU-09.
 */
@Entity
@Table(name = "ordenes_laboratorio")
public class OrdenLaboratorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToMany
    @JoinTable(name = "ordenes_laboratorio_examenes",
            joinColumns = @JoinColumn(name = "orden_id"),
            inverseJoinColumns = @JoinColumn(name = "examen_id"))
    private List<Examen> examenes = new ArrayList<>();

    @Column(length = 2000)
    private String observaciones;

    /** Estado de la orden; el CU-09 lo irá avanzando. Inicia en PENDIENTE. */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public OrdenLaboratorio() {}

    /** Número de orden mostrado al usuario, por ejemplo "OL-000012". */
    @Transient
    public String getNumeroOrden() {
        return id == null ? null : String.format("OL-%06d", id);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public List<Examen> getExamenes() { return examenes; }
    public void setExamenes(List<Examen> examenes) { this.examenes = examenes; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
