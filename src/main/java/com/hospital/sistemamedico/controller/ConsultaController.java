package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.SignosVitalesRepository;
import com.hospital.sistemamedico.service.CitaService;
import com.hospital.sistemamedico.service.ConsultaService;
import com.hospital.sistemamedico.service.LaboratorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Endpoints de la consulta médica (CU-08): panel del médico, inicio de
 * consulta, registro de la consulta, órdenes de laboratorio, recetas, no
 * asistió, cierre de la atención y catálogos de apoyo (CIE-10, exámenes,
 * medicamentos). Las respuestas se arman como mapas para no exponer datos
 * sensibles de los usuarios (contraseñas) al frontend.
 */
@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    @Autowired
    private ConsultaService consultaService;

    @Autowired
    private CitaService citaService;

    @Autowired
    private LaboratorioService laboratorioService;

    @Autowired
    private SignosVitalesRepository signosVitalesRepository;

    /**
     * Panel del médico (paso 1): sus citas agrupadas en "En Espera de Consulta",
     * "En Consulta Médica" y "Evaluados - Pendiente de cierre". Las emergencias
     * aparecen primero en cada sección.
     */
    @GetMapping("/panel/{medicoId}")
    public Map<String, Object> panel(@PathVariable Long medicoId) {
        List<Cita> citas = citaService.listarPorMedico(medicoId);
        Comparator<Cita> orden = Comparator.comparing(Cita::isEmergencia).reversed()
                .thenComparing(Cita::getFechaHora);

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("enEspera", citas.stream().filter(citaService::estaEnEsperaDeConsulta)
                .sorted(orden).map(this::resumenCita).collect(Collectors.toList()));
        panel.put("enConsulta", citas.stream().filter(c -> c.getEstado() == EstadoCita.EN_CONSULTA)
                .sorted(orden).map(this::resumenCita).collect(Collectors.toList()));
        panel.put("evaluados", citas.stream().filter(c -> c.getEstado() == EstadoCita.EVALUADO_PENDIENTE_CIERRE)
                .sorted(orden).map(this::resumenCita).collect(Collectors.toList()));
        return panel;
    }

    /** Paso 2: "Iniciar Consulta". Devuelve el texto que el frontend anuncia por voz (TTS). */
    @PutMapping("/iniciar/{citaId}")
    public ResponseEntity<?> iniciar(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            Cita cita = citaService.iniciarConsulta(citaId, idMedico(datos));
            Map<String, Object> respuesta = resumenCita(cita);
            respuesta.put("anuncio", "Turno número " + cita.getId() + ". Paciente "
                    + cita.getPaciente().getNombreCompleto() + ", favor pasar a consulta médica.");
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA06: el paciente no asistió cuando fue llamado a consulta. */
    @PutMapping("/no-asistio/{citaId}")
    public ResponseEntity<?> noAsistio(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            Cita cita = citaService.marcarNoAsistio(citaId, idMedico(datos));
            Map<String, Object> respuesta = resumenCita(cita);
            respuesta.put("mensaje", "Cita #" + cita.getId() + " marcada como No Asistió.");
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /**
     * Contexto del formulario de consulta (paso 3): datos de la cita, signos
     * vitales, consulta ya guardada (si existe), órdenes y recetas generadas
     * y el historial de consultas anteriores del paciente.
     */
    @GetMapping("/cita/{citaId}")
    public ResponseEntity<?> contexto(@PathVariable Long citaId) {
        try {
            Cita cita = citaService.buscarPorId(citaId);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("cita", resumenCita(cita));
            respuesta.put("signosVitales", signosVitalesRepository.findByCitaId(citaId).map(this::mapaSignos).orElse(null));
            Consulta consulta = consultaService.buscarPorCita(citaId);
            respuesta.put("consulta", consulta == null ? null : mapaConsulta(consulta));
            respuesta.put("ordenes", consultaService.ordenesDeCita(citaId).stream().map(this::mapaOrden).collect(Collectors.toList()));
            respuesta.put("recetas", consultaService.recetasDeCita(citaId).stream().map(this::mapaReceta).collect(Collectors.toList()));
            respuesta.put("historial", consultaService.historialDelPaciente(cita.getPaciente().getId()).stream()
                    .filter(c -> !c.getCita().getId().equals(citaId)).map(this::mapaConsulta).collect(Collectors.toList()));
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Pasos 4 a 9: guarda la consulta; con "finalizada": true la cierra y la cita pasa a Evaluados. */
    @PutMapping("/cita/{citaId}")
    public ResponseEntity<?> guardar(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            boolean finalizar = datos.get("finalizada") != null && Boolean.parseBoolean(datos.get("finalizada").toString());
            Consulta consulta = consultaService.guardar(citaId, idMedico(datos), datos, finalizar);
            Map<String, Object> respuesta = mapaConsulta(consulta);
            respuesta.put("mensaje", finalizar
                    ? "La consulta ha sido finalizada exitosamente. El paciente puede proceder a las siguientes indicaciones médicas."
                    : "La consulta se guardó como En curso.");
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA01: genera una orden de laboratorio. */
    @PostMapping("/cita/{citaId}/ordenes-laboratorio")
    public ResponseEntity<?> generarOrden(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            List<Long> examenIds = new ArrayList<>();
            if (datos.get("examenIds") instanceof List<?> lista) {
                for (Object id : lista) {
                    examenIds.add(Long.valueOf(id.toString()));
                }
            }
            OrdenLaboratorio orden = consultaService.generarOrdenLaboratorio(citaId, idMedico(datos), examenIds,
                    datos.get("observaciones") != null ? datos.get("observaciones").toString() : null,
                    datos.get("externa") != null && Boolean.parseBoolean(datos.get("externa").toString()));
            Map<String, Object> respuesta = mapaOrden(orden);
            respuesta.put("mensaje", "Orden de laboratorio generada exitosamente. Número de orden: " + orden.getNumeroOrden()
                    + ". Exámenes: " + nombresExamenes(orden) + ". El paciente debe dirigirse al área de laboratorio.");
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA04: genera una receta médica. */
    @PostMapping("/cita/{citaId}/recetas")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> generarReceta(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            if (datos.get("items") instanceof List<?> lista) {
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> mapa) {
                        items.add((Map<String, Object>) mapa);
                    }
                }
            }
            Receta receta = consultaService.generarReceta(citaId, idMedico(datos), items);
            Map<String, Object> respuesta = mapaReceta(receta);
            String lista = receta.getItems().stream().map(i -> i.getMedicamento().getNombre()).collect(Collectors.joining(", "));
            respuesta.put("mensaje", "Receta médica generada exitosamente. Medicamentos: " + lista
                    + ". El paciente puede adquirirlos en la farmacia de la clínica.");
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 11 y 12: "Finalizar Atención". */
    @PutMapping("/finalizar-atencion/{citaId}")
    public ResponseEntity<?> finalizarAtencion(@PathVariable Long citaId, @RequestBody Map<String, Object> datos) {
        try {
            Cita cita = citaService.finalizarAtencion(citaId, idMedico(datos));
            Map<String, Object> respuesta = resumenCita(cita);
            respuesta.put("mensaje", "Atención finalizada para cita #" + cita.getId() + ".");
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Historial clínico: consultas finalizadas de un paciente. */
    @GetMapping("/paciente/{pacienteId}/historial")
    public List<Map<String, Object>> historial(@PathVariable Long pacienteId) {
        return consultaService.historialDelPaciente(pacienteId).stream().map(this::mapaConsulta).collect(Collectors.toList());
    }

    /** Autocompletado CIE-10 (RNF-004). */
    @GetMapping("/cie10")
    public List<Cie10> buscarCie10(@RequestParam("q") String q) {
        return consultaService.buscarCie10(q);
    }

    @GetMapping("/examenes")
    public List<Examen> examenes() {
        return consultaService.listarExamenesActivos();
    }

    @GetMapping("/medicamentos")
    public List<Medicamento> medicamentos() {
        return consultaService.listarMedicamentosActivos();
    }

    // ---------------------------------------------------------------- helpers

    private Long idMedico(Map<String, Object> datos) {
        if (datos.get("medicoId") == null) {
            throw new IllegalArgumentException("Debe indicar el médico.");
        }
        return Long.valueOf(datos.get("medicoId").toString());
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private Map<String, Object> resumenCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("pacienteId", c.getPaciente().getId());
        m.put("paciente", c.getPaciente().getNombreCompleto());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("sucursal", c.getSucursal().getNombre());
        m.put("fechaHora", c.getFechaHora().toString());
        m.put("estado", c.getEstado().name());
        m.put("emergencia", c.isEmergencia());
        m.put("motivoCita", c.getMotivoConsulta());
        m.put("esSeguimiento", c.getCitaOrigenId() != null);
        return m;
    }

    private Map<String, Object> mapaSignos(SignosVitales s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presionSistolica", s.getPresionSistolica());
        m.put("presionDiastolica", s.getPresionDiastolica());
        m.put("temperatura", s.getTemperatura());
        m.put("peso", s.getPeso());
        m.put("talla", s.getTalla());
        m.put("frecuenciaCardiaca", s.getFrecuenciaCardiaca());
        m.put("emergencia", s.isEmergencia());
        m.put("fechaRegistro", s.getFechaRegistro().toString());
        return m;
    }

    private Map<String, Object> mapaConsulta(Consulta c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("citaId", c.getCita().getId());
        m.put("medico", c.getMedico().getNombreCompleto());
        m.put("especialidad", c.getCita().getEspecialidad().getNombre());
        m.put("motivoVisita", c.getMotivoVisita());
        m.put("hallazgosClinicos", c.getHallazgosClinicos());
        m.put("codigoCie10", c.getCodigoCie10());
        m.put("diagnostico", c.getDiagnostico());
        m.put("planTratamiento", c.getPlanTratamiento());
        m.put("notasAdicionales", c.getNotasAdicionales());
        m.put("finalizada", c.isFinalizada());
        m.put("version", c.getVersion());
        m.put("fechaFinalizacion", c.getFechaFinalizacion() == null ? null : c.getFechaFinalizacion().toString());
        return m;
    }

    private Map<String, Object> mapaOrden(OrdenLaboratorio o) {
        // El médico ve solo los resultados que el laboratorio ya publicó
        return laboratorioService.aMapa(o, true);
    }

    private Map<String, Object> mapaReceta(Receta r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("fecha", r.getFecha().toString());
        m.put("items", r.getItems().stream().map(i -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("medicamento", i.getMedicamento().getNombre());
            item.put("dosis", i.getDosis());
            item.put("frecuencia", i.getFrecuencia());
            item.put("duracion", i.getDuracion());
            item.put("indicaciones", i.getIndicaciones());
            return item;
        }).collect(Collectors.toList()));
        return m;
    }

    private String nombresExamenes(OrdenLaboratorio o) {
        return o.getItems().stream().map(i -> i.getExamen().getNombre()).collect(Collectors.joining(", "));
    }
}
