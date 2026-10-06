package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la configuración de especialidades por sede (CU-13). No
 * necesitan base de datos: los repositorios son simulados.
 */
class SedeEspecialidadServiceTest {

    private SedeEspecialidadService servicio;
    private SedeEspecialidadRepository repository;
    private SucursalRepository sucursalRepository;
    private EspecialidadRepository especialidadRepository;
    private AuditoriaService auditoriaService;
    private Usuario admin;
    private Sucursal sede;
    private Especialidad especialidad;

    @BeforeEach
    void preparar() {
        servicio = new SedeEspecialidadService();
        repository = mock(SedeEspecialidadRepository.class);
        sucursalRepository = mock(SucursalRepository.class);
        especialidadRepository = mock(EspecialidadRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "repository", repository);
        ReflectionTestUtils.setField(servicio, "sucursalRepository", sucursalRepository);
        ReflectionTestUtils.setField(servicio, "especialidadRepository", especialidadRepository);
        ReflectionTestUtils.setField(servicio, "auditoriaService", auditoriaService);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarioService);

        admin = new Usuario();
        admin.setId(1L);
        admin.setRol(Rol.ADMINISTRADOR);
        sede = new Sucursal();
        sede.setId(10L);
        sede.setNombre("Sucursal Norte");
        especialidad = new Especialidad();
        especialidad.setId(20L);
        especialidad.setNombre("Pediatría");
        when(usuarioService.buscarPorId(1L)).thenReturn(admin);
        when(sucursalRepository.findById(10L)).thenReturn(Optional.of(sede));
        when(especialidadRepository.findById(20L)).thenReturn(Optional.of(especialidad));
        when(repository.save(any(SedeEspecialidad.class))).thenAnswer(i -> {
            SedeEspecialidad s = i.getArgument(0);
            s.setId(99L);
            return s;
        });
    }

    private String error(Long sedeId, Long especialidadId) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.asignar(1L, sedeId, especialidadId)).getMessage();
    }

    @Test
    void asignaLaEspecialidadActivaYRegistraLaAuditoria() {
        SedeEspecialidad s = servicio.asignar(1L, 10L, 20L);

        assertTrue(s.isActivo());
        assertEquals("Pediatría", s.getEspecialidad().getNombre());
        verify(auditoriaService).registrar(eq(admin), eq("ASIGNAR_ESPECIALIDAD_SEDE"), eq("SedeEspecialidad"), eq(99L),
                contains("Pediatría"));
    }

    @Test
    void laSedeYLaEspecialidadSonObligatorias() {
        assertEquals("Debe seleccionar una sede.", error(null, 20L));
        assertEquals("Debe seleccionar una especialidad.", error(10L, null));
        verify(repository, never()).save(any());
    }

    @Test
    void noSePermiteLaMismaCombinacionDosVeces() {
        when(repository.existsBySucursalIdAndEspecialidadId(10L, 20L)).thenReturn(true);
        assertEquals("Esta combinación de sede y especialidad ya existe en el sistema.", error(10L, 20L));
        verify(repository, never()).save(any());
    }

    @Test
    void noSePermiteUnaSedeOEspecialidadInactiva() {
        sede.setActivo(false);
        assertEquals("La sede seleccionada no existe o no está activa.", error(10L, 20L));
        sede.setActivo(true);
        especialidad.setActivo(false);
        assertEquals("La especialidad seleccionada no existe o no está activa.", error(10L, 20L));
    }

    @Test
    void soloUnAdministradorPuedeConfigurar() {
        admin.setRol(Rol.MEDICO);
        assertEquals("Solo un administrador puede configurar las especialidades por sede.", error(10L, 20L));
        assertThrows(IllegalArgumentException.class, () -> servicio.eliminar(1L, 5L));
    }

    @Test
    void eliminarRegistraLaAuditoriaYLaAsignacionInexistenteDaError() {
        SedeEspecialidad s = new SedeEspecialidad(sede, especialidad);
        s.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(s));

        servicio.eliminar(1L, 5L);

        verify(repository).delete(s);
        verify(auditoriaService).registrar(eq(admin), eq("ELIMINAR_ESPECIALIDAD_SEDE"), eq("SedeEspecialidad"), eq(5L), contains("Pediatría"));
        assertEquals("La asignación no existe.", assertThrows(IllegalArgumentException.class, () -> servicio.eliminar(1L, 404L)).getMessage());
    }

    @Test
    void elListadoSePaginaYSePuedeFiltrarPorId() {
        List<SedeEspecialidad> todas = new java.util.ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            SedeEspecialidad s = new SedeEspecialidad(sede, especialidad);
            s.setId(i);
            todas.add(s);
        }
        when(repository.findAll()).thenReturn(todas);
        when(repository.findById(7L)).thenReturn(Optional.of(todas.get(6)));
        when(repository.findById(999L)).thenReturn(Optional.empty());

        Map<String, Object> pagina3 = servicio.listar(null, 2, 10);
        assertEquals(5, ((List<?>) pagina3.get("contenido")).size());
        assertEquals(25, pagina3.get("totalElementos"));
        assertEquals(3, pagina3.get("totalPaginas"));

        assertEquals(1, ((List<?>) servicio.listar(7L, 0, 10).get("contenido")).size());
        assertEquals(0, servicio.listar(999L, 0, 10).get("totalElementos"));
    }

    @Test
    void unaAsignacionConSedeInactivaSeMuestraInactiva() {
        SedeEspecialidad s = new SedeEspecialidad(sede, especialidad);
        s.setId(1L);
        assertEquals(true, servicio.aMapa(s).get("activo"));
        sede.setActivo(false);
        assertEquals(false, servicio.aMapa(s).get("activo"));
    }

    @Test
    void laCitaNormalExigeQueLaEspecialidadEsteAsignadaALaSede() {
        CitaService citas = new CitaService();
        UsuarioService usuarioService = mock(UsuarioService.class);
        SucursalService sucursalService = mock(SucursalService.class);
        EspecialidadService especialidadService = mock(EspecialidadService.class);
        Usuario paciente = new Usuario();
        paciente.setRol(Rol.PACIENTE);
        Usuario medico = new Usuario();
        medico.setRol(Rol.MEDICO);
        medico.setSucursal(sede);
        medico.setEspecialidad(especialidad);
        when(usuarioService.buscarPorId(5L)).thenReturn(paciente);
        when(usuarioService.buscarPorId(6L)).thenReturn(medico);
        when(sucursalService.buscarPorId(10L)).thenReturn(sede);
        when(especialidadService.buscarPorId(20L)).thenReturn(especialidad);
        ReflectionTestUtils.setField(citas, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(citas, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(citas, "especialidadService", especialidadService);
        ReflectionTestUtils.setField(citas, "citaRepository", mock(CitaRepository.class));
        ReflectionTestUtils.setField(citas, "sedeEspecialidadRepository", repository);
        when(repository.existsBySucursalIdAndEspecialidadIdAndActivoTrue(10L, 20L)).thenReturn(false);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> citas.agendarCita(5L, 6L, 10L, 20L,
                LocalDate.now().plusDays(2).atTime(10, 0), "Dolor de cabeza persistente", false, null, true));
        assertEquals("La especialidad seleccionada no está disponible en la sede indicada.", e.getMessage());
    }
}
