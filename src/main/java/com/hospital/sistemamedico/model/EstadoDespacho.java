package com.hospital.sistemamedico.model;

/**
 * Resultado de un despacho de medicamentos (CU-11):
 *
 * REGISTRADO   → se entregaron los medicamentos y se descontó el inventario
 * NO_ADQUIRIDO → el paciente decidió no comprar los medicamentos (FA03); no mueve inventario
 */
public enum EstadoDespacho {
    REGISTRADO,
    NO_ADQUIRIDO
}
