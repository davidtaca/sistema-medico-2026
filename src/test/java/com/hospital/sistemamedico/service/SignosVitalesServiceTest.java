package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.SignosVitalesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la toma de signos vitales (CU-07): rangos y decimales de captura
 * con los mensajes de las reglas, campos obligatorios, alertas clínicas y
 * transición de la cita. No necesitan base de datos.
 */
class SignosVitalesServiceTest {

    private SignosVitalesService servicio;
    private SignosVitalesRepository repositorio;
    private CitaService citaService;
    private Cita cita;

    @BeforeEach
    void preparar() {
        servicio = new SignosVitalesService();
        repositorio = mock(SignosVitalesRepository.class);
        citaService = mock(CitaService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "signosVitalesRepository", repositorio);
        ReflectionTestUtils.setField(servicio, "citaService", citaService);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarioService);

        cita = new Cita();
        cita.setId(5L);
        cita.setEstado(EstadoCita.SIGNOS_VITALES);
        Usuario enfermero = new Usuario();
        enfermero.setId(9L);
        enfermero.setRol(Rol.ENFERMERO);
        when(citaService.buscarPorId(5L)).thenReturn(cita);
        when(usuarioService.buscarPorId(9L)).thenReturn(enfermero);
        when(repositorio.save(any(SignosVitales.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SignosVitales registrar(Integer sis, Integer dia, Double temp, Double peso, Double talla, Integer fc) {
        return servicio.registrar(5L, 9L, sis, dia, temp, peso, talla, fc, false);
    }

    private String error(Integer sis, Integer dia, Double temp, Double peso, Double talla, Integer fc) {
        return assertThrows(IllegalArgumentException.class, () -> registrar(sis, dia, temp, peso, talla, fc)).getMessage();
    }

    @Test
    void registraValoresNormalesYDevuelveLaCitaALaSalaDeEspera() {
        SignosVitales sv = registrar(120, 80, 36.8, 70.55, 170.5, 75);
        assertEquals("", sv.getAlertas());
        verify(citaService).completarSignosVitales(5L, false);
    }

    @Test
    void laPresionArterialTieneRangoYMensajeDeLaRegla() {
        String m = "La presión arterial debe ingresarse en formato sistólica/diastólica (ej: 120/80) dentro de rangos válidos.";
        assertEquals(m, error(null, 80, 36.8, 70.0, 170.0, 75));
        assertEquals(m, error(120, null, 36.8, 70.0, 170.0, 75));
        assertEquals(m, error(59, 80, 36.8, 70.0, 170.0, 75));
        assertEquals(m, error(251, 80, 36.8, 70.0, 170.0, 75));
        assertEquals(m, error(120, 39, 36.8, 70.0, 170.0, 75));
        assertEquals(m, error(120, 151, 36.8, 70.0, 170.0, 75));
        assertDoesNotThrow(() -> registrar(60, 40, 36.8, 70.0, 170.0, 75));
        assertDoesNotThrow(() -> registrar(250, 150, 36.8, 70.0, 170.0, 75));
    }

    @Test
    void laTemperaturaTieneRangoYUnDecimal() {
        String m = "La temperatura debe estar entre 34.0 y 42.0°C con un decimal.";
        assertEquals(m, error(120, 80, null, 70.0, 170.0, 75));
        assertEquals(m, error(120, 80, 33.9, 70.0, 170.0, 75));
        assertEquals(m, error(120, 80, 42.1, 70.0, 170.0, 75));
        assertEquals(m, error(120, 80, 36.55, 70.0, 170.0, 75));
        assertDoesNotThrow(() -> registrar(120, 80, 36.5, 70.0, 170.0, 75));
        assertDoesNotThrow(() -> registrar(120, 80, 37.0, 70.0, 170.0, 75));
    }

    @Test
    void elPesoYLaTallaTienenRangoYDosDecimales() {
        String peso = "El peso debe estar entre 0.5 y 300 kg con dos decimales.";
        assertEquals(peso, error(120, 80, 36.8, null, 170.0, 75));
        assertEquals(peso, error(120, 80, 36.8, 0.4, 170.0, 75));
        assertEquals(peso, error(120, 80, 36.8, 300.5, 170.0, 75));
        assertEquals(peso, error(120, 80, 36.8, 70.123, 170.0, 75));
        String talla = "La talla debe estar entre 30 y 250 cm con dos decimales.";
        assertEquals(talla, error(120, 80, 36.8, 70.0, null, 75));
        assertEquals(talla, error(120, 80, 36.8, 70.0, 29.9, 75));
        assertEquals(talla, error(120, 80, 36.8, 70.0, 250.5, 75));
        assertEquals(talla, error(120, 80, 36.8, 70.0, 170.123, 75));
        assertDoesNotThrow(() -> registrar(120, 80, 36.8, 0.5, 30.0, 75));
    }

    @Test
    void laFrecuenciaCardiacaTieneRangoYMensajeDeLaRegla() {
        String m = "La frecuencia cardíaca debe estar entre 30 y 220 latidos por minuto.";
        assertEquals(m, error(120, 80, 36.8, 70.0, 170.0, null));
        assertEquals(m, error(120, 80, 36.8, 70.0, 170.0, 29));
        assertEquals(m, error(120, 80, 36.8, 70.0, 170.0, 221));
    }

    @Test
    void siHayErroresNoSeGuardaNiSeCambiaLaCita() {
        error(120, 80, 45.0, 70.0, 170.0, 75);
        verify(repositorio, never()).save(any());
        verify(citaService, never()).completarSignosVitales(any(), anyBoolean());
    }

    @Test
    void lasAlertasClinicasSeGuardanEnElExpediente() {
        // PA fuera de 90/60-140/90, temperatura fuera de 36.0-37.5 y FC fuera de 60-100
        SignosVitales sv = registrar(150, 95, 38.5, 70.0, 170.0, 110);
        assertEquals("Presión arterial fuera de rango normal. | Temperatura fuera de rango normal. | Frecuencia cardíaca fuera de rango normal.", sv.getAlertas());

        assertEquals("Presión arterial fuera de rango normal.", registrar(85, 70, 36.5, 70.0, 170.0, 80).getAlertas());
        assertEquals("Temperatura fuera de rango normal.", registrar(120, 80, 35.9, 70.0, 170.0, 80).getAlertas());
    }

    @Test
    void losLimitesDelRangoNormalNoGeneranAlerta() {
        assertTrue(servicio.alertasClinicas(90, 60, 36.0, 60).isEmpty());
        assertTrue(servicio.alertasClinicas(140, 90, 37.5, 100).isEmpty());
        assertEquals(List.of("Presión arterial fuera de rango normal."), servicio.alertasClinicas(141, 80, 36.5, 80));
        assertEquals(List.of("Presión arterial fuera de rango normal."), servicio.alertasClinicas(120, 91, 36.5, 80));
        assertEquals(List.of("Frecuencia cardíaca fuera de rango normal."), servicio.alertasClinicas(120, 80, 36.5, 59));
    }

    @Test
    void laEmergenciaSePropagaALaCita() {
        servicio.registrar(5L, 9L, 120, 80, 36.8, 70.0, 170.0, 75, true);
        verify(citaService).completarSignosVitales(5L, true);
    }

    @Test
    void soloSePuedenRegistrarSignosAUnaCitaEnTomaDeSignosVitales() {
        cita.setEstado(EstadoCita.PACIENTE_PRESENTE);
        assertEquals("La cita no está en proceso de toma de signos vitales.", error(120, 80, 36.8, 70.0, 170.0, 75));
    }
}
