package com.hospital.sistemamedico.model;

/**
 * Estados de una orden de laboratorio (CU-09):
 *
 * PENDIENTE  → generada por el médico (CU-08), esperando el pago en caja
 * EN_PROCESO → el pago ya se registró (CU-10); se puede tomar muestras y registrar resultados
 * COMPLETADA → todos los exámenes de la orden tienen su resultado publicado
 */
public enum EstadoOrdenLab {
    PENDIENTE("Pendiente"),
    EN_PROCESO("En proceso"),
    COMPLETADA("Completada");

    private final String etiqueta;

    EstadoOrdenLab(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
