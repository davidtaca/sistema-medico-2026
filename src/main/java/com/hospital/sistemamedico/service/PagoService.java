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

/**
 * Servicio con la lógica de negocio de los pagos de citas médicas.
 * Cubre tanto el pago en línea con tarjeta (CU-04) como el cobro presencial
 * en caja, en efectivo o tarjeta (CU-06). Un mismo método registrarPago
 * atiende ambos flujos, distinguiendo el caso según si viene o no el número
 * completo de la tarjeta.
 */
@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private CitaService citaService;

    /**
     * Valida un número de tarjeta usando el algoritmo de Luhn (el mismo
     * algoritmo que usan los bancos y pasarelas de pago reales para detectar
     * errores de digitación en el número de tarjeta, aunque no reemplaza una
     * validación bancaria real).
     *
     * @param numero número de tarjeta compuesto únicamente por dígitos
     * @return true si el número pasa la validación de Luhn (dígito de control correcto)
     */
    private boolean esNumeroTarjetaValido(String numero) {
        int suma = 0;
        boolean alternar = false;
        // Se recorre el número de derecha a izquierda, duplicando cada segundo dígito
        for (int i = numero.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(numero.charAt(i));
            if (alternar) {
                digito *= 2;
                if (digito > 9) digito -= 9; // si el doble supera 9, se restan 9 (equivale a sumar sus dígitos)
            }
            suma += digito;
            alternar = !alternar;
        }
        return suma % 10 == 0;
    }

    /**
     * Registra el pago de una cita, ya sea en línea con tarjeta (CU-04) o
     * presencial en caja (CU-06). Actualiza automáticamente el estado de la
     * cita a CONFIRMADA al finalizar.
     *
     * Si se recibe un número de tarjeta completo (numeroTarjeta != null),
     * se asume que es un pago en línea y se validan a fondo todos los datos
     * de la tarjeta (número con Luhn, titular, vencimiento, CVV), generando
     * internamente un número de transacción único. Si no se recibe número de
     * tarjeta completo pero el método es TARJETA, se asume que es un cobro
     * presencial en caja, donde solo se exige el número de transacción
     * (que en ese flujo contiene los últimos 4 dígitos como referencia).
     *
     * @param citaId id de la cita a pagar
     * @param monto monto a cobrar
     * @param metodoPago EFECTIVO, TARJETA o TRANSFERENCIA
     * @param numeroTransaccion referencia de transacción (usada tal cual en pagos de caja;
     *        se sobreescribe con un número generado automáticamente en pagos en línea)
     * @param numeroTarjeta número completo de la tarjeta (solo en pago en línea, CU-04); null en pagos de caja
     * @param nombreTitular nombre del titular de la tarjeta (solo pago en línea)
     * @param fechaVencimiento fecha de vencimiento en formato MM/AA (solo pago en línea)
     * @param cvv código de seguridad de la tarjeta, nunca se guarda (solo pago en línea)
     * @return el Pago registrado
     * @throws IllegalArgumentException si la cita está cancelada, ya tiene un pago
     *         registrado, o algún dato de la tarjeta no es válido
     */
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
            // El número de transacción de un pago en línea siempre se genera internamente,
            // nunca lo escribe el usuario
            numeroTransaccion = "TXN-" + java.util.UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }

        if (metodoPago == MetodoPago.TARJETA && numeroTarjeta == null
                && (numeroTransaccion == null || numeroTransaccion.isBlank())) {
            // Pago presencial en caja (CU-06): solo exige el número de transacción
            // (que en ese flujo ya viene armado con los últimos 4 dígitos de la tarjeta)
            throw new IllegalArgumentException("Se requiere número de transacción para pagos con tarjeta.");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setNumeroTransaccion(numeroTransaccion);
        pago.setFechaPago(LocalDateTime.now());

        Pago pagoGuardado = pagoRepository.save(pago);

        // Al completarse el pago, la cita pasa automáticamente a estado Confirmada
        citaService.confirmarCita(citaId);

        return pagoGuardado;
    }

    /**
     * Busca el pago asociado a una cita específica.
     *
     * @param citaId id de la cita
     * @return el Pago encontrado
     * @throws IllegalArgumentException si esa cita no tiene ningún pago registrado
     */
    public Pago buscarPorCita(Long citaId) {
        return pagoRepository.findByCitaId(citaId)
                .orElseThrow(() -> new IllegalArgumentException("No hay pago registrado para esta cita."));
    }

    /**
     * Busca un pago por su propio id. Usado en la pantalla de confirmación
     * de pago (confirmacion-pago.html) para mostrar el comprobante.
     *
     * @param id id del pago
     * @return el Pago encontrado
     * @throws IllegalArgumentException si no existe ningún pago con ese id
     */
    public Pago buscarPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
    }
}