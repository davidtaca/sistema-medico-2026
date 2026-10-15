package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.MetodoPago;
import com.hospital.sistemamedico.model.Pago;
import com.hospital.sistemamedico.service.EmailService;
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

    @Autowired
    private EmailService emailService;

    /**
     * Registra el pago de una cita (en línea con tarjeta, CU-04, o presencial en caja, CU-06).
     * Si la petición trae la cabecera "Idempotency-Key" y ya existe un pago con esa clave, se
     * devuelve ese mismo pago (200) sin cobrar ni enviar correos otra vez (RNF-016).
     */
    @PostMapping
    public ResponseEntity<?> registrarPago(@RequestBody Map<String, Object> datos,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            Long citaId = Long.valueOf(datos.get("citaId").toString());
            boolean yaExistia = idempotencyKey != null && !idempotencyKey.isBlank()
                    && pagoYaRegistrado(idempotencyKey);

            Pago pago = pagoService.registrarPago(
                    citaId,
                    new BigDecimal(datos.get("monto").toString()),
                    MetodoPago.valueOf(datos.get("metodoPago").toString()),
                    datos.get("numeroTransaccion") != null ? datos.get("numeroTransaccion").toString() : null,
                    datos.get("numeroTarjeta") != null ? datos.get("numeroTarjeta").toString() : null,
                    datos.get("nombreTitular") != null ? datos.get("nombreTitular").toString() : null,
                    datos.get("fechaVencimiento") != null ? datos.get("fechaVencimiento").toString() : null,
                    datos.get("cvv") != null ? datos.get("cvv").toString() : null,
                    datos.get("formaPago") != null ? datos.get("formaPago").toString() : null,
                    idempotencyKey,
                    datos.get("cajeroId") != null ? Long.valueOf(datos.get("cajeroId").toString()) : null
            );

            if (yaExistia) {
                return ResponseEntity.ok(pago);
            }

            var cita = pago.getCita();
            // Comprobante de pago (RN-CU04-05) y confirmación de la cita (RN-CU00-02)
            emailService.enviarComprobantePago(
                    cita.getPaciente().getCorreo(),
                    cita.getPaciente().getNombreCompleto(),
                    pago.getNumeroTransaccion(),
                    pago.getMonto().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    nombreFormaPago(pago.getFormaPago()),
                    pago.getFechaPago(),
                    cita.getMedico().getNombreCompleto(),
                    cita.getEspecialidad().getNombre(),
                    cita.getSucursal().getNombre(),
                    cita.getFechaHora()
            );
            emailService.enviarConfirmacionCita(
                    cita.getPaciente().getCorreo(),
                    cita.getPaciente().getNombreCompleto(),
                    cita.getEspecialidad().getNombre(),
                    cita.getMedico().getNombreCompleto(),
                    cita.getFechaHora(),
                    cita.getSucursal().getNombre()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(pago);
        } catch (IllegalArgumentException | NullPointerException e) {
            String mensaje = e instanceof NullPointerException ? "Faltan datos obligatorios del pago." : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", mensaje));
        }
    }

    private boolean pagoYaRegistrado(String idempotencyKey) {
        try {
            return pagoService.buscarPorIdempotencyKey(idempotencyKey.trim()) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String nombreFormaPago(String forma) {
        if (forma == null) {
            return "Tarjeta";
        }
        return switch (forma) {
            case "EFECTIVO" -> "Efectivo";
            case "VISA" -> "Tarjeta Visa";
            case "MASTERCARD" -> "Tarjeta Mastercard";
            case "DEBITO" -> "Tarjeta de débito";
            default -> "Tarjeta";
        };
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
