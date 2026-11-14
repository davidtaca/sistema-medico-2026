package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Dashboard de gráficas del administrador: los datos salen de la base de datos en cada consulta. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<?> dashboard(@RequestParam Long adminId,
                                       @RequestParam(defaultValue = "3") int dias,
                                       @RequestParam(defaultValue = "10") int medicamentos) {
        try {
            return ResponseEntity.ok(dashboardService.dashboard(adminId, dias, medicamentos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
