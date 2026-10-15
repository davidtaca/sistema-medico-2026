package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Especialidad;
import com.hospital.sistemamedico.model.SedeEspecialidad;
import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import com.hospital.sistemamedico.repository.ExamenRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Información pública del portal web (CU-00, paso 2): datos del hospital,
 * servicios, especialidades, ubicaciones y horarios de atención. Sale de los
 * catálogos del sistema (sedes y especialidades asignadas a cada sede) y se
 * mantiene precargada en memoria (precondición "catálogos precargados en
 * caché", RNF-005 y RNF-006): se arma al iniciar la aplicación, se renueva
 * cada minuto y se descarta apenas un administrador cambia un catálogo.
 */
@Service
public class PortalService {

    /** Tiempo máximo que se reutiliza la información precargada. */
    private static final long VIGENCIA_MS = 60_000;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

    @Value("${app.hospital.nombre:Hospital Sistema Médico}")
    private String nombreHospital;

    @Value("${app.hospital.descripcion:Atención médica integral: consulta con especialistas, laboratorio clínico y farmacia en un solo lugar.}")
    private String descripcionHospital;

    @Value("${app.hospital.telefono:}")
    private String telefonoHospital;

    /** Texto opcional para sobreescribir el horario de atención desde application.properties. */
    @Value("${app.hospital.horario:}")
    private String horarioConfigurado;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private SedeEspecialidadRepository sedeEspecialidadRepository;

    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    private volatile Map<String, Object> cache;
    private volatile long cargadoEn;

    /** Precarga la información del portal al terminar de iniciar la aplicación. */
    @EventListener(ApplicationReadyEvent.class)
    public void precargar() {
        invalidar();
        informacion();
    }

    /** Descarta la información guardada: se vuelve a armar en la siguiente consulta. */
    public void invalidar() {
        cache = null;
    }

    /** Información del portal, tomada de la memoria mientras siga vigente. */
    public Map<String, Object> informacion() {
        Map<String, Object> actual = cache;
        if (actual == null || System.currentTimeMillis() - cargadoEn > VIGENCIA_MS) {
            actual = construir();
            cache = actual;
            cargadoEn = System.currentTimeMillis();
        }
        return actual;
    }

    private Map<String, Object> construir() {
        List<Sucursal> sedes = sucursalRepository.findByActivoTrue().stream()
                .sorted(Comparator.comparing(Sucursal::getNombre, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
        List<SedeEspecialidad> asignaciones = sedeEspecialidadRepository.findAll().stream()
                .filter(a -> a.isActivo() && a.getEspecialidad().isActivo() && a.getSucursal().isActivo()).collect(Collectors.toList());

        // Especialidades por sede y sedes por especialidad
        Map<Long, List<Especialidad>> porSede = new HashMap<>();
        Map<Long, List<String>> sedesPorEspecialidad = new HashMap<>();
        for (SedeEspecialidad a : asignaciones) {
            porSede.computeIfAbsent(a.getSucursal().getId(), k -> new ArrayList<>()).add(a.getEspecialidad());
            sedesPorEspecialidad.computeIfAbsent(a.getEspecialidad().getId(), k -> new ArrayList<>()).add(a.getSucursal().getNombre());
        }

        List<Map<String, Object>> listaSedes = new ArrayList<>();
        for (Sucursal s : sedes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("nombre", s.getNombre());
            m.put("direccion", s.getDireccion());
            m.put("telefono", s.getTelefono());
            m.put("descripcion", s.getDescripcion());
            m.put("especialidades", porSede.getOrDefault(s.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(Especialidad::getNombre, String.CASE_INSENSITIVE_ORDER))
                    .map(Especialidad::getNombre).collect(Collectors.toList()));
            listaSedes.add(m);
        }

        List<Map<String, Object>> listaEspecialidades = especialidadRepository.findByActivoTrue().stream()
                .filter(e -> sedesPorEspecialidad.containsKey(e.getId()))
                .sorted(Comparator.comparing(Especialidad::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("nombre", e.getNombre());
                    m.put("descripcion", e.getDescripcion());
                    List<String> donde = new ArrayList<>(sedesPorEspecialidad.get(e.getId()));
                    Collections.sort(donde, String.CASE_INSENSITIVE_ORDER);
                    m.put("sedes", donde);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("nombre", nombreHospital);
        hospital.put("descripcion", descripcionHospital);
        hospital.put("telefono", telefonoHospital == null || telefonoHospital.isBlank() ? null : telefonoHospital);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("hospital", hospital);
        r.put("servicios", servicios(listaEspecialidades.size(), listaSedes.size()));
        r.put("especialidades", listaEspecialidades);
        r.put("sedes", listaSedes);
        r.put("horarios", horarios());
        return r;
    }

    /** Servicios del hospital, con datos tomados de los catálogos (cantidad de especialidades, exámenes, etc.). */
    private List<Map<String, Object>> servicios(int especialidades, int sedes) {
        int examenes = examenRepository.findByActivoTrueOrderByNombre().size();
        int medicamentos = medicamentoRepository.findByActivoTrueOrderByNombre().size();
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(servicio("🩺", "Consulta médica",
                "Atención con " + especialidades + " especialidad(es) médica(s) en " + sedes + " sede(s), con toma de signos vitales antes de la consulta."));
        l.add(servicio("🧪", "Laboratorio clínico",
                examenes + " examen(es) de laboratorio disponibles, con resultados que su médico consulta directamente en el sistema."));
        l.add(servicio("💊", "Farmacia",
                "Despacho de los medicamentos de su receta (" + medicamentos + " medicamento(s) en catálogo), con control de existencias."));
        l.add(servicio("📅", "Citas en línea",
                "Agende, pague y consulte sus citas desde el portal, y reciba su confirmación y recordatorios por correo."));
        l.add(servicio("🔁", "Seguimiento médico",
                "Citas de seguimiento para monitorear su tratamiento o revisar sus resultados de laboratorio."));
        l.add(servicio("🚨", "Atención prioritaria",
                "Si su caso es una emergencia, indíquelo al agendar y será atendido con prioridad."));
        return l;
    }

    private Map<String, Object> servicio(String icono, String titulo, String descripcion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("icono", icono);
        m.put("titulo", titulo);
        m.put("descripcion", descripcion);
        return m;
    }

    /**
     * Horarios de atención. Por defecto salen de los horarios que el sistema ofrece
     * para agendar citas; se pueden reemplazar con app.hospital.horario.
     */
    private List<Map<String, String>> horarios() {
        List<Map<String, String>> l = new ArrayList<>();
        if (horarioConfigurado != null && !horarioConfigurado.isBlank()) {
            l.add(Map.of("servicio", "Atención al paciente", "horario", horarioConfigurado.trim()));
        } else {
            l.add(Map.of("servicio", "Consulta externa",
                    "horario", "Todos los días, de " + CitaService.PRIMERA_HORA.format(HORA) + " a "
                            + CitaService.ULTIMA_HORA.plusMinutes(30).format(HORA) + " (citas cada 30 minutos)"));
        }
        l.add(Map.of("servicio", "Agendamiento de citas en línea", "horario", "Las 24 horas, todos los días"));
        return l;
    }
}
