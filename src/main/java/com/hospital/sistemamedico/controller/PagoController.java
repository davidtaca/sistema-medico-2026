package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.MetodoPago;
import com.hospital.sistemamedico.model.Pago;
import com.hospital.sistemamedico.service.PagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    @Autowired
    private PagoService pagoService;

    @PostMapping
    public ResponseEntity<?> registrarPago(@RequestBody Map<String, Object> datos) {
        try {
            Pago pago = pagoService.registrarPago(
                    Long.valueOf(datos.get("citaId").toString()),
                    new BigDecimal(datos.get("monto").toString()),
                    MetodoPago.valueOf(datos.get("metodoPago").toString()),
                    datos.get("numeroTransaccion") != null ? datos.get("numeroTransaccion").toString() : null
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(pago);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cita/{citaId}")
    public ResponseEntity<?> buscarPorCita(@PathVariable Long citaId) {
        try {
            return ResponseEntity.ok(pagoService.buscarPorCita(citaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(pagoService.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}