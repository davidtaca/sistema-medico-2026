package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Catálogo de exámenes de laboratorio que el médico puede solicitar en una
 * orden de laboratorio (CU-08, FA01). Cada examen tiene un monto (se suma al
 * total de la orden que se cobra en caja) y, opcionalmente, la unidad de
 * medida y el rango de referencia que el laboratorio ve al registrar el
 * resultado (CU-09).
 */
@Entity
@Table(name = "examenes")
public class Examen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    /** Monto en quetzales. */
    private BigDecimal precio;

    /** Unidad de medida habitual del resultado, por ejemplo "mg/dL" (opcional). */
    private String unidad;

    /** Rango de referencia normal, por ejemplo "70 - 100" (opcional). */
    @Column(name = "rango_referencia")
    private String rangoReferencia;

    public Examen() {}

    public Examen(String nombre, BigDecimal precio, String unidad, String rangoReferencia) {
        this.nombre = nombre;
        this.precio = precio;
        this.unidad = unidad;
        this.rangoReferencia = rangoReferencia;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public String getRangoReferencia() { return rangoReferencia; }
    public void setRangoReferencia(String rangoReferencia) { this.rangoReferencia = rangoReferencia; }
}
