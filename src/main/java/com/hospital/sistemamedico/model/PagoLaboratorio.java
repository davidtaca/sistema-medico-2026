package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago de una orden de laboratorio registrado por el cajero (CU-10). Tiene
 * relación uno a uno con OrdenLaboratorio: cada orden se cobra una sola vez.
 * Al registrarse, la orden pasa a "En proceso" y el laboratorio puede tomar
 * las muestras (CU-09).
 */
@Entity
@Table(name = "pagos_laboratorio")
public class PagoLaboratorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "orden_id", nullable = false, unique = true)
    private OrdenLaboratorio orden;

    /** Cajero que realizó el cobro. */
    @ManyToOne
    @JoinColumn(name = "cajero_id", nullable = false)
    private Usuario cajero;

    @Column(nullable = false)
    private BigDecimal monto;

    /** Forma de pago elegida en caja: EFECTIVO, VISA, MASTERCARD o DEBITO. */
    @Column(name = "forma_pago", nullable = false)
    private String formaPago;

    /** Número de transacción único del comprobante (RN-GLOBAL-005). */
    @Column(name = "numero_transaccion", nullable = false, unique = true)
    private String numeroTransaccion;

    /** Últimos 4 dígitos de la tarjeta; null si el pago fue en efectivo. */
    @Column(name = "ultimos_digitos", length = 4)
    private String ultimosDigitos;

    /** Monto recibido en efectivo; null si el pago fue con tarjeta. */
    @Column(name = "monto_recibido")
    private BigDecimal montoRecibido;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago = LocalDateTime.now();

    public PagoLaboratorio() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrdenLaboratorio getOrden() { return orden; }
    public void setOrden(OrdenLaboratorio orden) { this.orden = orden; }
    public Usuario getCajero() { return cajero; }
    public void setCajero(Usuario cajero) { this.cajero = cajero; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getFormaPago() { return formaPago; }
    public void setFormaPago(String formaPago) { this.formaPago = formaPago; }
    public String getNumeroTransaccion() { return numeroTransaccion; }
    public void setNumeroTransaccion(String numeroTransaccion) { this.numeroTransaccion = numeroTransaccion; }
    public String getUltimosDigitos() { return ultimosDigitos; }
    public void setUltimosDigitos(String ultimosDigitos) { this.ultimosDigitos = ultimosDigitos; }
    public BigDecimal getMontoRecibido() { return montoRecibido; }
    public void setMontoRecibido(BigDecimal montoRecibido) { this.montoRecibido = montoRecibido; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }
}
