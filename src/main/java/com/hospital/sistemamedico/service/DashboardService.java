package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.DespachoRepository;
import com.hospital.sistemamedico.repository.PagoLaboratorioRepository;
import com.hospital.sistemamedico.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Datos del dashboard de gráficas del administrador. Todo se calcula en cada
 * petición directamente con lo que hay en la base de datos: si los registros
 * cambian, la gráfica cambia en la siguiente actualización.
 */
@Service
public class DashboardService {

    /** Citas en las que el médico ya atendió (o está atendiendo) al paciente. */
    private static final Set<EstadoCita> ESTADOS_CON_CONSULTA = EnumSet.of(
            EstadoCita.EN_CONSULTA, EstadoCita.EVALUADO_PENDIENTE_CIERRE,
            EstadoCita.ATENCION_FINALIZADA, EstadoCita.COMPLETADA);

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private DespachoRepository despachoRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PagoLaboratorioRepository pagoLaboratorioRepository;

    /**
     * Arma las tres gráficas.
     *
     * @param adminId usuario administrador que consulta
     * @param diasDemanda cuántos "días de mayor demanda" considerar para la gráfica de médicos (1 a 10)
     * @param limiteMedicamentos cuántos medicamentos mostrar (1 a 20)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Long adminId, int diasDemanda, int limiteMedicamentos) {
        Usuario admin = usuarioService.buscarPorId(adminId);
        if (admin.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("Solo el administrador puede ver el dashboard.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medicosDiasDemanda", medicosEnDiasDeMayorDemanda(Math.max(1, Math.min(diasDemanda, 10))));
        m.put("medicamentosDespachados", medicamentosMasDespachados(Math.max(1, Math.min(limiteMedicamentos, 20))));
        m.put("metodosDePago", metodosDePago());
        m.put("generado", java.time.LocalDateTime.now().toString());
        return m;
    }

    /**
     * Gráfica 1: los días con más consultas y, dentro de ellos, los médicos con más consultas.
     * Una consulta es una cita en estado En Consulta, Evaluado, Atención Finalizada o Completada.
     */
    private Map<String, Object> medicosEnDiasDeMayorDemanda(int cantidadDias) {
        List<Cita> consultas = citaRepository.findAll().stream()
                .filter(c -> ESTADOS_CON_CONSULTA.contains(c.getEstado()))
                .collect(Collectors.toList());

        Map<LocalDate, Long> porDia = consultas.stream()
                .collect(Collectors.groupingBy(c -> c.getFechaHora().toLocalDate(), Collectors.counting()));
        List<LocalDate> diasPico = porDia.entrySet().stream()
                .sorted((a, b) -> {
                    int porCantidad = Long.compare(b.getValue(), a.getValue());
                    return porCantidad != 0 ? porCantidad : b.getKey().compareTo(a.getKey());
                })
                .limit(cantidadDias)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, List<Cita>> porMedico = consultas.stream()
                .filter(c -> diasPico.contains(c.getFechaHora().toLocalDate()))
                .collect(Collectors.groupingBy(c -> c.getMedico().getId()));

        List<Map<String, Object>> medicos = porMedico.values().stream().map(lista -> {
            Usuario medico = lista.get(0).getMedico();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("medico", medico.getNombreCompleto());
            f.put("especialidad", medico.getEspecialidad() == null ? null : medico.getEspecialidad().getNombre());
            f.put("consultas", lista.size());
            return f;
        }).sorted((a, b) -> Integer.compare((int) b.get("consultas"), (int) a.get("consultas")))
                .limit(10).collect(Collectors.toList());

        List<Map<String, Object>> dias = diasPico.stream().map(d -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("fecha", d.toString());
            f.put("consultas", porDia.get(d));
            return f;
        }).collect(Collectors.toList());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("diasDeMayorDemanda", dias);
        r.put("medicos", medicos);
        return r;
    }

    /** Gráfica 2: medicamentos con más unidades despachadas (despachos registrados). */
    private List<Map<String, Object>> medicamentosMasDespachados(int limite) {
        return despachoRepository.unidadesDespachadasPorMedicamento().stream().limit(limite).map(fila -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("medicamento", fila[0]);
            f.put("unidades", ((Number) fila[1]).longValue());
            return f;
        }).collect(Collectors.toList());
    }

    /**
     * Gráfica 3: cuántos pagos (y a cuántos pacientes distintos) por forma de pago. Cuenta los pagos de
     * citas (en línea y en caja) y los de laboratorio. Las formas son las del proyecto: efectivo, Visa,
     * Mastercard y débito; "TARJETA" son pagos en línea anteriores a que se guardara la marca.
     */
    private List<Map<String, Object>> metodosDePago() {
        Map<String, Integer> pagos = new LinkedHashMap<>();
        Map<String, Set<Long>> pacientes = new LinkedHashMap<>();
        for (String forma : List.of("EFECTIVO", "VISA", "MASTERCARD", "DEBITO", "TARJETA")) {
            pagos.put(forma, 0);
            pacientes.put(forma, new HashSet<>());
        }
        for (Pago p : pagoRepository.findAll()) {
            String forma = p.getFormaPago() != null ? p.getFormaPago() : p.getMetodoPago().name();
            contar(pagos, pacientes, forma, p.getCita().getPaciente().getId());
        }
        for (PagoLaboratorio p : pagoLaboratorioRepository.findAll()) {
            contar(pagos, pacientes, p.getFormaPago(), p.getOrden().getCita().getPaciente().getId());
        }
        List<Map<String, Object>> r = new ArrayList<>();
        pagos.forEach((forma, cantidad) -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("forma", forma);
            f.put("etiqueta", etiqueta(forma));
            f.put("pagos", cantidad);
            f.put("pacientes", pacientes.get(forma).size());
            r.add(f);
        });
        return r;
    }

    private void contar(Map<String, Integer> pagos, Map<String, Set<Long>> pacientes, String forma, Long pacienteId) {
        String clave = forma == null ? "TARJETA" : forma.trim().toUpperCase();
        pagos.merge(clave, 1, Integer::sum);
        pacientes.computeIfAbsent(clave, k -> new HashSet<>()).add(pacienteId);
    }

    private String etiqueta(String forma) {
        return switch (forma) {
            case "EFECTIVO" -> "Efectivo";
            case "VISA" -> "Visa";
            case "MASTERCARD" -> "Mastercard";
            case "DEBITO" -> "Débito";
            case "TARJETA" -> "Tarjeta en línea (sin marca)";
            default -> forma;
        };
    }
}
