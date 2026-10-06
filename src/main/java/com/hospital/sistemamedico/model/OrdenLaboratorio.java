package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de laboratorio generada por el médico durante o después de la
 * consulta (CU-08, FA01). El paciente la paga en caja (CU-10) y el laboratorio
 * toma las muestras y registra los resultados (CU-09).
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

    /** Exámenes de la orden, con su monto y su resultado. */
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrdenExamen> items = new ArrayList<>();

    @Column(length = 2000)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrdenLab estado = EstadoOrdenLab.PENDIENTE;

    /**
     * true si el médico marcó la orden como externa: el paciente se hace los
     * exámenes en otro laboratorio, así que no se cobra ni se procesa aquí (CU-09, FA01).
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean externa = false;

    /** Suma de los montos de los exámenes al generarse la orden. */
    @Column(name = "monto_total")
    private BigDecimal montoTotal = BigDecimal.ZERO;

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
    public List<OrdenExamen> getItems() { return items; }
    public void setItems(List<OrdenExamen> items) { this.items = items; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public EstadoOrdenLab getEstado() { return estado; }
    public void setEstado(EstadoOrdenLab estado) { this.estado = estado; }
    public boolean isExterna() { return externa; }
    public void setExterna(boolean externa) { this.externa = externa; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
