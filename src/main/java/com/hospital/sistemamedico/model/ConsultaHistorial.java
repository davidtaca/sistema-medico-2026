package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Copia inmutable de una versión de Consulta, guardada cada vez que el médico
 * guarda la consulta. Permite auditar los cambios del historial clínico
 * (RNF-026, versionamiento para auditoría).
 */
@Entity
@Table(name = "consultas_historial")
public class ConsultaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consulta_id", nullable = false)
    private Long consultaId;

    @Column(nullable = false)
    private int version;

    @Column(name = "medico_id", nullable = false)
    private Long medicoId;

    @Column(name = "motivo_visita", length = 2000)
    private String motivoVisita;

    @Column(name = "hallazgos_clinicos", length = 5000)
    private String hallazgosClinicos;

    @Column(name = "codigo_cie10", length = 10)
    private String codigoCie10;

    @Column(length = 5000)
    private String diagnostico;

    @Column(name = "plan_tratamiento", length = 5000)
    private String planTratamiento;

    @Column(name = "notas_adicionales", length = 5000)
    private String notasAdicionales;

    @Column(nullable = false)
    private boolean finalizada;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public ConsultaHistorial() {}

    /** Crea la copia de una consulta en su estado actual. */
    public ConsultaHistorial(Consulta c) {
        this.consultaId = c.getId();
        this.version = c.getVersion();
        this.medicoId = c.getMedico().getId();
        this.motivoVisita = c.getMotivoVisita();
        this.hallazgosClinicos = c.getHallazgosClinicos();
        this.codigoCie10 = c.getCodigoCie10();
        this.diagnostico = c.getDiagnostico();
        this.planTratamiento = c.getPlanTratamiento();
        this.notasAdicionales = c.getNotasAdicionales();
        this.finalizada = c.isFinalizada();
    }

    public Long getId() { return id; }
    public Long getConsultaId() { return consultaId; }
    public int getVersion() { return version; }
    public Long getMedicoId() { return medicoId; }
    public String getMotivoVisita() { return motivoVisita; }
    public String getHallazgosClinicos() { return hallazgosClinicos; }
    public String getCodigoCie10() { return codigoCie10; }
    public String getDiagnostico() { return diagnostico; }
    public String getPlanTratamiento() { return planTratamiento; }
    public String getNotasAdicionales() { return notasAdicionales; }
    public boolean isFinalizada() { return finalizada; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
