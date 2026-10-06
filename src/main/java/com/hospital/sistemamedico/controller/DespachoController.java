package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.DespachoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints del despacho de medicamentos en farmacia (CU-11): búsqueda de
 * recetas, detalle con disponibilidad, confirmación del despacho, constancia
 * de "no adquirido", alertas de stock mínimo e historial.
 */
@RestController
@RequestMapping("/api/despachos")
public class DespachoController {

    @Autowired
    private DespachoService despachoService;

    /** Paso 2: recetas activas, filtradas por ID de receta y/o ID de consulta. */
    @GetMapping("/recetas")
    public ResponseEntity<?> buscarRecetas(@RequestParam(required = false) String recetaId,
                                           @RequestParam(required = false) String consultaId) {
        try {
            return ResponseEntity.ok(despachoService.buscarRecetas(numero(recetaId, "ID de receta"), numero(consultaId, "ID de consulta"))
                    .stream().map(despachoService::resumenReceta).collect(Collectors.toList()));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 3 a 5: valida la vigencia y muestra los medicamentos con su disponibilidad. */
    @GetMapping("/recetas/{recetaId}")
    public ResponseEntity<?> detalle(@PathVariable Long recetaId, @RequestParam Long farmaceuticoId) {
        try {
            return ResponseEntity.ok(despachoService.detalle(recetaId, farmaceuticoId));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 9 a 12: confirma el despacho. */
    @PostMapping("/confirmar")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> confirmar(@RequestBody Map<String, Object> datos) {
        try {
            List<Map<String, Object>> lineas = new ArrayList<>();
            if (datos.get("items") instanceof List<?> lista) {
                for (Object o : lista) {
                    if (o instanceof Map<?, ?> m) {
                        lineas.add((Map<String, Object>) m);
                    }
                }
            }
            Map<String, Object> resultado = despachoService.confirmar(
                    idObligatorio(datos, "recetaId"), idObligatorio(datos, "farmaceuticoId"), lineas);
            despachoService.notificarSustituciones(Long.valueOf(resultado.get("despachoId").toString()));
            return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA03: el paciente no desea adquirir los medicamentos. */
    @PostMapping("/no-adquirido")
    public ResponseEntity<?> noAdquirido(@RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(despachoService.registrarNoAdquirido(
                    idObligatorio(datos, "recetaId"), idObligatorio(datos, "farmaceuticoId")));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Alertas de stock mínimo de la sucursal del usuario. */
    @GetMapping("/alertas-stock")
    public ResponseEntity<?> alertas(@RequestParam Long farmaceuticoId) {
        try {
            return ResponseEntity.ok(despachoService.alertasStock(farmaceuticoId));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Últimos despachos de la sucursal del usuario. */
    @GetMapping("/recientes")
    public ResponseEntity<?> recientes(@RequestParam Long farmaceuticoId) {
        try {
            return ResponseEntity.ok(despachoService.recientes(farmaceuticoId));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Historial de medicamentos despachados a un paciente. */
    @GetMapping("/paciente/{pacienteId}")
    public List<Map<String, Object>> historial(@PathVariable Long pacienteId) {
        return despachoService.historialPaciente(pacienteId);
    }

    private Long numero(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if (!valor.trim().matches("\\d{1,18}")) {
            throw new IllegalArgumentException("El " + campo + " debe ser un número.");
        }
        return Long.valueOf(valor.trim());
    }

    private Long idObligatorio(Map<String, Object> datos, String campo) {
        if (datos.get(campo) == null) {
            throw new IllegalArgumentException("Falta el dato obligatorio: " + campo + ".");
        }
        return Long.valueOf(datos.get(campo).toString());
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
