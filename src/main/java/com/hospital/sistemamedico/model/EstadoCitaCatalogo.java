package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Catálogo informativo de los estados de una cita (CU-15): nombre y
 * descripción con los que se presentan al usuario. El flujo de la cita sigue
 * dependiendo del enum EstadoCita; este catálogo solo documenta cada estado.
 */
@Entity
@Table(name = "catalogo_estados_cita")
public class EstadoCitaCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    public EstadoCitaCatalogo() {}

    public EstadoCitaCatalogo(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
