package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.SedeEspecialidad;
import com.hospital.sistemamedico.service.AuditoriaService;
import com.hospital.sistemamedico.service.SedeEspecialidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints de la configuración de especialidades por sede (CU-13): listado
 * paginado, asignar, eliminar y consultar las especialidades de una sede para
 * el portal de citas. También expone la bitácora de auditoría de solo lectura.
 */
@RestController
public class SedeEspecialidadController {

    @Autowired
    private SedeEspecialidadService service;

    @Autowired
    private AuditoriaService auditoriaService;

    /** Paso 3: tabla con filtro por ID y paginación. */
    @GetMapping("/api/sede-especialidades")
    public ResponseEntity<?> listar(@RequestParam(required = false) String id,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "10") int tamano) {
        try {
            Long filtro = null;
            if (id != null && !id.isBlank()) {
                if (!id.trim().matches("\\d{1,18}")) {
                    throw new IllegalArgumentException("El ID debe ser un número.");
                }
                filtro = Long.valueOf(id.trim());
            }
            return ResponseEntity.ok(service.listar(filtro, pagina, tamano));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 6 a 9: asigna una especialidad a una sede. */
    @PostMapping("/api/sede-especialidades")
    public ResponseEntity<?> asignar(@RequestBody Map<String, Object> datos) {
        try {
            if (datos.get("adminId") == null) {
                throw new IllegalArgumentException("Debe indicar el usuario administrador.");
            }
            SedeEspecialidad s = service.asignar(
                    Long.valueOf(datos.get("adminId").toString()),
                    idOpcional(datos.get("sucursalId")),
                    idOpcional(datos.get("especialidadId")));
            Map<String, Object> respuesta = new LinkedHashMap<>(service.aMapa(s));
            respuesta.put("mensaje", "Especialidad asignada a la sede correctamente");
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA02: elimina una asignación. */
    @DeleteMapping("/api/sede-especialidades/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, @RequestParam Long adminId) {
        try {
            service.eliminar(adminId, id);
            return ResponseEntity.ok(Map.of("mensaje", "Asignación eliminada correctamente."));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Especialidades disponibles en una sede, para el portal de citas (CU-03). */
    @GetMapping("/api/sede-especialidades/sucursal/{sucursalId}")
    public Object especialidadesDeSucursal(@PathVariable Long sucursalId) {
        return service.especialidadesDeSucursal(sucursalId);
    }

    /** Bitácora de auditoría (solo lectura). */
    @GetMapping("/api/auditoria")
    public Object auditoria(@RequestParam(required = false) String entidad) {
        return auditoriaService.ultimos(entidad);
    }

    private Long idOpcional(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El identificador indicado no es válido.");
        }
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
