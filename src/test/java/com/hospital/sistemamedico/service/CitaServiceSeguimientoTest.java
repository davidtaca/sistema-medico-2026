package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la cita de seguimiento (CU-12): validaciones, conflicto de
 * horario (FA01), calendario de disponibilidad y recordatorio automático.
 * No necesitan base de datos: los repositorios son simulados.
 */
class CitaServiceSeguimientoTest {

    private CitaService servicio;
    private CitaRepository citaRepository;
    private ConsultaRepository consultaRepository;
    private UsuarioService usuarioService;
    private EmailService emailService;
    private Cita origen;

    @BeforeEach
    void preparar() {
        servicio = new CitaService();
        citaRepository = mock(CitaRepository.class);
        consultaRepository = mock(ConsultaRepository.class);
        usuarioService = mock(UsuarioService.class);
        emailService = mock(EmailService.class);
        SucursalService sucursalService = mock(SucursalService.class);
        EspecialidadService especialidadService = mock(EspecialidadService.class);
        ReflectionTestUtils.setField(servicio, "citaRepository", citaRepository);
        ReflectionTestUtils.setField(servicio, "consultaRepository", consultaRepository);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(servicio, "emailService", emailService);
        ReflectionTestUtils.setField(servicio, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(servicio, "especialidadService", especialidadService);

        Sucursal sucursal = new Sucursal();
        sucursal.setId(1L);
        sucursal.setNombre("Sucursal Central");
        Especialidad especialidad = new Especialidad();
        especialidad.setId(1L);
        especialidad.setNombre("Medicina General");
        Usuario medico = new Usuario();
        medico.setId(3L);
        medico.setRol(Rol.MEDICO);
        medico.setNombreCompleto("Dr. Prueba");
        medico.setSucursal(sucursal);
        medico.setEspecialidad(especialidad);
        Usuario paciente = new Usuario();
        paciente.setId(16L);
        paciente.setRol(Rol.PACIENTE);
        paciente.setNombreCompleto("Paciente Prueba");
        paciente.setCorreo("paciente@example.test");

        origen = new Cita();
        origen.setId(15L);
        origen.setPaciente(paciente);
        origen.setMedico(medico);
        origen.setSucursal(sucursal);
        origen.setEspecialidad(especialidad);
        origen.setEstado(EstadoCita.EVALUADO_PENDIENTE_CIERRE);

        when(citaRepository.findById(15L)).thenReturn(Optional.of(origen));
        when(consultaRepository.findByCitaId(15L)).thenReturn(Optional.of(new Consulta()));
        when(usuarioService.buscarPorId(3L)).thenReturn(medico);
        when(usuarioService.buscarPorId(16L)).thenReturn(paciente);
        when(sucursalService.buscarPorId(1L)).thenReturn(sucursal);
        when(especialidadService.buscarPorId(1L)).thenReturn(especialidad);
        when(citaRepository.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Mañana a las 10:00: futuro y dentro del horario de atención. */
    private LocalDateTime horarioValido() {
        return LocalDate.now().plusDays(1).atTime(10, 0);
    }

    private String errorAgendando(String tipo, LocalDateTime fecha, String observaciones) {
        return assertThrows(IllegalArgumentException.class,
                () -> servicio.agendarSeguimiento(15L, 3L, tipo, fecha, observaciones, null)).getMessage();
    }

    @Test
    void agendaElSeguimientoConLosDatosDeLaCitaOriginalYNotificaAlPaciente() {
        Cita c = servicio.agendarSeguimiento(15L, 3L, "MONITOREO_TRATAMIENTO", horarioValido(), "Revisar evolución del tratamiento", "ALTA");

        assertEquals(TipoSeguimiento.MONITOREO_TRATAMIENTO, c.getTipoSeguimiento());
        assertEquals(PrioridadSeguimiento.ALTA, c.getPrioridad());
        assertEquals(15L, c.getCitaOrigenId());
        assertEquals(EstadoCita.PENDIENTE_PAGO, c.getEstado());
        assertEquals(16L, c.getPaciente().getId());
        assertEquals(3L, c.getMedico().getId());
        verify(emailService).enviarNotificacionSeguimiento(eq("paciente@example.test"), eq("Paciente Prueba"), eq("Dr. Prueba"),
                eq("Sucursal Central"), eq(c.getFechaHora()), eq("Monitoreo de Tratamiento"), eq("Revisar evolución del tratamiento"));
    }

    @Test
    void sinPrioridadSeUsaMedia() {
        Cita c = servicio.agendarSeguimiento(15L, 3L, "REVISION_RESULTADOS_LABORATORIO", horarioValido(), "Revisar resultados de laboratorio", null);
        assertEquals(PrioridadSeguimiento.MEDIA, c.getPrioridad());
    }

    @Test
    void elTipoDeSeguimientoEsObligatorio() {
        assertEquals("Debe seleccionar el tipo de seguimiento.", errorAgendando(null, horarioValido(), "Observaciones válidas"));
        assertEquals("Debe seleccionar el tipo de seguimiento.", errorAgendando("OTRO", horarioValido(), "Observaciones válidas"));
    }

    @Test
    void laFechaDebeSerFuturaYDentroDelHorarioDeAtencion() {
        String mensaje = "Seleccione una fecha futura dentro de los horarios disponibles del médico.";
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", null, "Observaciones válidas"));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", LocalDateTime.now().minusDays(1), "Observaciones válidas"));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", LocalDate.now().plusDays(1).atTime(10, 15), "Observaciones válidas"));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", LocalDate.now().plusDays(1).atTime(7, 30), "Observaciones válidas"));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", LocalDate.now().plusDays(1).atTime(17, 0), "Observaciones válidas"));
    }

    @Test
    void lasObservacionesDebenTenerEntre10y2000Caracteres() {
        String mensaje = "Las observaciones son obligatorias. Deben contener entre 10 y 2000 caracteres.";
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), null));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), "corto"));
        assertEquals(mensaje, errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), "x".repeat(2001)));
    }

    @Test
    void horarioOcupadoMuestraElMensajeDelConflictoFA01() {
        when(citaRepository.existsByMedicoIdAndFechaHoraAndEstadoNotIn(eq(3L), any(), any())).thenReturn(true);
        assertEquals("El horario seleccionado ya no está disponible. Por favor, elija otro horario.",
                errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), "Observaciones válidas"));
        verify(emailService, never()).enviarNotificacionSeguimiento(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void soloSePuedeAgendarDesdeUnaConsultaActivaORecienFinalizada() {
        origen.setEstado(EstadoCita.ATENCION_FINALIZADA);
        assertTrue(errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), "Observaciones válidas").contains("consulta activa o recién finalizada"));
    }

    @Test
    void laConsultaDebeEstarGuardadaYSerDelMedico() {
        when(consultaRepository.findByCitaId(15L)).thenReturn(Optional.empty());
        assertEquals("Guarde la consulta antes de agendar un seguimiento.", errorAgendando("MONITOREO_TRATAMIENTO", horarioValido(), "Observaciones válidas"));

        assertEquals("La cita no está asignada a este médico.",
                assertThrows(IllegalArgumentException.class,
                        () -> servicio.agendarSeguimiento(15L, 99L, "MONITOREO_TRATAMIENTO", horarioValido(), "Observaciones válidas", null)).getMessage());
    }

    @Test
    void elCalendarioMarcaOcupadosLosHorariosConCitaVigenteYLiberaLosCancelados() {
        LocalDate dia = LocalDate.now().plusDays(2);
        Cita ocupada = new Cita();
        ocupada.setFechaHora(dia.atTime(9, 0));
        ocupada.setEstado(EstadoCita.CONFIRMADA);
        Cita cancelada = new Cita();
        cancelada.setFechaHora(dia.atTime(9, 30));
        cancelada.setEstado(EstadoCita.CANCELADA);
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(3L), any(), any())).thenReturn(List.of(ocupada, cancelada));

        List<Map<String, Object>> horarios = servicio.disponibilidad(3L, dia.toString());

        assertEquals(18, horarios.size());
        assertEquals("08:00", horarios.get(0).get("hora"));
        assertEquals("16:30", horarios.get(17).get("hora"));
        assertEquals(false, horarios.stream().filter(h -> h.get("hora").equals("09:00")).findFirst().get().get("disponible"));
        assertEquals(true, horarios.stream().filter(h -> h.get("hora").equals("09:30")).findFirst().get().get("disponible"));
    }

    @Test
    void elCalendarioNoOfreceHorariosQueYaPasaron() {
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(3L), any(), any())).thenReturn(List.of());
        List<Map<String, Object>> ayer = servicio.disponibilidad(3L, LocalDate.now().minusDays(1).toString());
        assertTrue(ayer.stream().noneMatch(h -> (boolean) h.get("disponible")));
    }

    // ------------------------------------------------------------ recordatorio (RN-CU11-05)

    private Cita seguimientoProximo(EstadoCita estado) {
        Cita c = new Cita();
        c.setPaciente(origen.getPaciente());
        c.setMedico(origen.getMedico());
        c.setSucursal(origen.getSucursal());
        c.setFechaHora(LocalDateTime.now().plusHours(20));
        c.setEstado(estado);
        c.setTipoSeguimiento(TipoSeguimiento.MONITOREO_TRATAMIENTO);
        return c;
    }

    @Test
    void elRecordatorioSeEnviaUnaSolaVezYQuedaMarcado() {
        Cita c = seguimientoProximo(EstadoCita.CONFIRMADA);
        when(citaRepository.findByTipoSeguimientoIsNotNullAndRecordatorioEnviadoFalseAndFechaHoraBetween(any(), any())).thenReturn(List.of(c));
        when(emailService.enviarRecordatorioSeguimiento(any(), any(), any(), any(), any(), any())).thenReturn(true);

        servicio.enviarRecordatoriosSeguimiento();

        assertTrue(c.isRecordatorioEnviado());
        verify(citaRepository).save(c);
    }

    @Test
    void noSeEnviaRecordatorioDeCitasCanceladas() {
        Cita c = seguimientoProximo(EstadoCita.CANCELADA);
        when(citaRepository.findByTipoSeguimientoIsNotNullAndRecordatorioEnviadoFalseAndFechaHoraBetween(any(), any())).thenReturn(List.of(c));

        servicio.enviarRecordatoriosSeguimiento();

        assertFalse(c.isRecordatorioEnviado());
        verify(emailService, never()).enviarRecordatorioSeguimiento(any(), any(), any(), any(), any(), any());
    }

    @Test
    void siElEnvioFallaElRecordatorioSeReintentaDespues() {
        Cita c = seguimientoProximo(EstadoCita.PENDIENTE_PAGO);
        when(citaRepository.findByTipoSeguimientoIsNotNullAndRecordatorioEnviadoFalseAndFechaHoraBetween(any(), any())).thenReturn(List.of(c));
        when(emailService.enviarRecordatorioSeguimiento(any(), any(), any(), any(), any(), any())).thenReturn(false);

        servicio.enviarRecordatoriosSeguimiento();

        assertFalse(c.isRecordatorioEnviado());
        verify(citaRepository, never()).save(any());
    }
}
