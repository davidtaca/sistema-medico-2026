package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.Cita;
import com.hospital.sistemamedico.model.EstadoCita;
import com.hospital.sistemamedico.model.SignosVitales;
import com.hospital.sistemamedico.service.CitaService;
import com.hospital.sistemamedico.service.SignosVitalesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/signos-vitales")
public class SignosVitalesController {

    @Autowired
    private SignosVitalesService signosVitalesService;

    @Autowired
    private CitaService citaService;

    /**
     * Pacientes que necesitan signos vitales (CU-07, paso 1): los que están en "Paciente Presente" y
     * todavía no tienen signos registrados (los que ya los tienen esperan al médico) y los que ya
     * fueron llamados y están en "Signos Vitales". Las emergencias aparecen primero y luego por orden de llegada.
     */
    @GetMapping("/pendientes")
    public List<Cita> listarPacientesEnEspera() {
        return citaService.listarTodas().stream()
                .filter(c -> c.getEstado() == EstadoCita.SIGNOS_VITALES
                        || (c.getEstado() == EstadoCita.PACIENTE_PRESENTE && !signosVitalesService.tieneSignos(c.getId())))
                .sorted(java.util.Comparator.comparing(Cita::isEmergencia).reversed()
                        .thenComparing(c -> c.getHoraLlegada() != null ? c.getHoraLlegada() : c.getFechaHora()))
                .collect(Collectors.toList());
    }

    @PutMapping("/llamar/{citaId}")
    public ResponseEntity<?> llamarPaciente(@PathVariable Long citaId) {
        try {
            return ResponseEntity.ok(citaService.llamarParaSignosVitales(citaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody Map<String, Object> datos) {
        try {
            if (datos.get("citaId") == null || datos.get("enfermeroId") == null) {
                throw new IllegalArgumentException("Debe indicar la cita y el enfermero.");
            }
            // Los valores faltantes o no numéricos llegan como null y el servicio responde con el mensaje de su regla
            SignosVitales sv = signosVitalesService.registrar(
                    Long.valueOf(datos.get("citaId").toString()),
                    Long.valueOf(datos.get("enfermeroId").toString()),
                    entero(datos.get("presionSistolica")),
                    entero(datos.get("presionDiastolica")),
                    decimal(datos.get("temperatura")),
                    decimal(datos.get("peso")),
                    decimal(datos.get("talla")),
                    entero(datos.get("frecuenciaCardiaca")),
                    datos.get("emergencia") != null && Boolean.parseBoolean(datos.get("emergencia").toString())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(sv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Integer entero(Object valor) {
        try {
            return valor == null || valor.toString().isBlank() ? null : Integer.valueOf(valor.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double decimal(Object valor) {
        try {
            return valor == null || valor.toString().isBlank() ? null : Double.valueOf(valor.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping("/cita/{citaId}")
    public ResponseEntity<?> buscarPorCita(@PathVariable Long citaId) {
        try {
            return ResponseEntity.ok(signosVitalesService.buscarPorCita(citaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}