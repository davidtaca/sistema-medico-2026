package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad que representa el registro de signos vitales de un paciente para
 * una cita específica, tomado por personal de enfermería (CU-07). Tiene una
 * relación uno a uno con Cita: cada cita puede tener a lo sumo un registro
 * de signos vitales.
 */
@Entity
@Table(name = "signos_vitales")
public class SignosVitales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cita a la que corresponde este registro (relación uno a uno). */
    @OneToOne
    @JoinColumn(name = "cita_id", nullable = false, unique = true)
    private Cita cita;

    /** Usuario con rol ENFERMERO que realizó la toma de signos vitales. */
    @ManyToOne
    @JoinColumn(name = "enfermero_id", nullable = false)
    private Usuario enfermero;

    /** Presión arterial sistólica en mmHg (rango válido: 60-250). */
    @Column(name = "presion_sistolica", nullable = false)
    private Integer presionSistolica;

    /** Presión arterial diastólica en mmHg (rango válido: 40-150). */
    @Column(name = "presion_diastolica", nullable = false)
    private Integer presionDiastolica;

    /** Temperatura corporal en grados Celsius (rango válido: 34-42). */
    @Column(nullable = false)
    private Double temperatura;

    /** Peso corporal en kilogramos (rango válido: 0.5-300). */
    @Column(nullable = false)
    private Double peso;

    /** Talla/estatura en centímetros (rango válido: 30-250). */
    @Column(nullable = false)
    private Double talla;

    /** Frecuencia cardíaca en latidos por minuto (rango válido: 30-220). */
    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Integer frecuenciaCardiaca;

    /** true si la enfermera marcó el caso como prioridad de emergencia. */
    @Column(nullable = false)
    private boolean emergencia = false;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public SignosVitales() {}

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public Usuario getEnfermero() { return enfermero; }
    public void setEnfermero(Usuario enfermero) { this.enfermero = enfermero; }
    public Integer getPresionSistolica() { return presionSistolica; }
    public void setPresionSistolica(Integer presionSistolica) { this.presionSistolica = presionSistolica; }
    public Integer getPresionDiastolica() { return presionDiastolica; }
    public void setPresionDiastolica(Integer presionDiastolica) { this.presionDiastolica = presionDiastolica; }
    public Double getTemperatura() { return temperatura; }
    public void setTemperatura(Double temperatura) { this.temperatura = temperatura; }
    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Integer getFrecuenciaCardiaca() { return frecuenciaCardiaca; }
    public void setFrecuenciaCardiaca(Integer frecuenciaCardiaca) { this.frecuenciaCardiaca = frecuenciaCardiaca; }
    public boolean isEmergencia() { return emergencia; }
    public void setEmergencia(boolean emergencia) { this.emergencia = emergencia; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}