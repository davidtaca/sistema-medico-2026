package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de la bitácora de inventario: cada entrada o salida de stock de un
 * medicamento en una sucursal, con el stock anterior y el nuevo para poder
 * auditarlo. El despacho de medicamentos (CU-11) crea un movimiento de tipo
 * DESPACHO por cada medicamento entregado.
 */
@Entity
@Table(name = "movimientos_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimientoInventario tipo;

    @ManyToOne
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "stock_anterior", nullable = false)
    private int stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private int stockNuevo;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    /** Referencia del documento que originó el movimiento, por ejemplo "DESP-000012". */
    private String referencia;

    @Column(length = 500)
    private String notas;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

    public MovimientoInventario() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TipoMovimientoInventario getTipo() { return tipo; }
    public void setTipo(TipoMovimientoInventario tipo) { this.tipo = tipo; }
    public Medicamento getMedicamento() { return medicamento; }
    public void setMedicamento(Medicamento medicamento) { this.medicamento = medicamento; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public int getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(int stockAnterior) { this.stockAnterior = stockAnterior; }
    public int getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(int stockNuevo) { this.stockNuevo = stockNuevo; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
