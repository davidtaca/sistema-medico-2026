package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import com.hospital.sistemamedico.repository.EventoAgendaRepository;
import com.hospital.sistemamedico.repository.TareaAgendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio de la agenda médica (CU-14): calendario
 * con las citas del médico y sus eventos personales, y tareas del panel
 * lateral. Cada médico solo ve y modifica su propia agenda. Los eventos hacen
 * que el médico no esté disponible en esos horarios (lo aplican CitaService
 * al agendar y al ofrecer horarios de seguimiento).
 */
@Service
public class AgendaService {

    /** Color con el que se muestran todos los eventos (no es configurable, RN-CU14-01). */
    public static final String COLOR_EVENTO = "#8b5cf6";

    /** Citas que se muestran en el calendario: se excluyen Pendiente de pago, No asistió y Cancelada. */
    private static final Set<EstadoCita> ESTADOS_OCULTOS = Set.of(EstadoCita.PENDIENTE_PAGO, EstadoCita.NO_ASISTIO, EstadoCita.CANCELADA);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private EventoAgendaRepository eventoRepository;

    @Autowired
    private TareaAgendaRepository tareaRepository;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private UsuarioService usuarioService;

    // ------------------------------------------------------------ calendario

    /**
     * Citas y eventos del médico en un rango (paso 2).
     *
     * @param medicoId id del médico
     * @param desde inicio del rango (inclusive)
     * @param hasta fin del rango (exclusivo)
     * @return mapa con "citas" (sin las pendientes de pago, no asistidas ni canceladas) y "eventos"
     */
    public Map<String, Object> agenda(Long medicoId, LocalDateTime desde, LocalDateTime hasta) {
        validarMedico(medicoId);
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new IllegalArgumentException("El rango de fechas indicado no es válido.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("citas", citaRepository.findByMedicoIdAndFechaHoraBetween(medicoId, desde, hasta).stream()
                .filter(c -> !ESTADOS_OCULTOS.contains(c.getEstado()))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .map(this::mapaCita).collect(Collectors.toList()));
        m.put("eventos", eventoRepository.findByMedicoIdAndInicioLessThanAndFinGreaterThanOrderByInicio(medicoId, hasta, desde).stream()
                .map(this::mapaEvento).collect(Collectors.toList()));
        return m;
    }

    // ------------------------------------------------------------ eventos

    /**
     * Crea un evento en la agenda (pasos 3 a 8).
     *
     * @param datos titulo, descripcion, inicio, fin, tipo y todoElDia. Con todoElDia las fechas
     *        son solo fecha (AAAA-MM-DD) y el evento cubre desde el inicio del primer día hasta el
     *        final del último; si no, son fecha y hora (AAAA-MM-DDTHH:MM)
     * @return mapa con el evento creado y un mensaje de éxito
     * @throws IllegalArgumentException si algún dato incumple RN-CU14-01
     */
    public Map<String, Object> crearEvento(Long medicoId, Map<String, Object> datos) {
        Usuario medico = validarMedico(medicoId);
        EventoAgenda evento = new EventoAgenda();
        evento.setMedico(medico);
        aplicarDatosEvento(evento, datos);
        EventoAgenda guardado = eventoRepository.save(evento);

        Map<String, Object> r = new LinkedHashMap<>(mapaEvento(guardado));
        r.put("mensaje", "Evento creado exitosamente. " + guardado.getTitulo() + " — "
                + FORMATO_FECHA.format(guardado.getInicio()) + " a " + FORMATO_FECHA.format(guardado.getFin()) + ".");
        long citas = citaRepository.findByMedicoIdAndFechaHoraBetween(medicoId, guardado.getInicio(), guardado.getFin()).stream()
                .filter(c -> !ESTADOS_OCULTOS.contains(c.getEstado())).count();
        r.put("citasEnConflicto", citas);
        return r;
    }

    /**
     * Edita un evento existente (FA07).
     *
     * @throws IllegalArgumentException si el evento no existe, es de otro médico o los datos no son válidos
     */
    public Map<String, Object> actualizarEvento(Long medicoId, Long eventoId, Map<String, Object> datos) {
        validarMedico(medicoId);
        EventoAgenda evento = eventoDelMedico(medicoId, eventoId);
        aplicarDatosEvento(evento, datos);
        EventoAgenda guardado = eventoRepository.save(evento);
        Map<String, Object> r = new LinkedHashMap<>(mapaEvento(guardado));
        r.put("mensaje", "Evento actualizado exitosamente.");
        return r;
    }

