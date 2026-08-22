package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.Cita;
import com.hospital.sistemamedico.service.CitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    @Autowired
    private CitaService citaService;

    @PostMapping
    public ResponseEntity<?> agendar(@RequestBody Map<String, Object> datos) {
        try {
            Cita cita = citaService.agendarCita(
                    Long.valueOf(datos.get("pacienteId").toString()),
                    Long.valueOf(datos.get("medicoId").toString()),
                    Long.valueOf(datos.get("sucursalId").toString()),
                    Long.valueOf(datos.get("especialidadId").toString()),
                    LocalDateTime.parse(datos.get("fechaHora").toString()),
                    datos.get("motivoConsulta") != null ? datos.get("motivoConsulta").toString() : null,
                    datos.get("emergencia") != null && Boolean.parseBoolean(datos.get("emergencia").toString())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(cita);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(citaService.confirmarCita(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/presente")
    public ResponseEntity<?> marcarPresente(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(citaService.marcarPacientePresente(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(citaService.cancelarCita(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(citaService.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<Cita> listarPorPaciente(@PathVariable Long pacienteId) {
        return citaService.listarPorPaciente(pacienteId);
    }

    @GetMapping("/medico/{medicoId}")
    public List<Cita> listarPorMedico(@PathVariable Long medicoId) {
        return citaService.listarPorMedico(medicoId);
    }

    @GetMapping
    public List<Cita> listarTodas() {
        return citaService.listarTodas();
    }
}