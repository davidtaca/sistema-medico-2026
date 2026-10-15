package com.hospital.sistemamedico.model;

/**
 * Tipos de evento de la agenda del médico (CU-14, RN-CU14-01). El número
 * entre paréntesis es el código que usa el documento de requerimientos.
 */
public enum TipoEvento {
    REUNION("Reunión"),          // 0
    DESCANSO("Descanso"),        // 1
    CAPACITACION("Capacitación"), // 2
    PERSONAL("Personal"),        // 3
    OTRO("Otro");                // 4

    private final String etiqueta;

    TipoEvento(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
