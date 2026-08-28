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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@RestController
@RequestMapping("/api/citas")
public class CitaController {

    @Autowired
    private CitaService citaService;
    @Value("${app.upload.dir}")
    private String directorioSubida;

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
                    datos.get("emergencia") != null && Boolean.parseBoolean(datos.get("emergencia").toString()),
                    datos.get("documentoAdjunto") != null ? datos.get("documentoAdjunto").toString() : null
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

    @PostMapping("/documento")
    public ResponseEntity<?> subirDocumento(@RequestParam("archivo") MultipartFile archivo) {
        try {
            // RN-CU03-04: no puede estar vacío
            if (archivo.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "El documento debe ser un archivo PDF válido, no vacío, no encriptado y con un tamaño máximo de 2 MB."));
            }

            // RN-CU03-04: máximo 2 MB
            if (archivo.getSize() > 2 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "El documento debe ser un archivo PDF válido, no vacío, no encriptado y con un tamaño máximo de 2 MB."));
            }

            // RN-CU03-04: formato PDF (extensión + firma de archivo)
            String nombreOriginal = archivo.getOriginalFilename();
            if (nombreOriginal == null || !nombreOriginal.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "El documento debe ser un archivo PDF válido, no vacío, no encriptado y con un tamaño máximo de 2 MB."));
            }

            byte[] contenido = archivo.getBytes();
            String encabezado = new String(contenido, 0, Math.min(5, contenido.length));
            if (!encabezado.startsWith("%PDF-")) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "El documento debe ser un archivo PDF válido, no vacío, no encriptado y con un tamaño máximo de 2 MB."));
            }

            // RN-CU03-04: no debe estar encriptado (verificación básica de la firma /Encrypt)
            String contenidoTexto = new String(contenido);
            if (contenidoTexto.contains("/Encrypt")) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "El documento debe ser un archivo PDF válido, no vacío, no encriptado y con un tamaño máximo de 2 MB."));
            }

            Path carpeta = Paths.get(directorioSubida);
            Files.createDirectories(carpeta);

            String nombreGuardado = UUID.randomUUID() + ".pdf";
            Path destino = carpeta.resolve(nombreGuardado);
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of("nombreArchivo", nombreGuardado));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo procesar el archivo."));
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
    @PutMapping("/{id}/reasignar-medico")
    public ResponseEntity<?> reasignarMedico(@PathVariable Long id, @RequestBody Map<String, Object> datos) {
        try {
            Long nuevoMedicoId = Long.valueOf(datos.get("medicoId").toString());
            return ResponseEntity.ok(citaService.reasignarMedico(id, nuevoMedicoId));
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