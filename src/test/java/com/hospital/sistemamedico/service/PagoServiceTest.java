package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del pago de citas (CU-04 y CU-06): validación de la tarjeta, respuestas
 * simuladas de la pasarela, sesión de pago expirada, idempotencia y forma de pago
 * guardada. No necesitan base de datos.
 */
class PagoServiceTest {

    private PagoService servicio;
    private PagoRepository pagos;
    private CitaService citas;
    private Cita cita;

    @BeforeEach
    void preparar() {
        servicio = new PagoService();
        pagos = mock(PagoRepository.class);
        citas = mock(CitaService.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "pagoRepository", pagos);
        ReflectionTestUtils.setField(servicio, "citaService", citas);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarios);

        cita = new Cita();
        cita.setId(7L);
        cita.setEstado(EstadoCita.PENDIENTE_PAGO);
        when(citas.buscarPorId(7L)).thenReturn(cita);
        when(pagos.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));
        Usuario cajero = new Usuario();
        cajero.setId(3L);
        when(usuarios.buscarPorId(3L)).thenReturn(cajero);
    }

    private Pago enLinea(String tarjeta, String clave) {
        return servicio.registrarPago(7L, new BigDecimal("150"), MetodoPago.TARJETA, null, tarjeta, "MARIA LOPEZ PEREZ", "12/39", "123",
                null, clave, null);
    }

    private String errorEnLinea(String tarjeta) {
        return assertThrows(IllegalArgumentException.class, () -> enLinea(tarjeta, null)).getMessage();
    }

    @Test
    void unaTarjetaValidaEsAprobadaYQuedaConfirmadaLaCita() {
        Pago p = enLinea("4242424242424242", null);
        assertEquals("TARJETA", p.getFormaPago());
        assertTrue(p.getNumeroTransaccion().startsWith("TXN-"));
        verify(citas).confirmarCita(7L);
    }

    @Test
    void laPasarelaRechazaConLosMensajesDeLosDocumentos() {
        assertEquals("La transacción con tarjeta fue rechazada por el banco. Por favor, verifique los datos de su tarjeta o intente con una tarjeta diferente.", errorEnLinea("4000000000000002"));
        assertEquals("Su tarjeta fue rechazada por fondos insuficientes. Verifique su saldo e intente nuevamente.", errorEnLinea("4000000000009995"));
        assertEquals("Su tarjeta fue rechazada. El número de tarjeta es inválido. Verifique los datos e intente nuevamente.", errorEnLinea("4000000000000127"));
        assertEquals("Su tarjeta fue rechazada. La tarjeta está vencida. Utilice otra tarjeta de crédito o débito.", errorEnLinea("4000000000000069"));
        assertEquals("El pago no pudo ser procesado. Por favor, intente nuevamente o utilice otra tarjeta.", errorEnLinea("4000000000000119"));
        assertEquals("Error de comunicación con la pasarela de pago. Intente nuevamente en unos minutos.", errorEnLinea("4000000000000101"));
        // un pago rechazado no confirma la cita ni guarda nada
        verify(citas, never()).confirmarCita(any());
        verify(pagos, never()).save(any());
    }

    @Test
    void laTarjetaSeValidaAntesDeConsultarALaPasarela() {
        assertEquals("El número de tarjeta debe contener entre 13 y 19 dígitos y ser válido.", errorEnLinea("4242424242424241"));
        assertEquals("El número de tarjeta debe contener entre 13 y 19 dígitos y ser válido.", errorEnLinea("1234"));
    }

    @Test
    void siLaReservaVencioElPagoEnLineaDiceQueLaSesionExpiro() {
        cita.setEstado(EstadoCita.CANCELADA);
        assertEquals("Su sesión de pago ha expirado. Por favor, inicie el proceso de pago nuevamente.", errorEnLinea("4242424242424242"));

        // en caja el mensaje es el de una cita cancelada
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.registrarPago(7L, new BigDecimal("150"),
                MetodoPago.EFECTIVO, "EFECTIVO-1", null, null, null, null));
        assertEquals("No se puede pagar una cita cancelada.", e.getMessage());
    }

    @Test
    void repetirLaMismaOperacionNoCobraDosVeces() {
        Pago previo = new Pago();
        previo.setNumeroTransaccion("TXN-PREVIO");
        when(pagos.findByIdempotencyKey("clave-1")).thenReturn(Optional.of(previo));

        Pago p = enLinea("4242424242424242", "clave-1");

        assertSame(previo, p);
        verify(pagos, never()).save(any());
        verify(citas, never()).confirmarCita(any());
    }

    @Test
    void unaCitaConPagoNoSePuedePagarDeNuevoConOtraClave() {
        when(pagos.existsByCitaId(7L)).thenReturn(true);
        assertEquals("Esta cita ya tiene un pago registrado.", errorEnLinea("4242424242424242"));
    }

    @Test
    void elCobroEnCajaGuardaLaFormaDePagoRealYElCajero() {
        Pago p = servicio.registrarPago(7L, new BigDecimal("150"), MetodoPago.TARJETA, "VISA-****4242", null, null, null, null,
                "visa", null, 3L);
        assertEquals("VISA", p.getFormaPago());
        assertEquals(3L, p.getRegistradoPor().getId());

        Pago efectivo = servicio.registrarPago(7L, new BigDecimal("150"), MetodoPago.EFECTIVO, "EFECTIVO-1", null, null, null, null,
                "EFECTIVO", null, 3L);
        assertEquals("EFECTIVO", efectivo.getFormaPago());
    }

    @Test
    void sinFormaDePagoSeUsaElMetodo() {
        Pago p = servicio.registrarPago(7L, new BigDecimal("150"), MetodoPago.EFECTIVO, "EFECTIVO-1", null, null, null, null);
        assertEquals("EFECTIVO", p.getFormaPago());
    }
}
