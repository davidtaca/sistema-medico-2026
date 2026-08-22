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

    private boolean esNumeroTarjetaValido(String numero) {
        int suma = 0;
        boolean alternar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(numero.charAt(i));
            if (alternar) {
                digito *= 2;
                if (digito > 9) digito -= 9;
            }
            suma += digito;
            alternar = !alternar;
        }
        return suma % 10 == 0;
    }

    public Pago registrarPago(Long citaId, BigDecimal monto, MetodoPago metodoPago, String numeroTransaccion,
                              String numeroTarjeta, String nombreTitular, String fechaVencimiento, String cvv) {
        Cita cita = citaService.buscarPorId(citaId);

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalArgumentException("No se puede pagar una cita cancelada.");
        }
        if (pagoRepository.existsByCitaId(citaId)) {
            throw new IllegalArgumentException("Esta cita ya tiene un pago registrado.");
        }

        if (metodoPago == MetodoPago.TARJETA && numeroTarjeta != null) {
            // Pago en línea (CU-04): validación completa de tarjeta
            if (!numeroTarjeta.matches("^\\d{13,19}$") || !esNumeroTarjetaValido(numeroTarjeta)) {
                throw new IllegalArgumentException("El número de tarjeta debe contener entre 13 y 19 dígitos y ser válido.");
            }
            if (nombreTitular == null || !nombreTitular.trim().matches("^[a-zA-ZÀ-ÿ\\s]{5,100}$")) {
                throw new IllegalArgumentException("El nombre del titular debe contener entre 5 y 100 caracteres alfabéticos sin especiales.");
            }
            if (fechaVencimiento == null || !fechaVencimiento.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
                throw new IllegalArgumentException("La fecha de vencimiento debe estar en formato MM/AA y la tarjeta no debe estar vencida.");
            }
            int mesVenc = Integer.parseInt(fechaVencimiento.substring(0, 2));
            int anioVenc = 2000 + Integer.parseInt(fechaVencimiento.substring(3, 5));
            java.time.YearMonth vencimiento = java.time.YearMonth.of(anioVenc, mesVenc);
            if (vencimiento.isBefore(java.time.YearMonth.now())) {
                throw new IllegalArgumentException("La fecha de vencimiento debe estar en formato MM/AA y la tarjeta no debe estar vencida.");
            }
            if (cvv == null || !cvv.matches("^\\d{3,4}$")) {
                throw new IllegalArgumentException("El CVV debe contener 3 ó 4 dígitos numéricos.");
            }
            numeroTransaccion = "TXN-" + java.util.UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }

        if (metodoPago == MetodoPago.TARJETA && numeroTarjeta == null
                && (numeroTransaccion == null || numeroTransaccion.isBlank())) {
            // Pago presencial en caja (CU-06): solo exige el número de transacción
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