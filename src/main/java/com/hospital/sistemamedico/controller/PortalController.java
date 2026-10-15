package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.service.PortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Información pública del portal web (CU-00): hospital, servicios,
 * especialidades, ubicaciones y horarios. No requiere iniciar sesión.
 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    @Autowired
    private PortalService portalService;

    @GetMapping
    public Map<String, Object> informacion() {
        return portalService.informacion();
    }
}
