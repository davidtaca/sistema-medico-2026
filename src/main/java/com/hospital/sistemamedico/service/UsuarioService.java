package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Usuario registrarUsuario(Usuario usuario) {
        // RN-CU01-04: Nombre
        if (usuario.getNombreCompleto() == null || usuario.getNombreCompleto().isBlank()) {
            throw new IllegalArgumentException("El campo Nombre es obligatorio.");
        }
        int longitudNombre = usuario.getNombreCompleto().trim().length();
        if (longitudNombre < 10 || longitudNombre > 100) {
            throw new IllegalArgumentException("El nombre debe contener entre 10 y 100 caracteres. Usted ingresó " + longitudNombre + " caracteres.");
        }

        // RN-CU01-05: Credenciales (username)
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("El campo Usuario es obligatorio.");
        }
        String username = usuario.getUsername().trim();
        if (username.length() < 8) {
            throw new IllegalArgumentException("El usuario debe contener al menos 8 caracteres.");
        }
        if (username.length() > 9) {
            throw new IllegalArgumentException("El usuario no puede exceder los 9 caracteres.");
        }
        if (!username.matches("^[a-zA-Z0-9]+$")) {
            throw new IllegalArgumentException("El usuario debe contener únicamente caracteres alfanuméricos.");
        }
        if (usuarioRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El nombre de usuario " + username + " ya se encuentra registrado. Por favor, elija otro.");
        }

        // Contraseña (mismo estándar que RN-CU02-06)
        if (usuario.getPassword() == null || usuario.getPassword().length() < 12) {
            throw new IllegalArgumentException("La contraseña debe contener al menos 12 caracteres.");
        }

        // RN-GLOBAL-001 (vía RN-CU01-07): DPI opcional
        if (usuario.getDpi() != null && !usuario.getDpi().isBlank()) {
            if (!usuario.getDpi().matches("^\\d{13}$")) {
                throw new IllegalArgumentException("El DPI debe contener exactamente 13 dígitos numéricos.");
            }
            if (usuarioRepository.existsByDpi(usuario.getDpi())) {
                throw new IllegalArgumentException("Ya existe una cuenta registrada con este número de DPI. Si ya tiene cuenta, inicie sesión.");
            }
        }

        // RN-CU01-08 (interno, opcional) / RN-CU02-02 (paciente, obligatorio): Teléfono
        boolean esPaciente = usuario.getRol() == Rol.PACIENTE;
        if (esPaciente && (usuario.getTelefono() == null || usuario.getTelefono().isBlank())) {
            throw new IllegalArgumentException("El número de teléfono debe contener exactamente 8 dígitos numéricos.");
        }
        if (usuario.getTelefono() != null && !usuario.getTelefono().isBlank()) {
            if (!usuario.getTelefono().matches("^\\d{8}$")) {
                throw new IllegalArgumentException("El número de teléfono debe contener exactamente 8 dígitos numéricos.");
            }
        }

        // RN-CU01-09: Rol obligatorio
        if (usuario.getRol() == null) {
            throw new IllegalArgumentException("Debe seleccionar un rol para el usuario.");
        }

        // RN-CU01-06 / RN-CU01-13: Sucursal obligatoria en creación para todo usuario interno (no aplica a pacientes)
        if (usuario.getRol() != Rol.PACIENTE && usuario.getSucursal() == null) {
            throw new IllegalArgumentException("Debe seleccionar una sucursal para el usuario.");
        }

        // RN-CU01-14: Especialidad obligatoria solo si el rol es Médico
        if (usuario.getRol() == Rol.MEDICO && usuario.getEspecialidad() == null) {
            throw new IllegalArgumentException("Debe seleccionar una especialidad para el médico.");
        }

        // RN-CU01-11 (interno, opcional) / RN-GLOBAL-002 vía CU-02 (paciente, obligatorio): NIT
        if (esPaciente && (usuario.getNit() == null || usuario.getNit().isBlank())) {
            throw new IllegalArgumentException("El campo NIT es obligatorio.");
        }
        if (usuario.getNit() != null && !usuario.getNit().isBlank()) {
            String nit = usuario.getNit().trim();
            if (nit.length() < 8 || nit.length() > 9) {
                throw new IllegalArgumentException("El NIT debe contener entre 8 y 9 caracteres. Usted ingresó " + nit.length() + " caracteres.");
            }
            if (!nit.matches("^[a-zA-Z0-9]+$")) {
                throw new IllegalArgumentException("El NIT debe contener únicamente caracteres alfanuméricos.");
            }
        }

        // RN-CU01-12: Número de Seguro opcional
        if (usuario.getNumeroSeguro() != null && !usuario.getNumeroSeguro().isBlank()) {
            int longitudSeguro = usuario.getNumeroSeguro().trim().length();
            if (longitudSeguro < 5 || longitudSeguro > 50) {
                throw new IllegalArgumentException("El número de seguro debe contener entre 5 y 50 caracteres.");
            }
        }

        // RN-CU02-04: Correo Electrónico
        if (usuario.getCorreo() == null || usuario.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El campo Correo Electrónico es obligatorio.");
        }
        if (!usuario.getCorreo().matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("El formato del correo electrónico no es válido. Ejemplo: usuario@dominio.com");
        }
        if (usuarioRepository.existsByCorreo(usuario.getCorreo())) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con este correo electrónico.");
        }

        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    public Usuario autenticar(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario o contraseña incorrectos."));

        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(java.time.LocalDateTime.now())) {
                throw new IllegalStateException("Cuenta bloqueada temporalmente. Intente de nuevo en 15 minutos.");
            } else {
                usuario.setBloqueadoHasta(null);
                usuario.setIntentosFallidos(0);
            }
        }

        if (!usuario.getPassword().equals(password)) {
            usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);

            if (usuario.getIntentosFallidos() >= MAX_INTENTOS) {
                usuario.setBloqueadoHasta(java.time.LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
                usuarioRepository.save(usuario);
                throw new IllegalStateException("Cuenta bloqueada temporalmente. Intente de nuevo en 15 minutos.");
            }

            usuarioRepository.save(usuario);
            int restantes = MAX_INTENTOS - usuario.getIntentosFallidos();
            throw new IllegalArgumentException("Usuario o contraseña incorrectos. Intentos restantes: " + restantes + ".");
        }

        if (!usuario.isActivo()) {
            throw new IllegalArgumentException("Usuario inactivo.");
        }

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        return usuario;
    }

    public List<Usuario> listarPorRol(Rol rol) {
        return usuarioRepository.findByRol(rol);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }
    public List<Usuario> buscarPorCampo(String campo, String valor) {
        if (campo == null || valor == null || valor.isBlank()) {
            return listarTodos();
        }
        switch (campo.toUpperCase()) {
            case "ID":
                try {
                    Long id = Long.parseLong(valor.trim());
                    return usuarioRepository.findById(id).map(List::of).orElse(List.of());
                } catch (NumberFormatException e) {
                    return List.of();
                }
            case "NOMBRE":
                return usuarioRepository.findByNombreCompletoContainingIgnoreCase(valor.trim());
            case "CORREO":
                return usuarioRepository.findByCorreoContainingIgnoreCase(valor.trim());
            case "ROL":
                try {
                    return usuarioRepository.findByRol(Rol.valueOf(valor.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return List.of();
                }
            case "USERNAME":
                return usuarioRepository.findByUsernameContainingIgnoreCase(valor.trim());
            case "DPI":
                return usuarioRepository.findByDpiContaining(valor.trim());
            default:
                return listarTodos();
        }
    }

    public Usuario actualizarUsuario(Long id, Usuario datosActualizados) {
        Usuario existente = buscarPorId(id);

        if (datosActualizados.getNombreCompleto() != null && !datosActualizados.getNombreCompleto().isBlank()) {
            int longitud = datosActualizados.getNombreCompleto().trim().length();
            if (longitud < 10 || longitud > 100) {
                throw new IllegalArgumentException("El nombre debe contener entre 10 y 100 caracteres. Usted ingresó " + longitud + " caracteres.");
            }
            existente.setNombreCompleto(datosActualizados.getNombreCompleto());
        }

        if (datosActualizados.getCorreo() != null && !datosActualizados.getCorreo().isBlank()
                && !datosActualizados.getCorreo().equals(existente.getCorreo())) {
            if (usuarioRepository.existsByCorreo(datosActualizados.getCorreo())) {
                throw new IllegalArgumentException("El correo ya está registrado.");
            }
            existente.setCorreo(datosActualizados.getCorreo());
        }

        if (datosActualizados.getUsername() != null && !datosActualizados.getUsername().isBlank()
                && !datosActualizados.getUsername().equals(existente.getUsername())) {
            String username = datosActualizados.getUsername().trim();
            if (username.length() < 8 || username.length() > 9 || !username.matches("^[a-zA-Z0-9]+$")) {
                throw new IllegalArgumentException("El nombre de usuario debe tener entre 8 y 9 caracteres alfanuméricos.");
            }
            if (usuarioRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("El nombre de usuario " + username + " ya se encuentra registrado. Por favor, elija otro.");
            }
            existente.setUsername(username);
        }

        if (datosActualizados.getPassword() != null && !datosActualizados.getPassword().isBlank()) {
            if (datosActualizados.getPassword().length() < 12) {
                throw new IllegalArgumentException("La contraseña debe contener al menos 12 caracteres.");
            }
            existente.setPassword(datosActualizados.getPassword());
        }

        if (datosActualizados.getDpi() != null && !datosActualizados.getDpi().isBlank()) {
            if (!datosActualizados.getDpi().matches("^\\d{13}$")) {
                throw new IllegalArgumentException("El DPI debe contener exactamente 13 dígitos numéricos.");
            }
            existente.setDpi(datosActualizados.getDpi());
        }

        if (datosActualizados.getTelefono() != null && !datosActualizados.getTelefono().isBlank()) {
            if (!datosActualizados.getTelefono().matches("^\\d{8}$")) {
                throw new IllegalArgumentException("El teléfono debe contener exactamente 8 dígitos.");
            }
            existente.setTelefono(datosActualizados.getTelefono());
        }

        if (datosActualizados.getRol() != null) {
            existente.setRol(datosActualizados.getRol());
        }

        if (datosActualizados.getNit() != null) {
            if (!datosActualizados.getNit().isBlank()) {
                int longitudNit = datosActualizados.getNit().trim().length();
                if (longitudNit < 8 || longitudNit > 9) {
                    throw new IllegalArgumentException("El NIT debe contener entre 8 y 9 caracteres.");
                }
            }
            existente.setNit(datosActualizados.getNit());
        }

        if (datosActualizados.getNumeroSeguro() != null) {
            if (!datosActualizados.getNumeroSeguro().isBlank()) {
                int longitudSeguro = datosActualizados.getNumeroSeguro().trim().length();
                if (longitudSeguro < 5 || longitudSeguro > 50) {
                    throw new IllegalArgumentException("El número de seguro debe contener entre 5 y 50 caracteres.");
                }
            }
            existente.setNumeroSeguro(datosActualizados.getNumeroSeguro());
        }

        if (datosActualizados.getSucursal() != null) {
            existente.setSucursal(datosActualizados.getSucursal());
        }

        if (datosActualizados.getEspecialidad() != null) {
            existente.setEspecialidad(datosActualizados.getEspecialidad());
        }

        return usuarioRepository.save(existente);
    }

    public void desactivarUsuario(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }
    public Usuario buscarPorDpi(String dpi) {
        return usuarioRepository.findByDpi(dpi)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ningún paciente con ese DPI."));
    }
}