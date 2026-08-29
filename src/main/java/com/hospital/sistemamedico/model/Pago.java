package com.hospital.sistemamedico.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa el pago de una consulta médica. Tiene una relación
 * uno a uno con Cita: cada cita puede tener a lo sumo un pago (validado en
 * PagoService antes de guardar). Cubre tanto el pago en línea con tarjeta
 * (CU-04) como el cobro presencial en caja (CU-06).
 */
@Entity
@Table(name = "pagos")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cita a la que corresponde este pago (relación uno a uno). */
    @OneToOne @JoinColumn(name = "cita_id", nullable = false, unique = true)
    private Cita cita;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    /**
     * Referencia única de la transacción. En pagos en línea (CU-04) se genera
     * automáticamente con formato "TXN-XXXXXXXXXXXX"; en pagos de caja (CU-06)
     * contiene el método y los últimos 4 dígitos de la tarjeta, o un
     * identificador generado si fue en efectivo.
     */
    @Column(name = "numero_transaccion")
    private String numeroTransaccion;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago = LocalDateTime.now();

    public Pago() {}

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public String getNumeroTransaccion() { return numeroTransaccion; }
    public void setNumeroTransaccion(String numeroTransaccion) { this.numeroTransaccion = numeroTransaccion; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }
}