package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Cita;
import com.hospital.sistemamedico.model.EstadoCita;
import com.hospital.sistemamedico.model.MetodoPago;
import com.hospital.sistemamedico.model.Pago;
import com.hospital.sistemamedico.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private CitaService citaService;

    public Pago registrarPago(Long citaId, BigDecimal monto, MetodoPago metodoPago, String numeroTransaccion) {
        Cita cita = citaService.buscarPorId(citaId);

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalArgumentException("No se puede pagar una cita cancelada.");
        }
        if (pagoRepository.existsByCitaId(citaId)) {
            throw new IllegalArgumentException("Esta cita ya tiene un pago registrado.");
        }
        if (metodoPago == MetodoPago.TARJETA && (numeroTransaccion == null || numeroTransaccion.isBlank())) {
            throw new IllegalArgumentException("Se requiere número de transacción para pagos con tarjeta.");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setNumeroTransaccion(numeroTransaccion);
        pago.setFechaPago(LocalDateTime.now());

        Pago pagoGuardado = pagoRepository.save(pago);

        citaService.confirmarCita(citaId);

        return pagoGuardado;
    }

    public Pago buscarPorCita(Long citaId) {
        return pagoRepository.findByCitaId(citaId)
                .orElseThrow(() -> new IllegalArgumentException("No hay pago registrado para esta cita."));
    }

    public Pago buscarPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
    }
}