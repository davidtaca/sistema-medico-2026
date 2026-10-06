package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.OrdenLaboratorioRepository;
import com.hospital.sistemamedico.repository.PagoLaboratorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica del cobro de órdenes de laboratorio en caja (CU-10):
 * búsqueda de órdenes pendientes de pago por DPI o número de orden, registro
 * del pago en efectivo o con tarjeta y generación del comprobante. Al cobrar,
 * la orden pasa a "En proceso" (CU-09).
 */
@Service
public class PagoLaboratorioService {

    private static final Set<String> FORMAS_TARJETA = Set.of("VISA", "MASTERCARD", "DEBITO");

    @Autowired
    private PagoLaboratorioRepository pagoRepository;

    @Autowired
    private OrdenLaboratorioRepository ordenRepository;

    @Autowired
    private LaboratorioService laboratorioService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Busca las órdenes de laboratorio pendientes de pago (pasos 2 y 3). Las
     * órdenes externas no se cobran aquí, así que no aparecen.
     *
     * @param tipo "DPI" (DPI del paciente, 13 dígitos) u "ORDEN" (número de orden, por ejemplo "OL-000004" o "4")
     * @param valor valor a buscar
     * @return las órdenes pendientes que coinciden; vacía si no hay ninguna (FA01)
     * @throws IllegalArgumentException si falta el valor, el DPI no cumple RN-GLOBAL-001 o el número de orden no es válido
     */
    public List<OrdenLaboratorio> buscarPendientes(String tipo, String valor) {
        String v = valor == null ? "" : valor.trim();
        List<OrdenLaboratorio> pendientes = ordenRepository.findAllByOrderByFechaDesc().stream()
                .filter(o -> o.getEstado() == EstadoOrdenLab.PENDIENTE && !o.isExterna() && esCobrable(o))
                .collect(Collectors.toList());

        if ("ORDEN".equalsIgnoreCase(tipo)) {
            if (v.isEmpty()) {
                throw new IllegalArgumentException("Debe ingresar el número de orden para buscar.");
            }
            String digitos = v.toUpperCase().startsWith("OL-") ? v.substring(3) : v;
            if (!digitos.matches("\\d{1,9}")) {
                throw new IllegalArgumentException("El número de orden no es válido. Ejemplo: OL-000004 o 4.");
            }
            long numero = Long.parseLong(digitos);
            return pendientes.stream().filter(o -> o.getId() == numero).collect(Collectors.toList());
        }

        // RN-GLOBAL-001: validación del DPI
        if (v.isEmpty()) {
            throw new IllegalArgumentException("El campo DPI es obligatorio. Por favor, ingrese su número de DPI.");
        }
        if (!v.matches("\\d+")) {
            throw new IllegalArgumentException("El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.");
        }
        if (v.length() != 13) {
            throw new IllegalArgumentException("El DPI debe contener exactamente 13 dígitos. Usted ingresó " + v.length() + " dígitos.");
        }
        return pendientes.stream()
                .filter(o -> v.equals(o.getCita().getPaciente().getDpi()))
                .collect(Collectors.toList());
    }

