package com.hospital.sistemamedico.model;

/**
 * Prioridad que el médico asigna a una cita de seguimiento al confirmarla (CU-12).
 */
public enum PrioridadSeguimiento {
    ALTA("Alta"),
    MEDIA("Media"),
    BAJA("Baja");

    private final String etiqueta;

    PrioridadSeguimiento(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
