package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Catálogo de medicamentos que el médico puede recetar (CU-08, FA04) y que
 * farmacia despacha (CU-11). Cada medicamento tiene un precio unitario y,
 * opcionalmente, el stock mínimo a partir del cual se alerta para reabastecer
 * (RN-CU10-03).
 */
@Entity
@Table(name = "medicamentos")
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    /** Precio unitario en quetzales. */
    private BigDecimal precio;

    /** Nivel mínimo de stock por sucursal; null = sin alerta configurada. */
    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    public Medicamento() {}

    public Medicamento(String nombre, BigDecimal precio, Integer stockMinimo) {
        this.nombre = nombre;
        this.precio = precio;
        this.stockMinimo = stockMinimo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }
}
