package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Catálogo informativo de los roles de usuario (CU-15): nombre y descripción
 * de cada rol. Los permisos reales de cada rol siguen definidos por el enum
 * Rol; este catálogo solo documenta los roles para el administrador.
 */
@Entity
@Table(name = "catalogo_roles")
public class RolCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    public RolCatalogo() {}

    public RolCatalogo(String nombre, String descripcion) {
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
