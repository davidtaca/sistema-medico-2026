package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.CatalogoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints del mantenimiento de catálogos del sistema (CU-15). Una sola API
 * atiende todos los catálogos; la clave del catálogo va en la ruta (por
 * ejemplo, /api/catalogos/medicamentos).
 */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    @Autowired
    private CatalogoService service;

    @GetMapping
    public Object catalogos() {
        return service.listarCatalogos();
    }

    @GetMapping("/{clave}/esquema")
    public ResponseEntity<?> esquema(@PathVariable String clave) {
        try {
            return ResponseEntity.ok(service.esquema(clave));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{clave}/opciones")
    public ResponseEntity<?> opciones(@PathVariable String clave, @RequestParam String campo) {
        try {
            return ResponseEntity.ok(service.opciones(clave, campo));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Paso 2: listado con filtros (texto y estado) y paginación. */
    @GetMapping("/{clave}")
    public ResponseEntity<?> listar(@PathVariable String clave,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String estado,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "10") int tamano) {
        try {
            return ResponseEntity.ok(service.listar(clave, q, estado, pagina, tamano));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    @GetMapping("/{clave}/{id}")
    public ResponseEntity<?> obtener(@PathVariable String clave, @PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.obtener(clave, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Crear. El cuerpo lleva "adminId" y los campos del catálogo. */
    @PostMapping("/{clave}")
    public ResponseEntity<?> crear(@PathVariable String clave, @RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mensaje", service.crear(adminId(datos), clave, datos)));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA02: editar. */
    @PutMapping("/{clave}/{id}")
    public ResponseEntity<?> actualizar(@PathVariable String clave, @PathVariable Long id, @RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.ok(Map.of("mensaje", service.actualizar(adminId(datos), clave, id, datos)));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** FA03: eliminar (baja lógica). */
    @DeleteMapping("/{clave}/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String clave, @PathVariable Long id, @RequestParam Long adminId) {
        try {
            return ResponseEntity.ok(Map.of("mensaje", service.eliminar(adminId, clave, id)));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    private Long adminId(Map<String, Object> datos) {
        if (datos.get("adminId") == null) {
            throw new IllegalArgumentException("Debe indicar el usuario administrador.");
        }
        try {
            return Long.valueOf(datos.get("adminId").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El identificador del administrador no es válido.");
        }
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
