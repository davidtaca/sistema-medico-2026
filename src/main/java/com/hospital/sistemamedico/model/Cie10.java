package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Catálogo de diagnósticos CIE-10 (Clasificación Internacional de
 * Enfermedades) usado por el autocompletado de la consulta médica (CU-08).
 */
@Entity
@Table(name = "cie10")
public class Cie10 {

    /** Código CIE-10, por ejemplo "J06.9". Es la llave primaria del catálogo. */
    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    public Cie10() {}

    public Cie10(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
