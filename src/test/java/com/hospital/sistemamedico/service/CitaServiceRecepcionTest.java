package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.EventoAgendaRepository;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la recepción de pacientes (CU-05) y del agendamiento desde el portal (CU-03):
 * hora de llegada, emergencia sin cita previa, reasignación con motivo y horarios
 * de atención. No necesitan base de datos.
 */
class CitaServiceRecepcionTest {

    private CitaService servicio;
    private CitaRepository citas;
    private UsuarioService usuarios;
    private AuditoriaService auditoria;
    private SucursalService sucursales;
    private Sucursal sucursal;
    private Especialidad general;
    private Usuario medico;
    private Usuario paciente;
    private Usuario recepcionista;

    @BeforeEach
    void preparar() {
        servicio = new CitaService();
        citas = mock(CitaRepository.class);
        usuarios = mock(UsuarioService.class);
        auditoria = mock(AuditoriaService.class);
        sucursales = mock(SucursalService.class);
        EspecialidadService especialidades = mock(EspecialidadService.class);
        ReflectionTestUtils.setField(servicio, "citaRepository", citas);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarios);
        ReflectionTestUtils.setField(servicio, "auditoriaService", auditoria);
        ReflectionTestUtils.setField(servicio, "sucursalService", sucursales);
        ReflectionTestUtils.setField(servicio, "especialidadService", especialidades);
        ReflectionTestUtils.setField(servicio, "agendaService", mock(AgendaService.class));
        ReflectionTestUtils.setField(servicio, "eventoAgendaRepository", mock(EventoAgendaRepository.class));
        SedeEspecialidadRepository sedeEsp = mock(SedeEspecialidadRepository.class);
        when(sedeEsp.existsBySucursalIdAndEspecialidadIdAndActivoTrue(any(), any())).thenReturn(true);
        ReflectionTestUtils.setField(servicio, "sedeEspecialidadRepository", sedeEsp);

        sucursal = new Sucursal();
        sucursal.setId(1L);
        sucursal.setNombre("Central");
        general = new Especialidad();
        general.setId(1L);
        general.setNombre("Medicina General");
        medico = new Usuario();
        medico.setId(3L);
        medico.setRol(Rol.MEDICO);
        medico.setNombreCompleto("Dr. Pérez");
        medico.setSucursal(sucursal);
        medico.setEspecialidad(general);
        paciente = new Usuario();
        paciente.setId(16L);
        paciente.setRol(Rol.PACIENTE);
        paciente.setNombreCompleto("Paciente Prueba");
        recepcionista = new Usuario();
        recepcionista.setId(8L);
        recepcionista.setRol(Rol.RECEPCIONISTA);
        recepcionista.setSucursal(sucursal);

        when(usuarios.buscarPorId(3L)).thenReturn(medico);
        when(usuarios.buscarPorId(16L)).thenReturn(paciente);
        when(usuarios.buscarPorId(8L)).thenReturn(recepcionista);
        when(usuarios.listarPorRol(Rol.MEDICO)).thenReturn(List.of(medico));
        when(sucursales.buscarPorId(1L)).thenReturn(sucursal);
        when(especialidades.buscarPorId(1L)).thenReturn(general);
        when(citas.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------ llegada del paciente

    @Test
    void alRegistrarLaLlegadaQuedaLaHoraExactaYElEstadoPacientePresente() {
        Cita cita = new Cita();
        cita.setId(10L);
        cita.setEstado(EstadoCita.CONFIRMADA);
        when(citas.findById(10L)).thenReturn(Optional.of(cita));

        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        Cita r = servicio.marcarPacientePresente(10L);

        assertEquals(EstadoCita.PACIENTE_PRESENTE, r.getEstado());
        assertNotNull(r.getHoraLlegada());
        assertTrue(r.getHoraLlegada().isAfter(antes));
    }

    @Test
    void soloUnaCitaConfirmadaPuedeRegistrarLlegada() {
        Cita cita = new Cita();
        cita.setId(10L);
        cita.setEstado(EstadoCita.PENDIENTE_PAGO);
        when(citas.findById(10L)).thenReturn(Optional.of(cita));
        assertThrows(IllegalArgumentException.class, () -> servicio.marcarPacientePresente(10L));
        assertNull(cita.getHoraLlegada());
    }

    // ------------------------------------------------------------ emergencia sin cita (FA01)

    @Test
    void laEmergenciaCreaUnaCitaPrioritariaYListaParaSignosVitales() {
        Cita c = servicio.registrarEmergencia(16L, 8L);

        assertTrue(c.isEmergencia());
        assertEquals(EstadoCita.PACIENTE_PRESENTE, c.getEstado());
        assertNotNull(c.getHoraLlegada());
        assertEquals(16L, c.getPaciente().getId());
        assertEquals(3L, c.getMedico().getId());
        assertEquals(1L, c.getSucursal().getId());
        assertFalse(c.isAgendadaPorPaciente());
    }

    @Test
    void laEmergenciaPrefiereMedicinaGeneral() {
        Especialidad pediatria = new Especialidad();
        pediatria.setId(2L);
        pediatria.setNombre("Pediatría");
        Usuario pediatra = new Usuario();
        pediatra.setId(4L);
        pediatra.setRol(Rol.MEDICO);
        pediatra.setSucursal(sucursal);
        pediatra.setEspecialidad(pediatria);
        when(usuarios.listarPorRol(Rol.MEDICO)).thenReturn(List.of(pediatra, medico));

        assertEquals(3L, servicio.registrarEmergencia(16L, 8L).getMedico().getId());
    }

    @Test
    void laEmergenciaValidaQuienRegistraElPacienteYLaDisponibilidadDeMedicos() {
        recepcionista.setRol(Rol.CAJERO);
        assertEquals("Solo el personal de recepción puede registrar una emergencia.",
                assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmergencia(16L, 8L)).getMessage());

        recepcionista.setRol(Rol.RECEPCIONISTA);
        paciente.setRol(Rol.MEDICO);
        assertEquals("El usuario indicado no es un paciente.",
                assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmergencia(16L, 8L)).getMessage());