    /**
     * Registra el cobro de una orden (pasos 6 a 9). Deja la orden "En proceso".
     *
     * @param ordenId id de la orden a cobrar
     * @param cajeroId id del usuario (CAJERO o ADMINISTRADOR) que cobra
     * @param formaPago EFECTIVO, VISA, MASTERCARD o DEBITO (RN-GLOBAL-004)
     * @param montoRecibido monto entregado por el paciente; obligatorio en efectivo
     * @param ultimosDigitos últimos 4 dígitos de la tarjeta; obligatorios con tarjeta (FA03)
     * @return el pago registrado
     * @throws IllegalArgumentException si la orden es externa, no está pendiente o ya fue cobrada,
     *         si el método no es válido, el efectivo es insuficiente o faltan los 4 dígitos de la tarjeta
     */
    @Transactional
    public PagoLaboratorio registrarPago(Long ordenId, Long cajeroId, String formaPago, BigDecimal montoRecibido,
                                         String ultimosDigitos) {
        Usuario cajero = usuarioService.buscarPorId(cajeroId);
        if (cajero.getRol() != Rol.CAJERO && cajero.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("El usuario indicado no es personal de caja.");
        }

        OrdenLaboratorio orden = laboratorioService.buscarPorId(ordenId);
        if (orden.isExterna()) {
            throw new IllegalArgumentException("Las órdenes externas no se cobran en esta clínica.");
        }
        if (!esCobrable(orden)) {
            throw new IllegalArgumentException("La orden no tiene exámenes con monto por cobrar.");
        }
        if (orden.getEstado() != EstadoOrdenLab.PENDIENTE || pagoRepository.existsByOrdenId(ordenId)) {
            throw new IllegalArgumentException("La orden no está pendiente de pago.");
        }

        String forma = formaPago == null ? "" : formaPago.trim().toUpperCase();
        boolean esEfectivo = forma.equals("EFECTIVO");
        if (!esEfectivo && !FORMAS_TARJETA.contains(forma)) {
            // RN-GLOBAL-004
            throw new IllegalArgumentException("El método de pago seleccionado no está disponible. Los métodos aceptados son: efectivo (Quetzales), tarjeta de crédito (Visa/Mastercard) o tarjeta de débito.");
        }

        BigDecimal total = orden.getMontoTotal();
        PagoLaboratorio pago = new PagoLaboratorio();
        if (esEfectivo) {
            if (montoRecibido == null || montoRecibido.compareTo(total) < 0) {
                BigDecimal recibido = montoRecibido == null ? BigDecimal.ZERO : montoRecibido;
                throw new IllegalArgumentException("El monto recibido (Q" + recibido.setScale(2, java.math.RoundingMode.HALF_UP)
                        + ") es menor al monto a cobrar (Q" + total.setScale(2, java.math.RoundingMode.HALF_UP) + ")");
            }
            pago.setMontoRecibido(montoRecibido);
        } else {
            if (ultimosDigitos == null || !ultimosDigitos.trim().matches("\\d{4}")) {
                throw new IllegalArgumentException("Ingrese los últimos 4 dígitos de la tarjeta.");
            }
            pago.setUltimosDigitos(ultimosDigitos.trim());
        }

        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMonto(total);
        pago.setFormaPago(forma);
        pago.setNumeroTransaccion("LAB-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        pago.setFechaPago(LocalDateTime.now());
        PagoLaboratorio guardado = pagoRepository.save(pago);

        laboratorioService.marcarEnProceso(ordenId);
        return guardado;
    }

    /**
     * Busca el pago de una orden, para reimprimir el comprobante sin límite (RNF-033).
     *
     * @throws IllegalArgumentException si la orden no tiene un pago registrado
     */
    public PagoLaboratorio buscarPorOrden(Long ordenId) {
        return pagoRepository.findByOrdenId(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No hay pago registrado para esta orden de laboratorio."));
    }

    /** Una orden se puede cobrar solo si tiene exámenes y un monto total mayor a cero. */
    private boolean esCobrable(OrdenLaboratorio o) {
        return !o.getItems().isEmpty() && o.getMontoTotal() != null && o.getMontoTotal().signum() > 0;
    }

    /** Resumen de una orden pendiente para la lista de resultados de la búsqueda (paso 3). */
    public Map<String, Object> resumenOrden(OrdenLaboratorio o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("numeroOrden", o.getNumeroOrden());
        m.put("paciente", o.getCita().getPaciente().getNombreCompleto());
        m.put("dpi", o.getCita().getPaciente().getDpi());
        m.put("cantidadExamenes", o.getItems().size());
        m.put("fecha", o.getFecha().toString());
        m.put("montoTotal", o.getMontoTotal());
        m.put("examenes", o.getItems().stream().map(i -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nombre", i.getExamen().getNombre());
            e.put("monto", i.getPrecio());
            return e;
        }).collect(Collectors.toList()));
        return m;
    }

    /** Comprobante de pago con los datos que exige RN-GLOBAL-005. */
    public Map<String, Object> comprobante(PagoLaboratorio p) {
        OrdenLaboratorio o = p.getOrden();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroTransaccion", p.getNumeroTransaccion());
        m.put("paciente", o.getCita().getPaciente().getNombreCompleto());
        m.put("dpi", o.getCita().getPaciente().getDpi());
        m.put("sucursal", "Laboratorio");
        m.put("numeroOrden", o.getNumeroOrden());
        m.put("monto", p.getMonto());
        m.put("formaPago", p.getFormaPago());
        m.put("ultimosDigitos", p.getUltimosDigitos());
        m.put("montoRecibido", p.getMontoRecibido());
        m.put("cambio", p.getMontoRecibido() == null ? null : p.getMontoRecibido().subtract(p.getMonto()));
        m.put("fechaPago", p.getFechaPago().toString());
        m.put("cajero", p.getCajero().getNombreCompleto());
        m.put("detalle", o.getItems().stream().map(i -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nombre", i.getExamen().getNombre());
            e.put("monto", i.getPrecio());
            return e;
        }).collect(Collectors.toList()));
        return m;
    }
}
