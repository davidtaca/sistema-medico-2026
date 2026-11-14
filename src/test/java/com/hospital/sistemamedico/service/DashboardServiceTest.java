package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.DespachoRepository;
import com.hospital.sistemamedico.repository.PagoLaboratorioRepository;
import com.hospital.sistemamedico.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas del dashboard del administrador: las tres gráficas se calculan con lo que
 * devuelven los repositorios (simulados, sin base de datos).
 */
class DashboardServiceTest {

    private DashboardService servicio;
    private CitaRepository citas;
    private DespachoRepository despachos;
    private PagoRepository pagos;
    private PagoLaboratorioRepository pagosLab;

    @BeforeEach
    void preparar() {
        servicio = new DashboardService();
        UsuarioService usuarios = mock(UsuarioService.class);
        citas = mock(CitaRepository.class);
        despachos = mock(DespachoRepository.class);
        pagos = mock(PagoRepository.class);
        pagosLab = mock(PagoLaboratorioRepository.class);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarios);
        ReflectionTestUtils.setField(servicio, "citaRepository", citas);
        ReflectionTestUtils.setField(servicio, "despachoRepository", despachos);
        ReflectionTestUtils.setField(servicio, "pagoRepository", pagos);
        ReflectionTestUtils.setField(servicio, "pagoLaboratorioRepository", pagosLab);

        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setRol(Rol.ADMINISTRADOR);
        when(usuarios.buscarPorId(1L)).thenReturn(admin);
        Usuario medico = new Usuario();
        medico.setId(2L);
        medico.setRol(Rol.MEDICO);
        when(usuarios.buscarPorId(2L)).thenReturn(medico);
        when(citas.findAll()).thenReturn(List.of());
        when(despachos.unidadesDespachadasPorMedicamento()).thenReturn(List.of());
        when(pagos.findAll()).thenReturn(List.of());
        when(pagosLab.findAll()).thenReturn(List.of());
    }

    private Usuario medico(long id, String nombre) {
        Usuario m = new Usuario();
        m.setId(id);
        m.setNombreCompleto(nombre);
        m.setRol(Rol.MEDICO);
        return m;
    }

    private Cita consulta(Usuario medico, LocalDateTime fecha, EstadoCita estado) {
        Cita c = new Cita();
        c.setMedico(medico);
        c.setFechaHora(fecha);
        c.setEstado(estado);
        return c;
    }

    private Pago pago(long pacienteId, String forma) {
        Usuario paciente = new Usuario();
        paciente.setId(pacienteId);
        Cita cita = new Cita();
        cita.setPaciente(paciente);
        Pago p = new Pago();
        p.setCita(cita);
        p.setFormaPago(forma);
        p.setMetodoPago(MetodoPago.TARJETA);
        return p;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datos() {
        return servicio.dashboard(1L, 3, 10);
    }

    @Test
    void soloElAdministradorVeElDashboard() {
        assertEquals("Solo el administrador puede ver el dashboard.",
                assertThrows(IllegalArgumentException.class, () -> servicio.dashboard(2L, 3, 10)).getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void losMedicosSeCuentanSoloEnLosDiasDeMayorDemanda() {
        Usuario ana = medico(10, "Dra. Ana");
        Usuario luis = medico(11, "Dr. Luis");
        LocalDateTime lunes = LocalDateTime.of(2026, 11, 2, 9, 0);
        LocalDateTime martes = LocalDateTime.of(2026, 11, 3, 9, 0);
        LocalDateTime miercoles = LocalDateTime.of(2026, 11, 4, 9, 0);
        List<Cita> todas = new ArrayList<>();
        // lunes: 3 consultas, martes: 2, miércoles: 1 (con 1 solo día de demanda solo cuenta el lunes)
        todas.add(consulta(ana, lunes, EstadoCita.ATENCION_FINALIZADA));
        todas.add(consulta(ana, lunes.plusHours(1), EstadoCita.ATENCION_FINALIZADA));
        todas.add(consulta(luis, lunes.plusHours(2), EstadoCita.EVALUADO_PENDIENTE_CIERRE));
        todas.add(consulta(luis, martes, EstadoCita.ATENCION_FINALIZADA));
        todas.add(consulta(luis, martes.plusHours(1), EstadoCita.ATENCION_FINALIZADA));
        todas.add(consulta(ana, miercoles, EstadoCita.ATENCION_FINALIZADA));
        // estas no son consultas: no cuentan
        todas.add(consulta(ana, lunes.plusHours(3), EstadoCita.CANCELADA));
        todas.add(consulta(ana, lunes.plusHours(4), EstadoCita.NO_ASISTIO));
        todas.add(consulta(ana, lunes.plusHours(5), EstadoCita.CONFIRMADA));
        when(citas.findAll()).thenReturn(todas);

        Map<String, Object> g1 = (Map<String, Object>) servicio.dashboard(1L, 1, 10).get("medicosDiasDemanda");
        List<Map<String, Object>> dias = (List<Map<String, Object>>) g1.get("diasDeMayorDemanda");
        List<Map<String, Object>> medicos = (List<Map<String, Object>>) g1.get("medicos");
        assertEquals(1, dias.size());
        assertEquals("2026-11-02", dias.get(0).get("fecha"));
        assertEquals(3L, dias.get(0).get("consultas"));
        assertEquals("Dra. Ana", medicos.get(0).get("medico"));
        assertEquals(2, medicos.get(0).get("consultas"));
        assertEquals(1, medicos.get(1).get("consultas"));

        // con 3 días de demanda se suman todas las consultas válidas
        Map<String, Object> g3 = (Map<String, Object>) datos().get("medicosDiasDemanda");
        List<Map<String, Object>> medicos3 = (List<Map<String, Object>>) g3.get("medicos");
        assertEquals(3, medicos3.get(0).get("consultas"));
        assertEquals(3, medicos3.get(1).get("consultas"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void losMedicamentosVienenDeLoDespachadoEnLaBase() {
        when(despachos.unidadesDespachadasPorMedicamento())
                .thenReturn(List.of(new Object[]{"Acetaminofén 500 mg", 40L}, new Object[]{"Amoxicilina 500 mg", 12L}));
        List<Map<String, Object>> meds = (List<Map<String, Object>>) datos().get("medicamentosDespachados");
        assertEquals(2, meds.size());
        assertEquals("Acetaminofén 500 mg", meds.get(0).get("medicamento"));
        assertEquals(40L, meds.get(0).get("unidades"));

        List<Map<String, Object>> uno = (List<Map<String, Object>>) servicio.dashboard(1L, 3, 1).get("medicamentosDespachados");
        assertEquals(1, uno.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void losMetodosDePagoCuentanPagosYPacientesDistintos() {
        List<Pago> lista = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            lista.add(pago(i, "VISA"));
        }
        lista.add(pago(1, "VISA"));          // el paciente 1 pagó otra vez con Visa
        lista.add(pago(20, "MASTERCARD"));
        lista.add(pago(21, "EFECTIVO"));
        lista.add(pago(22, null));           // sin forma guardada: se usa el método (TARJETA)
        when(pagos.findAll()).thenReturn(lista);

        PagoLaboratorio lab = new PagoLaboratorio();
        lab.setFormaPago("VISA");
        OrdenLaboratorio orden = new OrdenLaboratorio();
        orden.setCita(lista.get(0).getCita());
        lab.setOrden(orden);
        when(pagosLab.findAll()).thenReturn(List.of(lab));

        List<Map<String, Object>> metodos = (List<Map<String, Object>>) datos().get("metodosDePago");
        Map<String, Map<String, Object>> porForma = new java.util.HashMap<>();
        metodos.forEach(m -> porForma.put((String) m.get("forma"), m));

        assertEquals(12, porForma.get("VISA").get("pagos"));          // 10 + 1 repetido + 1 de laboratorio
        assertEquals(10, porForma.get("VISA").get("pacientes"));      // pero solo 10 pacientes distintos
        assertEquals(1, porForma.get("MASTERCARD").get("pagos"));
        assertEquals(1, porForma.get("EFECTIVO").get("pagos"));
        assertEquals(0, porForma.get("DEBITO").get("pagos"));
        assertEquals(1, porForma.get("TARJETA").get("pagos"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cambiarLosRegistrosCambiaLaGrafica() {
        List<Pago> lista = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            lista.add(pago(i, "VISA"));
        }
        when(pagos.findAll()).thenReturn(lista);
        assertEquals(10, visa((List<Map<String, Object>>) datos().get("metodosDePago")));

        // el docente pide 15: se cambian 5 pagos más a Visa en la base
        for (int i = 11; i <= 15; i++) {
            lista.add(pago(i, "VISA"));
        }
        assertEquals(15, visa((List<Map<String, Object>>) datos().get("metodosDePago")));
    }

    private Object visa(List<Map<String, Object>> metodos) {
        return metodos.stream().filter(m -> "VISA".equals(m.get("forma"))).findFirst().orElseThrow().get("pagos");
    }
}
