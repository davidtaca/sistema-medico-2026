package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.AgendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de la agenda médica (CU-14): calendario con citas y eventos,
 * gestión de eventos personales y tareas del panel lateral. Todas las
 * operaciones reciben el id del médico dueño de la agenda.
 */
@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    @Autowired
    private AgendaService agendaService;

    /** Paso 2: citas (sin pendientes, no asistidas ni canceladas) y eventos del médico en un rango. */
    @GetMapping("/{medicoId}")
    public ResponseEntity<?> agenda(@PathVariable Long medicoId, @RequestParam String desde, @RequestParam String hasta) {
        try {
            return ResponseEntity.ok(agendaService.agenda(medicoId, fechaHora(desde), fechaHora(hasta)));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 3 a 8: crea un evento. */
    @PostMapping("/eventos")
    public ResponseEntity<?> crearEvento(@RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(agendaService.crearEvento(medicoId(datos), datos));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA07: edita un evento. */
    @PutMapping("/eventos/{id}")
    public ResponseEntity<?> actualizarEvento(@PathVariable Long id, @RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.ok(agendaService.actualizarEvento(medicoId(datos), id, datos));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA04: elimina un evento. */
    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<?> eliminarEvento(@PathVariable Long id, @RequestParam Long medicoId) {
        try {
            agendaService.eliminarEvento(medicoId, id);
            return ResponseEntity.ok(Map.of("mensaje", "Evento eliminado exitosamente."));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA06: tareas del médico, opcionalmente de un día y filtradas por estado. */
    @GetMapping("/{medicoId}/tareas")
    public ResponseEntity<?> tareas(@PathVariable Long medicoId, @RequestParam(required = false) String fecha,
                                    @RequestParam(required = false) String estado) {
        try {
            LocalDate dia = null;
            if (fecha != null && !fecha.isBlank()) {
                try {
                    dia = LocalDate.parse(fecha.trim());
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("La fecha indicada no es válida.");
                }
            }
            List<Map<String, Object>> lista = agendaService.tareas(medicoId, dia, estado);
            return ResponseEntity.ok(lista);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    @PostMapping("/tareas")
    public ResponseEntity<?> crearTarea(@RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(agendaService.crearTarea(medicoId(datos), datos));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Completa o descompleta una tarea. */
    @PutMapping("/tareas/{id}/completar")
    public ResponseEntity<?> completarTarea(@PathVariable Long id, @RequestBody Map<String, Object> datos) {
        try {
            boolean completada = datos.get("completada") != null && Boolean.parseBoolean(datos.get("completada").toString());
            return ResponseEntity.ok(agendaService.completarTarea(medicoId(datos), id, completada));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    @DeleteMapping("/tareas/{id}")
    public ResponseEntity<?> eliminarTarea(@PathVariable Long id, @RequestParam Long medicoId) {
        try {
            agendaService.eliminarTarea(medicoId, id);
            return ResponseEntity.ok(Map.of("mensaje", "Tarea eliminada exitosamente."));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    private Long medicoId(Map<String, Object> datos) {
        if (datos.get("medicoId") == null) {
            throw new IllegalArgumentException("Debe indicar el médico.");
        }
        try {
            return Long.valueOf(datos.get("medicoId").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El identificador del médico no es válido.");
        }
    }

    private LocalDateTime fechaHora(String texto) {
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El rango de fechas indicado no es válido.");
        }
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
