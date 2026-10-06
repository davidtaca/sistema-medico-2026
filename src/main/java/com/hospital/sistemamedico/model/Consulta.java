package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro clínico de la consulta médica de una cita (CU-08): motivo,
 * hallazgos, diagnóstico y plan de tratamiento. Tiene relación uno a uno con
 * Cita. Mientras "finalizada" sea false la consulta está "En curso" y el
 * médico puede seguir editándola; al finalizarla queda en el historial clínico
 * del paciente. Cada guardado incrementa "version" y deja una copia en
 * ConsultaHistorial (RNF-026).
 */
@Entity
@Table(name = "consultas")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "cita_id", nullable = false, unique = true)
    private Cita cita;

    /** Médico que atendió la consulta. */
    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "motivo_visita", length = 2000)
    private String motivoVisita;

    @Column(name = "hallazgos_clinicos", length = 5000)
    private String hallazgosClinicos;

    /** Código CIE-10 del diagnóstico (opcional, RN-CU08-01). */
    @Column(name = "codigo_cie10", length = 10)
    private String codigoCie10;

    @Column(length = 5000)
    private String diagnostico;

    @Column(name = "plan_tratamiento", length = 5000)
    private String planTratamiento;

    @Column(name = "notas_adicionales", length = 5000)
    private String notasAdicionales;

    /** false = "En curso", true = "Finalizada". */
    @Column(nullable = false)
    private boolean finalizada = false;

    /** Número de versión del registro; aumenta con cada guardado (RNF-026). */
    @Column(nullable = false)
    private int version = 1;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    public Consulta() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public String getMotivoVisita() { return motivoVisita; }
    public void setMotivoVisita(String motivoVisita) { this.motivoVisita = motivoVisita; }
    public String getHallazgosClinicos() { return hallazgosClinicos; }
    public void setHallazgosClinicos(String hallazgosClinicos) { this.hallazgosClinicos = hallazgosClinicos; }
    public String getCodigoCie10() { return codigoCie10; }
    public void setCodigoCie10(String codigoCie10) { this.codigoCie10 = codigoCie10; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getPlanTratamiento() { return planTratamiento; }
    public void setPlanTratamiento(String planTratamiento) { this.planTratamiento = planTratamiento; }
    public String getNotasAdicionales() { return notasAdicionales; }
    public void setNotasAdicionales(String notasAdicionales) { this.notasAdicionales = notasAdicionales; }
    public boolean isFinalizada() { return finalizada; }
    public void setFinalizada(boolean finalizada) { this.finalizada = finalizada; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public LocalDateTime getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(LocalDateTime fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
}
