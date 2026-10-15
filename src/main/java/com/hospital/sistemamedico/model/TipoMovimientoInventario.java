package com.hospital.sistemamedico.model;

/**
 * Tipos de movimiento de la bitácora de inventario de farmacia (CU-16). El
 * número entre paréntesis es el código del documento de requerimientos. Los
 * seis primeros se registran manualmente; DESPACHO lo genera
 * automáticamente el despacho de medicamentos (CU-11) y no aparece en el
 * formulario. Venta, Reclamo, Ajuste- y Despacho son salidas de stock; el
 * resto son entradas.
 */
public enum TipoMovimientoInventario {
    COMPRA("Compra", false, true, "Factura"),                       // 0
    DEVOLUCION("Devolución", false, true, "Devolución"),            // 1
    VENTA("Venta", true, true, "Venta"),                            // 2
    RECLAMO("Reclamo", true, true, "Reclamo"),                      // 3
    AJUSTE_POSITIVO("Ajuste+", false, true, null),                  // 4
    AJUSTE_NEGATIVO("Ajuste-", true, true, null),                   // 5
    DESPACHO("Despacho", true, false, "Despacho");                  // 6

    private final String etiqueta;
    private final boolean salida;
    private final boolean manual;
    private final String tipoReferencia;

    TipoMovimientoInventario(String etiqueta, boolean salida, boolean manual, String tipoReferencia) {
        this.etiqueta = etiqueta;
        this.salida = salida;
        this.manual = manual;
        this.tipoReferencia = tipoReferencia;
    }

    public String getEtiqueta() { return etiqueta; }

    /** true si descuenta stock (Venta, Reclamo, Ajuste- y Despacho). */
    public boolean esSalida() { return salida; }

    /** true si se puede registrar a mano desde el formulario (todos menos Despacho). */
    public boolean esManual() { return manual; }

    /** Etiqueta del número de referencia según el tipo (Factura, Devolución, Venta, Reclamo); null si no aplica. */
    public String getTipoReferencia() { return tipoReferencia; }
}
