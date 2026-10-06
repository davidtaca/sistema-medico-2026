package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Despacho de los medicamentos de una receta en la farmacia (CU-11). Guarda
 * el detalle de lo entregado y el monto total; el cobro físico se hace fuera
 * del sistema. También sirve para dejar constancia de que el paciente no
 * adquirió los medicamentos (estado NO_ADQUIRIDO, FA03).
 */
@Entity
@Table(name = "despachos")
public class Despacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    /** Sucursal cuyo inventario se utilizó. */
    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    /** Personal de farmacia que realizó el despacho. */
    @ManyToOne
    @JoinColumn(name = "farmaceutico_id", nullable = false)
    private Usuario farmaceutico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDespacho estado = EstadoDespacho.REGISTRADO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DespachoItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public Despacho() {}

    /** Número de despacho mostrado al usuario, por ejemplo "DESP-000007". */
    @Transient
    public String getNumeroDespacho() {
        return id == null ? null : String.format("DESP-%06d", id);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Receta getReceta() { return receta; }
    public void setReceta(Receta receta) { this.receta = receta; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Usuario getFarmaceutico() { return farmaceutico; }
    public void setFarmaceutico(Usuario farmaceutico) { this.farmaceutico = farmaceutico; }
    public EstadoDespacho getEstado() { return estado; }
    public void setEstado(EstadoDespacho estado) { this.estado = estado; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<DespachoItem> getItems() { return items; }
    public void setItems(List<DespachoItem> items) { this.items = items; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
