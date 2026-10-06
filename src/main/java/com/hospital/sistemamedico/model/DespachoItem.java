package com.hospital.sistemamedico.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Un medicamento entregado dentro de un despacho (CU-11). Si el medicamento
 * recetado no estaba disponible, el personal de farmacia pudo sustituirlo
 * (FA02): en ese caso "medicamentoEntregado" difiere de "medicamentoRecetado"
 * y se guarda la razón de la sustitución.
 */
@Entity
@Table(name = "despacho_items")
public class DespachoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @ManyToOne
    @JoinColumn(name = "medicamento_recetado_id", nullable = false)
    private Medicamento medicamentoRecetado;

    @ManyToOne
    @JoinColumn(name = "medicamento_entregado_id", nullable = false)
    private Medicamento medicamentoEntregado;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private boolean sustituido = false;

    @Column(name = "razon_sustitucion", length = 500)
    private String razonSustitucion;

    public DespachoItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Despacho getDespacho() { return despacho; }
    public void setDespacho(Despacho despacho) { this.despacho = despacho; }
    public Medicamento getMedicamentoRecetado() { return medicamentoRecetado; }
    public void setMedicamentoRecetado(Medicamento medicamentoRecetado) { this.medicamentoRecetado = medicamentoRecetado; }
    public Medicamento getMedicamentoEntregado() { return medicamentoEntregado; }
    public void setMedicamentoEntregado(Medicamento medicamentoEntregado) { this.medicamentoEntregado = medicamentoEntregado; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public boolean isSustituido() { return sustituido; }
    public void setSustituido(boolean sustituido) { this.sustituido = sustituido; }
    public String getRazonSustitucion() { return razonSustitucion; }
    public void setRazonSustitucion(String razonSustitucion) { this.razonSustitucion = razonSustitucion; }
}
