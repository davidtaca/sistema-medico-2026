package com.hospital.sistemamedico.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un examen dentro de una orden de laboratorio (CU-08 FA01 / CU-09). Guarda el
 * monto que tenía el examen al generarse la orden y, cuando el laboratorio lo
 * procesa, su resultado. Un resultado ya publicado no puede modificarse
 * (RNF-024).
 */
@Entity
@Table(name = "orden_examenes")
public class OrdenExamen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenLaboratorio orden;

    @ManyToOne
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    /** Monto del examen al momento de generar la orden. */
    @Column(nullable = false)
    private BigDecimal precio;

    @Column(name = "valor_resultado", length = 500)
    private String valorResultado;

    @Column(name = "unidad_resultado", length = 50)
    private String unidadResultado;

    @Column(name = "fecha_resultado")
    private LocalDate fechaResultado;

    /** Marcado manualmente por el laboratorio cuando el valor está fuera del rango de referencia. */
    @Column(name = "fuera_de_rango", nullable = false)
    private boolean fueraDeRango = false;

    @Column(name = "notas_resultado", length = 1000)
    private String notasResultado;

    /** true cuando el resultado ya fue publicado (isPublished). */
    @Column(nullable = false)
    private boolean publicado = false;

    @Column(name = "fecha_publicacion")
    private LocalDateTime fechaPublicacion;

    /** Personal de laboratorio que registró el resultado. */
    @ManyToOne
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    public OrdenExamen() {}

    public boolean tieneResultado() { return valorResultado != null && !valorResultado.isBlank(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrdenLaboratorio getOrden() { return orden; }
    public void setOrden(OrdenLaboratorio orden) { this.orden = orden; }
    public Examen getExamen() { return examen; }
    public void setExamen(Examen examen) { this.examen = examen; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getValorResultado() { return valorResultado; }
    public void setValorResultado(String valorResultado) { this.valorResultado = valorResultado; }
    public String getUnidadResultado() { return unidadResultado; }
    public void setUnidadResultado(String unidadResultado) { this.unidadResultado = unidadResultado; }
    public LocalDate getFechaResultado() { return fechaResultado; }
    public void setFechaResultado(LocalDate fechaResultado) { this.fechaResultado = fechaResultado; }
    public boolean isFueraDeRango() { return fueraDeRango; }
    public void setFueraDeRango(boolean fueraDeRango) { this.fueraDeRango = fueraDeRango; }
    public String getNotasResultado() { return notasResultado; }
    public void setNotasResultado(String notasResultado) { this.notasResultado = notasResultado; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDateTime fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }
    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }
}
