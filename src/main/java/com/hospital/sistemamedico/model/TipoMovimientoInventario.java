package com.hospital.sistemamedico.model;

/**
 * Tipos de movimiento de la bitácora de inventario de farmacia. El CU-15
 * (bitácora) permite registrar manualmente los seis primeros; DESPACHO lo
 * genera automáticamente el despacho de medicamentos (CU-11).
 */
public enum TipoMovimientoInventario {
    COMPRA,
    DEVOLUCION,
    VENTA,
    RECLAMO,
    AJUSTE_POSITIVO,
    AJUSTE_NEGATIVO,
    DESPACHO
}
