package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad que representa una cita médica agendada por un paciente. Es la
 * entidad central del sistema: casi todos los casos de uso (CU-03 a CU-08)
 * giran alrededor del ciclo de vida de una Cita, representado por el campo
 * "estado" (ver EstadoCita).
 */
@Entity
@Table(name = "citas")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paciente que agenda la cita (debe tener rol PACIENTE). */
    @ManyToOne @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    /** Médico asignado a la cita (debe tener rol MEDICO). Puede cambiar mediante reasignación, CU-05. */
    @ManyToOne @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "motivo_consulta")
    private String motivoConsulta;

    /** Estado actual de la cita dentro de su ciclo de vida (ver EstadoCita). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCita estado = EstadoCita.PENDIENTE_PAGO;

    /** true si la cita tiene prioridad de emergencia (marcada al agendar o detectada en signos vitales). */
    private boolean emergencia = false;

    /** Nombre del archivo PDF adjuntado al agendar la cita (CU-03, opcional). Null si no se adjuntó ningún documento. */
    @Column(name = "documento_adjunto")
    private String documentoAdjunto;

    /**
     * true si la cita fue agendada directamente por el paciente desde el portal;
     * false si fue creada por personal interno (cita "walk-in" desde recepción, CU-05).
     * Se usa para decidir si aplica la cancelación automática por falta de pago (CU-06):
     * las citas walk-in NO se cancelan automáticamente.
     */
    @Column(name = "agendada_por_paciente", nullable = false)
    private boolean agendadaPorPaciente = true;

    /** Fecha y hora en que se creó la cita. Se usa junto con agendadaPorPaciente para calcular el vencimiento de los 10 minutos de pago (CU-06). */
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Cita() {}

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getPaciente() { return paciente; }
    public void setPaciente(Usuario paciente) { this.paciente = paciente; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public String getMotivoConsulta() { return motivoConsulta; }
    public void setMotivoConsulta(String motivoConsulta) { this.motivoConsulta = motivoConsulta; }
    public EstadoCita getEstado() { return estado; }
    public void setEstado(EstadoCita estado) { this.estado = estado; }
    public boolean isEmergencia() { return emergencia; }
    public void setEmergencia(boolean emergencia) { this.emergencia = emergencia; }
    public String getDocumentoAdjunto() { return documentoAdjunto; }
    public void setDocumentoAdjunto(String documentoAdjunto) { this.documentoAdjunto = documentoAdjunto; }
    public boolean isAgendadaPorPaciente() { return agendadaPorPaciente; }
    public void setAgendadaPorPaciente(boolean agendadaPorPaciente) { this.agendadaPorPaciente = agendadaPorPaciente; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
