package com.hospital.sistemamedico.model;

/**
 * Formas de pago aceptadas para una consulta médica. En el frontend,
 * el pago en línea (CU-04) siempre usa TARJETA con validación completa,
 * mientras que en caja (CU-06) el cajero puede elegir cualquiera de las tres,
 * distinguiendo Visa/Mastercard/Débito solo a nivel visual (todas se guardan
 * internamente como TARJETA).
 */
public enum MetodoPago {
    TARJETA,
    EFECTIVO,
    TRANSFERENCIA
}