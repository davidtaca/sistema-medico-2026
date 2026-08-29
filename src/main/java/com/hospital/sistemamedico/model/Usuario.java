package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad que representa a CUALQUIER usuario del sistema: tanto personal
 * interno (médicos, enfermeras, cajeros, recepcionistas, laboratoristas,
 * farmacéuticos, administradores) como pacientes. Se usa una sola tabla
 * "usuarios" con el campo "rol" para distinguirlos, en vez de tablas
 * separadas, porque ambos tipos comparten casi todos los mismos campos
 * (nombre, correo, username, contraseña, DPI, teléfono).
 *
 * Los campos "sucursal" y "especialidad" solo se llenan cuando el rol es
 * MEDICO; para el resto de roles (incluyendo PACIENTE) quedan en null.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String dpi;

    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    private String nit;

    @Column(name = "numero_seguro")
    private String numeroSeguro;

    /** Solo aplica si rol = MEDICO: sucursal donde atiende. */
    @ManyToOne
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    /** Solo aplica si rol = MEDICO: especialidad que ejerce. */
    @ManyToOne
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    @Column(nullable = false)
    private boolean activo = true;

    /** Contador de intentos de login fallidos consecutivos (CU-00, RN-CU00-02). Se reinicia al iniciar sesión con éxito. */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    /** Fecha y hora hasta la cual la cuenta queda bloqueada tras exceder el máximo de intentos fallidos (CU-00, RN-CU00-03). Null si la cuenta no está bloqueada. */
    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    public Usuario() {}

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNumeroSeguro() { return numeroSeguro; }
    public void setNumeroSeguro(String numeroSeguro) { this.numeroSeguro = numeroSeguro; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
}
