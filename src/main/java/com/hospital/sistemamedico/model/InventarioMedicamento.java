package com.hospital.sistemamedico.model;

import jakarta.persistence.*;

/**
 * Existencias de un medicamento en una sucursal (inventario de farmacia).
 * Hay a lo sumo un registro por combinación medicamento-sucursal. Usa control
 * de concurrencia optimista (@Version) para que dos despachos simultáneos no
 * descuenten el mismo stock (RNF-025).
 */
@Entity
@Table(name = "inventario_medicamentos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"medicamento_id", "sucursal_id"}))
public class InventarioMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "stock_actual", nullable = false)
    private int stockActual;

    @Version
    private Long version;

    public InventarioMedicamento() {}

    public InventarioMedicamento(Medicamento medicamento, Sucursal sucursal, int stockActual) {
        this.medicamento = medicamento;
        this.sucursal = sucursal;
        this.stockActual = stockActual;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Medicamento getMedicamento() { return medicamento; }
    public void setMedicamento(Medicamento medicamento) { this.medicamento = medicamento; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public int getStockActual() { return stockActual; }
    public void setStockActual(int stockActual) { this.stockActual = stockActual; }
    public Long getVersion() { return version; }
}
