package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.PagoLaboratorio;
import com.hospital.sistemamedico.service.PagoLaboratorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints del cobro de laboratorio en caja (CU-10): búsqueda de órdenes
 * pendientes de pago, registro del pago y consulta del comprobante.
 */
@RestController
@RequestMapping("/api/pagos-laboratorio")
public class PagoLaboratorioController {

    @Autowired
    private PagoLaboratorioService pagoService;

    /** Pasos 2 y 3: órdenes pendientes de pago por DPI u orden. Si no hay ninguna, devuelve una lista vacía (FA01). */
    @GetMapping("/pendientes")
    public ResponseEntity<?> pendientes(@RequestParam String tipo, @RequestParam(required = false) String valor) {
        try {
            return ResponseEntity.ok(pagoService.buscarPendientes(tipo, valor).stream()
                    .map(pagoService::resumenOrden).collect(Collectors.toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Pasos 6 a 10: registra el pago y devuelve el comprobante. */
    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody Map<String, Object> datos) {
        try {
            if (datos.get("ordenId") == null || datos.get("cajeroId") == null) {
                throw new IllegalArgumentException("Debe indicar la orden y el cajero.");
            }
            BigDecimal recibido = null;
            if (datos.get("montoRecibido") != null && !datos.get("montoRecibido").toString().isBlank()) {
                try {
                    recibido = new BigDecimal(datos.get("montoRecibido").toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("El monto recibido no es válido.");
                }
            }
            PagoLaboratorio pago = pagoService.registrarPago(
                    Long.valueOf(datos.get("ordenId").toString()),
                    Long.valueOf(datos.get("cajeroId").toString()),
                    datos.get("formaPago") != null ? datos.get("formaPago").toString() : null,
                    recibido,
                    datos.get("ultimosDigitos") != null ? datos.get("ultimosDigitos").toString() : null);

            Map<String, Object> respuesta = new LinkedHashMap<>(pagoService.comprobante(pago));
            respuesta.put("mensaje", "¡Pago de laboratorio registrado exitosamente! Paciente: "
                    + pago.getOrden().getCita().getPaciente().getNombreCompleto()
                    + ". La orden ha sido actualizada a estado 'En proceso'.");
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Comprobante de una orden ya cobrada (reimpresión sin límite, RNF-033). */
    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<?> comprobante(@PathVariable Long ordenId) {
        try {
            return ResponseEntity.ok(pagoService.comprobante(pagoService.buscarPorOrden(ordenId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
