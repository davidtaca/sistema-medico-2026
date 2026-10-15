package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.BitacoraInventarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de la bitácora de movimientos de inventario de farmacia (CU-16):
 * listado con filtros, detalle, registro de movimientos, activar o desactivar
 * registros, panel de inventario en tiempo real, resumen mensual y opciones de
 * los desplegables.
 */
@RestController
@RequestMapping("/api/bitacora-inventario")
public class BitacoraInventarioController {

    @Autowired
    private BitacoraInventarioService service;

    /** Paso 2: tabla con filtros por columna y paginación. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String tipo,
                                    @RequestParam(required = false) String medicamentoId,
                                    @RequestParam(required = false) String sucursalId,
                                    @RequestParam(required = false) String referencia,
                                    @RequestParam(required = false) String usuario,
                                    @RequestParam(required = false) String desde,
                                    @RequestParam(required = false) String hasta,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "10") int tamano) {
        try {
            return ResponseEntity.ok(service.listar(tipo, id(medicamentoId), id(sucursalId), referencia, usuario, desde, hasta, pagina, tamano));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Acción "Ver". */
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.detalle(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Desplegables del formulario: tipos manuales, medicamentos y sucursales activos. */
    @GetMapping("/opciones")
    public Map<String, Object> opciones() {
        return Map.of("tipos", service.tiposManuales(), "medicamentos", service.opcionesMedicamentos(), "sucursales", service.opcionesSucursales());
    }

    /** Panel informativo de inventario: stock actual y mínimo del medicamento en la sucursal. */
    @GetMapping("/panel")
    public ResponseEntity<?> panel(@RequestParam Long medicamentoId, @RequestParam Long sucursalId) {
        try {
            return ResponseEntity.ok(service.panel(medicamentoId, sucursalId));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Resumen mensual de movimientos por tipo. */
    @GetMapping("/resumen")
    public ResponseEntity<?> resumen(@RequestParam String mes, @RequestParam(required = false) String medicamentoId,
                                     @RequestParam(required = false) String sucursalId) {
        try {
            return ResponseEntity.ok(service.resumenMensual(mes, id(medicamentoId), id(sucursalId)));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Pasos 5 a 9: registra el movimiento. El cuerpo lleva "usuarioId" y los campos del formulario. */
    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody Map<String, Object> datos) {
        try {
            if (datos.get("usuarioId") == null) {
                throw new IllegalArgumentException("Debe indicar el usuario.");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(Long.valueOf(datos.get("usuarioId").toString()), datos));
        } catch (NumberFormatException e) {
            return error(new IllegalArgumentException("El identificador del usuario no es válido."));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    /** Acción "Desactivar/Activar". */
    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, Object> datos) {
        try {
            if (datos.get("usuarioId") == null) {
                throw new IllegalArgumentException("Debe indicar el usuario.");
            }
            boolean activo = datos.get("activo") != null && Boolean.parseBoolean(datos.get("activo").toString());
            return ResponseEntity.ok(service.cambiarEstado(Long.valueOf(datos.get("usuarioId").toString()), id, activo));
        } catch (NumberFormatException e) {
            return error(new IllegalArgumentException("El identificador del usuario no es válido."));
        } catch (IllegalArgumentException e) {
            return error(e);
        }
    }

    private Long id(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El filtro indicado no es válido.");
        }
    }

    private ResponseEntity<?> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
