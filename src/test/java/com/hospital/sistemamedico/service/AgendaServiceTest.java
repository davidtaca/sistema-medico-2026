package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.EventoAgendaRepository;
import com.hospital.sistemamedico.repository.TareaAgendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la agenda médica (CU-14): validación de eventos y tareas,
 * eventos de todo el día, propiedad de la agenda y citas visibles en el
 * calendario. No necesitan base de datos: los repositorios son simulados.
 */
class AgendaServiceTest {

    private AgendaService servicio;
    private EventoAgendaRepository eventoRepository;
    private TareaAgendaRepository tareaRepository;
    private CitaRepository citaRepository;
    private Usuario medico;

    @BeforeEach
    void preparar() {
        servicio = new AgendaService();
        eventoRepository = mock(EventoAgendaRepository.class);
        tareaRepository = mock(TareaAgendaRepository.class);
        citaRepository = mock(CitaRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "eventoRepository", eventoRepository);
        ReflectionTestUtils.setField(servicio, "tareaRepository", tareaRepository);
        ReflectionTestUtils.setField(servicio, "citaRepository", citaRepository);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarioService);

        medico = new Usuario();
        medico.setId(3L);
        medico.setRol(Rol.MEDICO);
        Usuario paciente = new Usuario();
        paciente.setId(7L);
        when(usuarioService.buscarPorId(3L)).thenReturn(medico);
        when(usuarioService.buscarPorId(7L)).thenReturn(paciente);
        paciente.setRol(Rol.PACIENTE);
        when(eventoRepository.save(any(EventoAgenda.class))).thenAnswer(i -> {
            EventoAgenda e = i.getArgument(0);
            if (e.getId() == null) e.setId(50L);
            return e;
        });
        when(tareaRepository.save(any(TareaAgenda.class))).thenAnswer(i -> {
            TareaAgenda t = i.getArgument(0);
            if (t.getId() == null) t.setId(60L);
            return t;
        });
    }

    private Map<String, Object> evento(String... extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("titulo", "Capacitación en congreso médico");
        m.put("descripcion", "Congreso anual");
        m.put("inicio", "2026-11-10T09:00");
        m.put("fin", "2026-11-10T12:00");
        m.put("tipo", "CAPACITACION");
        for (int i = 0; i < extra.length; i += 2) {
            m.put(extra[i], extra[i + 1]);
        }
        return m;
    }

    private String errorEvento(Map<String, Object> datos) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.crearEvento(3L, datos)).getMessage();
    }

    @Test
    void creaElEventoConElMensajeDeExito() {
        Map<String, Object> r = servicio.crearEvento(3L, evento());
        assertEquals("Evento creado exitosamente. Capacitación en congreso médico — 10/11/2026 a 10/11/2026.", r.get("mensaje"));
        assertEquals("#8b5cf6", r.get("color"));
        assertEquals("Capacitación", r.get("tipoEtiqueta"));
    }

    @Test
    void lasFechasSonObligatoriasYElFinDebeSerPosterior() {
        assertEquals("La fecha de inicio es obligatoria.", errorEvento(evento("inicio", "")));
        assertEquals("La fecha de fin es obligatoria.", errorEvento(evento("fin", null)));
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorEvento(evento("fin", "2026-11-10T09:00")));
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorEvento(evento("fin", "2026-11-09T12:00")));
        assertEquals("Las fechas del evento no son válidas.", errorEvento(evento("inicio", "mañana")));
    }

    @Test
    void elTipoEsObligatorioYDebeSerUnoDeLaLista() {
        assertEquals("Debe seleccionar el tipo de evento.", errorEvento(evento("tipo", "")));
        assertEquals("Debe seleccionar el tipo de evento.", errorEvento(evento("tipo", "VACACIONES")));
    }

    @Test
    void elTituloYLaDescripcionTienenLimites() {
        String mensajeTitulo = "El título es obligatorio. Debe contener entre 5 y 200 caracteres.";
        assertEquals(mensajeTitulo, errorEvento(evento("titulo", "")));
        assertEquals(mensajeTitulo, errorEvento(evento("titulo", "abc")));
        assertEquals(mensajeTitulo, errorEvento(evento("titulo", "x".repeat(201))));
        assertEquals("La descripción no puede exceder 500 caracteres.", errorEvento(evento("descripcion", "x".repeat(501))));
    }

    @Test
    void unEventoDeTodoElDiaCubreDesdeElInicioDelPrimerDiaHastaElFinalDelUltimo() {
        Map<String, Object> r = servicio.crearEvento(3L, evento("todoElDia", "true", "inicio", "2026-11-10", "fin", "2026-11-12"));
        assertEquals("2026-11-10T00:00", r.get("inicio"));
        assertEquals("2026-11-12T23:59:59", r.get("fin"));
        assertEquals(true, r.get("todoElDia"));

        // un solo día completo es válido
        assertDoesNotThrow(() -> servicio.crearEvento(3L, evento("todoElDia", "true", "inicio", "2026-11-10", "fin", "2026-11-10")));
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.",
                errorEvento(evento("todoElDia", "true", "inicio", "2026-11-10", "fin", "2026-11-09")));
    }

    @Test
    void soloUnMedicoTieneAgenda() {
        assertEquals("Solo un médico tiene agenda médica.",
                assertThrows(IllegalArgumentException.class, () -> servicio.crearEvento(7L, evento())).getMessage());
    }

    @Test
    void editarYEliminarSoloFuncionanEnLaPropiaAgenda() {
        Usuario otro = new Usuario();
        otro.setId(9L);
        EventoAgenda ajeno = new EventoAgenda();
        ajeno.setId(70L);
        ajeno.setMedico(otro);
        when(eventoRepository.findById(70L)).thenReturn(Optional.of(ajeno));
        EventoAgenda propio = new EventoAgenda();
        propio.setId(71L);
        propio.setMedico(medico);
        when(eventoRepository.findById(71L)).thenReturn(Optional.of(propio));

        assertEquals("El evento pertenece a la agenda de otro médico.",
                assertThrows(IllegalArgumentException.class, () -> servicio.eliminarEvento(3L, 70L)).getMessage());
        assertEquals("El evento pertenece a la agenda de otro médico.",
                assertThrows(IllegalArgumentException.class, () -> servicio.actualizarEvento(3L, 70L, evento())).getMessage());

        assertEquals("Evento actualizado exitosamente.", servicio.actualizarEvento(3L, 71L, evento("titulo", "Reunión de equipo")).get("mensaje"));
        servicio.eliminarEvento(3L, 71L);
        verify(eventoRepository).delete(propio);
    }

    @Test
    void elCalendarioNoMuestraCitasPendientesNoAsistidasNiCanceladas() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 10, 10, 0);
        Cita[] citas = new Cita[EstadoCita.values().length];
        int i = 0;
        for (EstadoCita estado : EstadoCita.values()) {
            Usuario p = new Usuario();
            p.setNombreCompleto("Paciente " + estado);
            Especialidad e = new Especialidad();
            e.setNombre("General");
            Sucursal s = new Sucursal();
            s.setNombre("Central");
            Cita c = new Cita();
            c.setId((long) i + 1);
            c.setPaciente(p);
            c.setEspecialidad(e);
            c.setSucursal(s);
            c.setFechaHora(t.plusMinutes(30L * i));
            c.setEstado(estado);
            citas[i++] = c;
        }
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq3(), any(), any())).thenReturn(List.of(citas));
        when(eventoRepository.findByMedicoIdAndInicioLessThanAndFinGreaterThanOrderByInicio(eq3(), any(), any())).thenReturn(List.of());

        Map<String, Object> agenda = servicio.agenda(3L, t.minusDays(1), t.plusDays(1));
        List<?> visibles = (List<?>) agenda.get("citas");

        assertEquals(EstadoCita.values().length - 3, visibles.size());
        assertTrue(visibles.stream().map(v -> ((Map<?, ?>) v).get("estado").toString())
                .noneMatch(e -> e.equals("PENDIENTE_PAGO") || e.equals("NO_ASISTIO") || e.equals("CANCELADA")));
    }

    private static Long eq3() {
        return org.mockito.ArgumentMatchers.eq(3L);
    }

    @Test
    void elRangoDeFechasDebeSerValido() {
        LocalDateTime t = LocalDateTime.now();
        assertEquals("El rango de fechas indicado no es válido.",
                assertThrows(IllegalArgumentException.class, () -> servicio.agenda(3L, t, t.minusDays(1))).getMessage());
    }

    // ------------------------------------------------------------ tareas

    private Map<String, Object> tarea(String... extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("titulo", "Llamar al laboratorio");
        m.put("descripcion", "Confirmar resultados");
        m.put("prioridad", "ALTA");
        m.put("fechaLimite", LocalDate.now().plusDays(1).toString());
        for (int i = 0; i < extra.length; i += 2) {
            m.put(extra[i], extra[i + 1]);
        }
        return m;
    }

    private String errorTarea(Map<String, Object> datos) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.crearTarea(3L, datos)).getMessage();
    }

    @Test
    void creaLaTareaComoPendiente() {
        Map<String, Object> t = servicio.crearTarea(3L, tarea());
        assertEquals(false, t.get("completada"));
        assertEquals("Pendiente", t.get("estado"));
        assertEquals("Alta", t.get("prioridadEtiqueta"));
    }

    @Test
    void laPrioridadYLaFechaLimiteSonObligatorias() {
        assertEquals("Debe seleccionar la prioridad de la tarea.", errorTarea(tarea("prioridad", "")));
        assertEquals("Debe seleccionar la prioridad de la tarea.", errorTarea(tarea("prioridad", "URGENTE")));
        assertEquals("La fecha límite es obligatoria.", errorTarea(tarea("fechaLimite", "")));
        assertEquals("La fecha límite no es válida.", errorTarea(tarea("fechaLimite", "pronto")));
        assertEquals("La descripción no puede exceder 1000 caracteres.", errorTarea(tarea("descripcion", "x".repeat(1001))));
    }

    @Test
    void completarYDescompletarUnaTarea() {
        TareaAgenda t = new TareaAgenda();
        t.setId(80L);
        t.setMedico(medico);
        t.setTitulo("Tarea de prueba");
        t.setPrioridad(PrioridadTarea.NORMAL);
        t.setFechaLimite(LocalDate.now());
        when(tareaRepository.findById(80L)).thenReturn(Optional.of(t));

        assertEquals(true, servicio.completarTarea(3L, 80L, true).get("completada"));
        assertEquals("Completada", servicio.completarTarea(3L, 80L, true).get("estado"));
        assertEquals(false, servicio.completarTarea(3L, 80L, false).get("completada"));
    }

    @Test
    void lasTareasSeFiltranPorDiaYEstado() {
        LocalDate hoy = LocalDate.now();
        TareaAgenda a = unaTarea(1L, hoy, false);
        TareaAgenda b = unaTarea(2L, hoy, true);
        TareaAgenda c = unaTarea(3L, hoy.plusDays(1), false);
        when(tareaRepository.findByMedicoIdOrderByFechaLimiteAscIdAsc(3L)).thenReturn(List.of(a, b, c));

        assertEquals(3, servicio.tareas(3L, null, "TODAS").size());
        assertEquals(2, servicio.tareas(3L, hoy, "TODAS").size());
        assertEquals(1, servicio.tareas(3L, hoy, "PENDIENTES").size());
        assertEquals(1, servicio.tareas(3L, hoy, "COMPLETADAS").size());
        assertEquals("El filtro de estado no es válido.", assertThrows(IllegalArgumentException.class, () -> servicio.tareas(3L, hoy, "XX")).getMessage());
    }

    private TareaAgenda unaTarea(Long id, LocalDate fecha, boolean completada) {
        TareaAgenda t = new TareaAgenda();
        t.setId(id);
        t.setMedico(medico);
        t.setTitulo("Tarea " + id);
        t.setPrioridad(PrioridadTarea.BAJA);
        t.setFechaLimite(fecha);
        t.setCompletada(completada);
        return t;
    }
}
