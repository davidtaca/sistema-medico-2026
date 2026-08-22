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

    @GetMapping("/pendientes")
    public List<Cita> listarPacientesEnEspera() {
        return citaService.listarTodas().stream()
                .filter(c -> c.getEstado() == EstadoCita.PACIENTE_PRESENTE || c.getEstado() == EstadoCita.SIGNOS_VITALES)
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
            SignosVitales sv = signosVitalesService.registrar(
                    Long.valueOf(datos.get("citaId").toString()),
                    Long.valueOf(datos.get("enfermeroId").toString()),
                    Integer.valueOf(datos.get("presionSistolica").toString()),
                    Integer.valueOf(datos.get("presionDiastolica").toString()),
                    Double.valueOf(datos.get("temperatura").toString()),
                    Double.valueOf(datos.get("peso").toString()),
                    Double.valueOf(datos.get("talla").toString()),
                    Integer.valueOf(datos.get("frecuenciaCardiaca").toString()),
                    datos.get("emergencia") != null && Boolean.parseBoolean(datos.get("emergencia").toString())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(sv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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