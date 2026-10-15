package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Especialidad;
import com.hospital.sistemamedico.model.SedeEspecialidad;
import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import com.hospital.sistemamedico.repository.ExamenRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la información pública del portal web (CU-00): contenido tomado de
 * los catálogos, sedes y especialidades inactivas fuera del portal, horarios y
 * caché precargada.
 */
class PortalServiceTest {

    private PortalService servicio;
    private SucursalRepository sucursales;
    private SedeEspecialidadRepository asignaciones;
    private Sucursal central;
    private Sucursal norte;
    private Especialidad general;
    private Especialidad pediatria;

    @BeforeEach
    void preparar() {
        servicio = new PortalService();
        sucursales = mock(SucursalRepository.class);
        asignaciones = mock(SedeEspecialidadRepository.class);
        EspecialidadRepository especialidades = mock(EspecialidadRepository.class);
        ExamenRepository examenes = mock(ExamenRepository.class);
        MedicamentoRepository medicamentos = mock(MedicamentoRepository.class);
        ReflectionTestUtils.setField(servicio, "sucursalRepository", sucursales);
        ReflectionTestUtils.setField(servicio, "sedeEspecialidadRepository", asignaciones);
        ReflectionTestUtils.setField(servicio, "especialidadRepository", especialidades);
        ReflectionTestUtils.setField(servicio, "examenRepository", examenes);
        ReflectionTestUtils.setField(servicio, "medicamentoRepository", medicamentos);
        ReflectionTestUtils.setField(servicio, "nombreHospital", "Hospital Sistema Médico");
        ReflectionTestUtils.setField(servicio, "descripcionHospital", "Atención médica integral.");
        ReflectionTestUtils.setField(servicio, "telefonoHospital", "");
        ReflectionTestUtils.setField(servicio, "horarioConfigurado", "");

        central = sede(1L, "Sucursal Central", "Zona 10, Ciudad", "22334455");
        norte = sede(2L, "Sucursal Norte", null, null);
        general = especialidad(1L, "Medicina General", "Atención general");
        pediatria = especialidad(2L, "Pediatría", null);

        when(sucursales.findByActivoTrue()).thenReturn(List.of(norte, central));
        when(especialidades.findByActivoTrue()).thenReturn(List.of(pediatria, general));
        when(asignaciones.findAll()).thenReturn(List.of(asignacion(central, general), asignacion(central, pediatria), asignacion(norte, general)));
        when(examenes.findByActivoTrueOrderByNombre()).thenReturn(List.of());
        when(medicamentos.findByActivoTrueOrderByNombre()).thenReturn(List.of());
    }

