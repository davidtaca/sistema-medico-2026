package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.OrdenExamen;
import com.hospital.sistemamedico.model.OrdenLaboratorio;
import com.hospital.sistemamedico.service.LaboratorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints de la gestión de laboratorio (CU-09): tabla de órdenes con
 * filtros, detalle de la orden, registro de resultados y publicación
 * individual de cada resultado.
 */
@RestController
@RequestMapping("/api/laboratorio")
public class LaboratorioController {

    @Autowired
    private LaboratorioService laboratorioService;

    /** Paso 1: tabla de órdenes con filtros por estado, paciente (nombre o DPI) y médico. */
    @GetMapping("/ordenes")
    public ResponseEntity<?> listar(@RequestParam(required = false) String estado,
                                    @RequestParam(required = false) String paciente,
                                    @RequestParam(required = false) String medico) {
        try {
            return ResponseEntity.ok(laboratorioService.listar(estado, paciente, medico).stream()
                    .map(o -> laboratorioService.aMapa(o, false)).collect(Collectors.toList()));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 2 y 3: detalle de la orden con sus exámenes y resultados. */
    @GetMapping("/ordenes/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(laboratorioService.aMapa(laboratorioService.buscarPorId(id), false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Pasos 9 y 10 (FA02): guarda el resultado de un examen. */
    @PutMapping("/ordenes/{ordenId}/examenes/{examenId}/resultado")
    public ResponseEntity<?> guardarResultado(@PathVariable Long ordenId, @PathVariable Long examenId,
                                              @RequestBody Map<String, Object> datos) {
        try {
            OrdenExamen item = laboratorioService.guardarResultado(ordenId, examenId, idPersonal(datos), datos);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("mensaje", "Resultado guardado exitosamente.");
            respuesta.put("fueraDeRango", item.isFueraDeRango());
            if (item.isFueraDeRango()) {
                respuesta.put("advertencia", "Los resultados están fuera del rango de referencia normal. Requiere revisión.");
            }
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 11 a 14: publica el resultado de un examen. */
    @PutMapping("/ordenes/{ordenId}/examenes/{examenId}/publicar")
    public ResponseEntity<?> publicar(@PathVariable Long ordenId, @PathVariable Long examenId,
                                      @RequestBody Map<String, Object> datos) {
        try {
            OrdenLaboratorio orden = laboratorioService.publicarResultado(ordenId, examenId, idPersonal(datos));
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("mensaje", "Resultado publicado exitosamente.");
            respuesta.put("ordenCompletada", orden.getEstado().name().equals("COMPLETADA"));
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    private Long idPersonal(Map<String, Object> datos) {
        if (datos.get("laboratoristaId") == null) {
            throw new IllegalArgumentException("Debe indicar el usuario de laboratorio.");
        }
        return Long.valueOf(datos.get("laboratoristaId").toString());
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
