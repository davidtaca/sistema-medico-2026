package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.RecetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de la vigencia de recetas del despacho de medicamentos (CU-11,
 * RN-CU10-01). No necesitan base de datos: los repositorios son simulados.
 */
class DespachoServiceTest {

    private DespachoService servicio;
    private RecetaRepository recetaRepository;
    private Usuario farmaceutico;

    @BeforeEach
    void preparar() {
        servicio = new DespachoService();
        recetaRepository = Mockito.mock(RecetaRepository.class);
        UsuarioService usuarioService = Mockito.mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "recetaRepository", recetaRepository);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarioService);

        farmaceutico = new Usuario();
        farmaceutico.setRol(Rol.FARMACEUTICO);
        Mockito.when(usuarioService.buscarPorId(1L)).thenReturn(farmaceutico);
    }

    private Receta recetaEmitidaHace(int dias) {
        Receta r = new Receta();
        r.setId(42L);
        r.setFecha(LocalDateTime.now().minusDays(dias));
        Mockito.when(recetaRepository.findById(42L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void recetaDeSieteDiasSigueVigente() {
        assertTrue(servicio.estaVigente(recetaEmitidaHace(7)));
    }

    @Test
    void recetaDeOchoDiasEstaVencida() {
        Receta r = recetaEmitidaHace(8);
        assertFalse(servicio.estaVigente(r));
        assertEquals(8, servicio.diasTranscurridos(r));
    }

    @Test
    void despacharRecetaVencidaMuestraElMensajeDelDocumento() {
        recetaEmitidaHace(9);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.detalle(42L, 1L));
        assertEquals("Receta Vencida. La receta #42 fue emitida hace 9 días y ya no es válida para despacho.", e.getMessage());

        assertThrows(IllegalArgumentException.class, () -> servicio.confirmar(42L, 1L, List.of()));
    }

    @Test
    void recetaYaDespachadaNoSePuedeDespacharDeNuevo() {
        Receta r = recetaEmitidaHace(1);
        r.setActiva(false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.detalle(42L, 1L));
        assertTrue(e.getMessage().contains("ya fue despachada"));
    }

    @Test
    void soloElPersonalDeFarmaciaPuedeDespachar() {
        farmaceutico.setRol(Rol.PACIENTE);
        recetaEmitidaHace(1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servicio.detalle(42L, 1L));
        assertEquals("El usuario indicado no es personal de farmacia.", e.getMessage());
    }
}