    /**
     * Elimina un evento (FA04). La confirmación se pide en la pantalla.
     *
     * @throws IllegalArgumentException si el evento no existe o es de otro médico
     */
    public void eliminarEvento(Long medicoId, Long eventoId) {
        validarMedico(medicoId);
        eventoRepository.delete(eventoDelMedico(medicoId, eventoId));
    }

    /**
     * true si el médico tiene algún evento que cubra ese instante: en ese momento no puede recibir citas.
     */
    public boolean medicoOcupadoPorEvento(Long medicoId, LocalDateTime momento) {
        return !eventoRepository.findByMedicoIdAndInicioLessThanEqualAndFinGreaterThan(medicoId, momento, momento).isEmpty();
    }

    // ------------------------------------------------------------ tareas

    /**
     * Tareas del médico (FA06).
     *
     * @param fecha si se indica, solo las tareas con esa fecha límite
     * @param estado "PENDIENTES", "COMPLETADAS" o vacío/"TODAS"
     */
    public List<Map<String, Object>> tareas(Long medicoId, LocalDate fecha, String estado) {
        validarMedico(medicoId);
        String filtro = estado == null ? "" : estado.trim().toUpperCase();
        if (!filtro.isEmpty() && !Set.of("PENDIENTES", "COMPLETADAS", "TODAS").contains(filtro)) {
            throw new IllegalArgumentException("El filtro de estado no es válido.");
        }
        return tareaRepository.findByMedicoIdOrderByFechaLimiteAscIdAsc(medicoId).stream()
                .filter(t -> fecha == null || t.getFechaLimite().equals(fecha))
                .filter(t -> !filtro.equals("PENDIENTES") || !t.isCompletada())
                .filter(t -> !filtro.equals("COMPLETADAS") || t.isCompletada())
                .map(this::mapaTarea).collect(Collectors.toList());
    }

