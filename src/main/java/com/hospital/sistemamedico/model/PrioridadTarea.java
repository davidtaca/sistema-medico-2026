package com.hospital.sistemamedico.model;

/**
 * Prioridad de una tarea del médico (CU-14, RN-CU14-02): Baja (0), Normal (1) o Alta (2).
 */
public enum PrioridadTarea {
    BAJA("Baja"),
    NORMAL("Normal"),
    ALTA("Alta");

    private final String etiqueta;

    PrioridadTarea(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
