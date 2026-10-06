package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de auditoría: deja constancia de quién hizo qué y cuándo sobre la
 * configuración del sistema (por ejemplo, asignar o eliminar una especialidad
 * de una sede, CU-13). Los registros nunca se modifican ni se borran.
 */
@Entity
@Table(name = "bitacora_auditoria")
public class BitacoraAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Acción realizada, por ejemplo "ASIGNAR_ESPECIALIDAD_SEDE". */
    @Column(nullable = false)
    private String accion;

    /** Tipo de elemento afectado, por ejemplo "SedeEspecialidad". */
    @Column(nullable = false)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(length = 1000)
    private String detalle;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public BitacoraAuditoria() {}

    public BitacoraAuditoria(String accion, String entidad, Long entidadId, String detalle, Usuario usuario) {
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle;
        this.usuario = usuario;
    }

    public Long getId() { return id; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public Long getEntidadId() { return entidadId; }
    public String getDetalle() { return detalle; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
}