    /**
     * Crea una tarea (RN-CU14-02).
     *
     * @param datos titulo, descripcion (opcional, máx. 1000), prioridad (BAJA, NORMAL o ALTA) y
     *        fechaLimite (AAAA-MM-DD, obligatoria)
     */
    public Map<String, Object> crearTarea(Long medicoId, Map<String, Object> datos) {
        Usuario medico = validarMedico(medicoId);
        String titulo = texto(datos.get("titulo"));
        String descripcion = texto(datos.get("descripcion"));
        validarTitulo(titulo);
        if (descripcion.length() > 1000) {
            throw new IllegalArgumentException("La descripción no puede exceder 1000 caracteres.");
        }
        PrioridadTarea prioridad;
        try {
            prioridad = PrioridadTarea.valueOf(texto(datos.get("prioridad")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Debe seleccionar la prioridad de la tarea.");
        }
        String fecha = texto(datos.get("fechaLimite"));
        if (fecha.isEmpty()) {
            throw new IllegalArgumentException("La fecha límite es obligatoria.");
        }
        LocalDate fechaLimite;
        try {
            fechaLimite = LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha límite no es válida.");
        }

        TareaAgenda t = new TareaAgenda();
        t.setMedico(medico);
        t.setTitulo(titulo);
        t.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        t.setPrioridad(prioridad);
        t.setFechaLimite(fechaLimite);
        return mapaTarea(tareaRepository.save(t));
    }

    /** Marca una tarea como completada o la devuelve a pendiente. */
    public Map<String, Object> completarTarea(Long medicoId, Long tareaId, boolean completada) {
        validarMedico(medicoId);
        TareaAgenda t = tareaDelMedico(medicoId, tareaId);
        t.setCompletada(completada);
        return mapaTarea(tareaRepository.save(t));
    }

    public void eliminarTarea(Long medicoId, Long tareaId) {
        validarMedico(medicoId);
        tareaRepository.delete(tareaDelMedico(medicoId, tareaId));
    }

    // ------------------------------------------------------------ helpers

    /** Valida y copia los datos del formulario de evento (RN-CU14-01). */
    private void aplicarDatosEvento(EventoAgenda evento, Map<String, Object> datos) {
        String titulo = texto(datos.get("titulo"));
        String descripcion = texto(datos.get("descripcion"));
        boolean todoElDia = datos.get("todoElDia") != null && Boolean.parseBoolean(datos.get("todoElDia").toString());

        validarTitulo(titulo);
        if (descripcion.length() > 500) {
            throw new IllegalArgumentException("La descripción no puede exceder 500 caracteres.");
        }
        TipoEvento tipo;
        try {
            tipo = TipoEvento.valueOf(texto(datos.get("tipo")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de evento.");
        }

        String textoInicio = texto(datos.get("inicio"));
        String textoFin = texto(datos.get("fin"));
        if (textoInicio.isEmpty()) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }
        if (textoFin.isEmpty()) {
            throw new IllegalArgumentException("La fecha de fin es obligatoria.");
        }
        LocalDateTime inicio;
        LocalDateTime fin;
        try {
            if (todoElDia) {
                // Solo fecha: cubre desde el inicio del primer día hasta el final del último
                LocalDate dia1 = LocalDate.parse(textoInicio.length() > 10 ? textoInicio.substring(0, 10) : textoInicio);
                LocalDate dia2 = LocalDate.parse(textoFin.length() > 10 ? textoFin.substring(0, 10) : textoFin);
                inicio = dia1.atStartOfDay();
                fin = dia2.atTime(LocalTime.of(23, 59, 59));
                if (dia2.isBefore(dia1)) {
                    throw new IllegalArgumentException("La fecha de fin debe ser posterior a la fecha de inicio.");
                }
            } else {
                inicio = LocalDateTime.parse(textoInicio);
                fin = LocalDateTime.parse(textoFin);
                if (!fin.isAfter(inicio)) {
                    throw new IllegalArgumentException("La fecha de fin debe ser posterior a la fecha de inicio.");
                }
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Las fechas del evento no son válidas.");
        }

        evento.setTitulo(titulo);
        evento.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        evento.setTipo(tipo);
        evento.setTodoElDia(todoElDia);
        evento.setInicio(inicio);
        evento.setFin(fin);
    }

    private void validarTitulo(String titulo) {
        if (titulo.length() < 5 || titulo.length() > 200) {
            throw new IllegalArgumentException("El título es obligatorio. Debe contener entre 5 y 200 caracteres.");
        }
    }

    private Usuario validarMedico(Long medicoId) {
        if (medicoId == null) {
            throw new IllegalArgumentException("Debe indicar el médico.");
        }
        Usuario u = usuarioService.buscarPorId(medicoId);
        if (u.getRol() != Rol.MEDICO) {
            throw new IllegalArgumentException("Solo un médico tiene agenda médica.");
        }
        return u;
    }

    private EventoAgenda eventoDelMedico(Long medicoId, Long eventoId) {
        EventoAgenda e = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("El evento no existe."));
        if (!e.getMedico().getId().equals(medicoId)) {
            throw new IllegalArgumentException("El evento pertenece a la agenda de otro médico.");
        }
        return e;
    }

    private TareaAgenda tareaDelMedico(Long medicoId, Long tareaId) {
        TareaAgenda t = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("La tarea no existe."));
        if (!t.getMedico().getId().equals(medicoId)) {
            throw new IllegalArgumentException("La tarea pertenece a otro médico.");
        }
        return t;
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString().trim();
    }

    private Map<String, Object> mapaEvento(EventoAgenda e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("titulo", e.getTitulo());
        m.put("descripcion", e.getDescripcion());
        m.put("inicio", e.getInicio().toString());
        m.put("fin", e.getFin().toString());
        m.put("tipo", e.getTipo().name());
        m.put("tipoEtiqueta", e.getTipo().getEtiqueta());
        m.put("todoElDia", e.isTodoElDia());
        m.put("color", COLOR_EVENTO);
        return m;
    }

    private Map<String, Object> mapaCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("fechaHora", c.getFechaHora().toString());
        m.put("paciente", c.getPaciente().getNombreCompleto());
        m.put("estado", c.getEstado().name());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("sucursal", c.getSucursal().getNombre());
        m.put("motivo", c.getMotivoConsulta());
        m.put("emergencia", c.isEmergencia());
        m.put("seguimiento", c.getTipoSeguimiento() != null ? c.getTipoSeguimiento().getEtiqueta() : null);
        return m;
    }

    private Map<String, Object> mapaTarea(TareaAgenda t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("titulo", t.getTitulo());
        m.put("descripcion", t.getDescripcion());
        m.put("prioridad", t.getPrioridad().name());
        m.put("prioridadEtiqueta", t.getPrioridad().getEtiqueta());
        m.put("fechaLimite", t.getFechaLimite().toString());
        m.put("completada", t.isCompletada());
        m.put("estado", t.isCompletada() ? "Completada" : "Pendiente");
        return m;
    }
}