    private Sucursal sede(Long id, String nombre, String direccion, String telefono) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre(nombre);
        s.setDireccion(direccion);
        s.setTelefono(telefono);
        return s;
    }

    private Especialidad especialidad(Long id, String nombre, String descripcion) {
        Especialidad e = new Especialidad();
        e.setId(id);
        e.setNombre(nombre);
        e.setDescripcion(descripcion);
        return e;
    }

    private SedeEspecialidad asignacion(Sucursal s, Especialidad e) {
        return new SedeEspecialidad(s, e);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> info, String clave) {
        return (List<Map<String, Object>>) info.get(clave);
    }

    @Test
    void muestraHospitalServiciosEspecialidadesUbicacionesYHorarios() {
        Map<String, Object> info = servicio.informacion();

        assertEquals("Hospital Sistema Médico", ((Map<?, ?>) info.get("hospital")).get("nombre"));
        assertEquals(6, lista(info, "servicios").size());
        assertEquals(2, lista(info, "especialidades").size());
        assertEquals(2, lista(info, "sedes").size());
        assertFalse(lista(info, "horarios").isEmpty());
    }

    @Test
    void lasSedesSeOrdenanPorNombreYMuestranDireccionTelefonoYEspecialidades() {
        List<Map<String, Object>> sedes = lista(servicio.informacion(), "sedes");

        assertEquals("Sucursal Central", sedes.get(0).get("nombre"));
        assertEquals("Zona 10, Ciudad", sedes.get(0).get("direccion"));
        assertEquals("22334455", sedes.get(0).get("telefono"));
        assertEquals(List.of("Medicina General", "Pediatría"), sedes.get(0).get("especialidades"));
        assertEquals("Sucursal Norte", sedes.get(1).get("nombre"));
        assertNull(sedes.get(1).get("direccion"));
        assertEquals(List.of("Medicina General"), sedes.get(1).get("especialidades"));
    }

    @Test
    void cadaEspecialidadIndicaEnQueSedesSeAtiende() {
        List<Map<String, Object>> esps = lista(servicio.informacion(), "especialidades");

        assertEquals("Medicina General", esps.get(0).get("nombre"));
        assertEquals(List.of("Sucursal Central", "Sucursal Norte"), esps.get(0).get("sedes"));
        assertEquals("Pediatría", esps.get(1).get("nombre"));
        assertEquals(List.of("Sucursal Central"), esps.get(1).get("sedes"));
    }

    @Test
    void noSeMuestranAsignacionesInactivasNiEspecialidadesSinSede() {
        SedeEspecialidad inactiva = asignacion(norte, pediatria);
        inactiva.setActivo(false);
        Especialidad sinSede = especialidad(3L, "Dermatología", "Piel");
        when(asignaciones.findAll()).thenReturn(List.of(asignacion(central, general), inactiva));
        EspecialidadRepository esp = (EspecialidadRepository) ReflectionTestUtils.getField(servicio, "especialidadRepository");
        when(esp.findByActivoTrue()).thenReturn(List.of(general, pediatria, sinSede));

        Map<String, Object> info = servicio.informacion();

        assertEquals(1, lista(info, "especialidades").size());
        assertEquals("Medicina General", lista(info, "especialidades").get(0).get("nombre"));
        assertEquals(List.of(), lista(info, "sedes").stream().filter(s -> s.get("nombre").equals("Sucursal Norte")).findFirst().get().get("especialidades"));
    }

    @Test
    void noSeMuestranSedesOEspecialidadesDeBaja() {
        central.setActivo(false);
        when(sucursales.findByActivoTrue()).thenReturn(List.of(norte));

        Map<String, Object> info = servicio.informacion();

        assertEquals(1, lista(info, "sedes").size());
        // la asignación a una sede dada de baja tampoco aparece
        assertEquals(List.of("Sucursal Norte"), lista(info, "especialidades").get(0).get("sedes"));
    }

    @Test
    void elHorarioPorDefectoSaleDeLosHorariosDeCitasYSePuedeConfigurar() {
        String porDefecto = lista(servicio.informacion(), "horarios").get(0).get("horario").toString();
        assertTrue(porDefecto.contains("8:00") && porDefecto.contains("17:00"), porDefecto);

        ReflectionTestUtils.setField(servicio, "horarioConfigurado", "Lunes a viernes, de 7:00 a 15:00");
        servicio.invalidar();
        assertEquals("Lunes a viernes, de 7:00 a 15:00", lista(servicio.informacion(), "horarios").get(0).get("horario"));
    }

    @Test
    void elTelefonoDelHospitalSoloSeMuestraSiEstaConfigurado() {
        assertNull(((Map<?, ?>) servicio.informacion().get("hospital")).get("telefono"));
        ReflectionTestUtils.setField(servicio, "telefonoHospital", "2222-3333");
        servicio.invalidar();
        assertEquals("2222-3333", ((Map<?, ?>) servicio.informacion().get("hospital")).get("telefono"));
    }

    @Test
    void laInformacionSeReutilizaDeLaCacheHastaQueSeInvalida() {
        servicio.precargar();
        servicio.informacion();
        servicio.informacion();
        verify(sucursales, times(1)).findByActivoTrue();

        // un cambio en el catálogo la descarta y se vuelve a armar
        Sucursal nueva = sede(3L, "Sucursal Sur", "Zona 5", "55667788");
        when(sucursales.findByActivoTrue()).thenReturn(List.of(central, norte, nueva));
        servicio.invalidar();
        assertEquals(3, lista(servicio.informacion(), "sedes").size());
        verify(sucursales, times(2)).findByActivoTrue();
    }
}
