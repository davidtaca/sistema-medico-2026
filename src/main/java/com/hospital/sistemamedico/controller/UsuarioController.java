package com.hospital.sistemamedico.controller;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hospital.sistemamedico.service.EmailService;
import com.hospital.sistemamedico.service.AuditoriaService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * Registra en la bitácora de auditoría (postcondición del CU-01) la acción hecha
     * por un administrador sobre una cuenta. Si la petición no indica un administrador
     * (por ejemplo, el autorregistro de un paciente) no se registra nada.
     */
    private void auditar(Long adminId, String accion, Usuario afectado, String detalle) {
        if (adminId == null) {
            return;
        }
        try {
            Usuario admin = usuarioService.buscarPorId(adminId);
            if (admin.getRol() == Rol.ADMINISTRADOR) {
                auditoriaService.registrar(admin, accion, "Usuario", afectado.getId(), detalle);
            }
        } catch (IllegalArgumentException e) {
            // un administrador inexistente no impide completar la operación ya realizada
        }
    }

    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody Usuario usuario, @RequestParam(required = false) Long adminId) {
        try {
            Usuario creado = usuarioService.registrarUsuario(usuario);
            if (creado.getRol() == com.hospital.sistemamedico.model.Rol.PACIENTE) {
                emailService.enviarCorreoBienvenida(creado.getCorreo(), creado.getNombreCompleto());
            }
            auditar(adminId, "CREAR_USUARIO", creado, "Se creó el usuario " + creado.getUsername() + " con rol " + creado.getRol() + ".");
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        try {
            Usuario usuario = usuarioService.autenticar(
                    credenciales.get("username"), credenciales.get("password"));
            return ResponseEntity.ok(usuario);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", e.getMessage(), "bloqueado", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<Usuario> listarTodos() {
        return usuarioService.listarTodos();
    }
    @GetMapping("/dpi/{dpi}")
    public ResponseEntity<?> buscarPorDpi(@PathVariable String dpi) {
        try {
            return ResponseEntity.ok(usuarioService.buscarPorDpi(dpi));
        } catch (IllegalArgumentException e) {
            // Un DPI mal escrito es un error de formato (400); uno bien escrito que no existe es "no encontrado" (404)
            boolean formato = e.getMessage().startsWith("El DPI") || e.getMessage().startsWith("El campo DPI");
            return ResponseEntity.status(formato ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/rol/{rol}")
    public List<Usuario> listarPorRol(@PathVariable Rol rol) {
        return usuarioService.listarPorRol(rol);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(usuarioService.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/buscar")
    public List<Usuario> buscarPorCampo(@RequestParam(required = false) String campo,
                                        @RequestParam(required = false) String valor) {
        return usuarioService.buscarPorCampo(campo, valor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Usuario usuario,
                                        @RequestParam(required = false) Boolean estado,
                                        @RequestParam(required = false) Long adminId) {
        try {
            // El estado (Activo/Inactivo) solo se cambia si la pantalla lo envía como parámetro "estado"
            Usuario actualizado = usuarioService.actualizarUsuario(id, usuario, estado);
            auditar(adminId, "ACTUALIZAR_USUARIO", actualizado, "Se actualizó el usuario " + actualizado.getUsername() + ".");
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> desactivar(@PathVariable Long id, @RequestParam(required = false) Long adminId) {
        try {
            Usuario usuario = usuarioService.buscarPorId(id);
            usuarioService.desactivarUsuario(id);
            auditar(adminId, "ELIMINAR_USUARIO", usuario, "Se eliminó (baja lógica) el usuario " + usuario.getUsername() + ".");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}