        paciente.setRol(Rol.PACIENTE);
        when(usuarios.listarPorRol(Rol.MEDICO)).thenReturn(List.of());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> servicio.registrarEmergencia(16L, 8L))
                .getMessage().startsWith("No hay un médico disponible"));
    }

    // ------------------------------------------------------------ reasignación (FA07)

    @Test
    void laReasignacionDejaConstanciaConElMotivo() {
        Usuario otro = new Usuario();
        otro.setId(4L);
        otro.setRol(Rol.MEDICO);
        otro.setNombreCompleto("Dra. López");
        otro.setSucursal(sucursal);
        otro.setEspecialidad(general);
        when(usuarios.buscarPorId(4L)).thenReturn(otro);
        Cita cita = new Cita();
        cita.setId(10L);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setMedico(medico);
        cita.setSucursal(sucursal);
        cita.setEspecialidad(general);
        when(citas.findById(10L)).thenReturn(Optional.of(cita));

        servicio.reasignarMedico(10L, 4L, 8L, "El médico tiene una cirugía");

        assertEquals(4L, cita.getMedico().getId());
        verify(auditoria).registrar(eq(recepcionista), eq("REASIGNAR_MEDICO"), eq("Cita"), eq(10L),
                org.mockito.ArgumentMatchers.contains("Motivo: El médico tiene una cirugía"));
    }

    // ------------------------------------------------------------ horarios del portal (CU-03)

    private String errorPortal(LocalDateTime fecha, boolean portal) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.agendarCita(16L, 3L, 1L, 1L, fecha,
                "Dolor de cabeza persistente", false, null, portal)).getMessage();
    }

    @Test
    void elPacienteSoloPuedeElegirHorariosDeAtencion() {
        String m = "Seleccione una fecha futura dentro de los horarios disponibles del médico.";
        LocalDate manana = LocalDate.now().plusDays(1);
        assertEquals(m, errorPortal(manana.atTime(7, 30), true));
        assertEquals(m, errorPortal(manana.atTime(10, 15), true));
        assertEquals(m, errorPortal(manana.atTime(17, 0), true));
        assertEquals(m, errorPortal(manana.atTime(22, 0), true));
        assertDoesNotThrow(() -> servicio.agendarCita(16L, 3L, 1L, 1L, manana.atTime(8, 0), "Dolor de cabeza persistente", false, null, true));
        assertDoesNotThrow(() -> servicio.agendarCita(16L, 3L, 1L, 1L, manana.atTime(16, 30), "Dolor de cabeza persistente", false, null, true));
    }

    @Test
    void laCitaPresencialDeRecepcionNoTieneEsaRestriccion() {
        LocalDate manana = LocalDate.now().plusDays(1);
        assertDoesNotThrow(() -> servicio.agendarCita(16L, 3L, 1L, 1L, manana.atTime(18, 20), "Dolor de cabeza persistente", false, null, false));
    }

    @Test
    void lasFechasPasadasSiguenRechazadasConElMensajeDeLaRegla() {
        assertEquals("Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.",
                errorPortal(LocalDateTime.now().minusDays(1), true));
    }
}
