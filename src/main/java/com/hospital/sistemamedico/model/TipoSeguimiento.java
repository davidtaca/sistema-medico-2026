package com.hospital.sistemamedico.model;

/**
 * Tipo de una cita de seguimiento agendada por el médico (CU-12, RN-CU11-01).
 */
public enum TipoSeguimiento {
    MONITOREO_TRATAMIENTO("Monitoreo de Tratamiento"),
    REVISION_RESULTADOS_LABORATORIO("Revisión de Resultados de Laboratorio");

    private final String etiqueta;

    TipoSeguimiento(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
