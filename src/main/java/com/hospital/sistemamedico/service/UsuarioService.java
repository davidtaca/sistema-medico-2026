package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio que contiene toda la lógica de negocio relacionada con los usuarios
 * del sistema (tanto personal interno como pacientes, ya que ambos se manejan
 * en la misma tabla "usuarios" diferenciados por el campo "rol").
 *
 * Cubre: CU-00 (login y bloqueo de cuenta), CU-01 (mantenimiento de usuarios
 * internos) y CU-02 (registro de pacientes).
 */
@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Número máximo de intentos fallidos de login antes de bloquear la cuenta (RN-CU00-02). */
    private static final int MAX_INTENTOS = 5;

    /** Minutos que dura el bloqueo de una cuenta tras exceder MAX_INTENTOS (RN-CU00-03). */
    private static final int MINUTOS_BLOQUEO = 15;

    /**
     * Registra un usuario nuevo en el sistema, ya sea personal interno (CU-01)
     * o un paciente que se autorregistra (CU-02). Aplica todas las validaciones
     * de las reglas de negocio RN-CU01-03 a RN-CU01-14 y RN-CU02-01 a RN-CU02-06.
     *
     * Algunos campos (teléfono y NIT) son obligatorios solo si el rol es PACIENTE;
     * para el resto de roles son opcionales. La sucursal es obligatoria para
     * cualquier rol que no sea PACIENTE, y la especialidad solo es obligatoria
     * si el rol es MEDICO.
     *
     * @param usuario objeto Usuario con los datos ingresados en el formulario (aún sin guardar)
     * @return el Usuario ya guardado en la base de datos, con su id asignado
     * @throws IllegalArgumentException si algún campo no cumple con las reglas de validación,
     *         o si el username/correo/DPI ya existen
     */
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

        // RN-GLOBAL-001 (vía RN-CU01-07): DPI opcional para personal interno
        if (usuario.getDpi() != null && !usuario.getDpi().isBlank()) {
            if (!usuario.getDpi().matches("^\\d{13}$")) {
                throw new IllegalArgumentException("El DPI debe contener exactamente 13 dígitos numéricos.");
            }
            if (usuarioRepository.existsByDpi(usuario.getDpi())) {
                throw new IllegalArgumentException("Ya existe una cuenta registrada con este número de DPI. Si ya tiene cuenta, inicie sesión.");
            }
        }

        boolean esPaciente = usuario.getRol() == Rol.PACIENTE;

        // RN-CU01-08 (interno, opcional) / RN-CU02-02 (paciente, obligatorio): Teléfono
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

    /**
     * Valida las credenciales de inicio de sesión de un usuario (CU-00).
     * Implementa el bloqueo de cuenta: si la cuenta ya está bloqueada y el
     * tiempo de bloqueo no ha vencido, rechaza el intento sin siquiera revisar
     * la contraseña. Si la contraseña es incorrecta, incrementa el contador de
     * intentos fallidos y, al llegar a MAX_INTENTOS, bloquea la cuenta por
     * MINUTOS_BLOQUEO minutos. Si el login es exitoso, reinicia el contador.
     *
     * @param username nombre de usuario ingresado en el formulario de login
     * @param password contraseña ingresada en el formulario de login
     * @return el Usuario autenticado, si las credenciales son correctas
     * @throws IllegalArgumentException si el usuario no existe, la contraseña es incorrecta,
     *         o el usuario está inactivo
     * @throws IllegalStateException si la cuenta está actualmente bloqueada por intentos fallidos
     */
    public Usuario autenticar(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario o contraseña incorrectos."));

        // Si la cuenta estaba bloqueada, verificar si el bloqueo ya venció
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(java.time.LocalDateTime.now())) {
                throw new IllegalStateException("Cuenta bloqueada temporalmente. Intente de nuevo en 15 minutos.");
            } else {
                // El bloqueo ya expiró: se libera la cuenta y se reinicia el contador
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

        // Login exitoso: se reinicia el contador de intentos fallidos por si tenía alguno acumulado
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        return usuario;
    }

    /**
     * Devuelve todos los usuarios que tengan un rol específico.
     * Usado, por ejemplo, para listar médicos disponibles al agendar una cita.
     *
     * @param rol el rol a filtrar (ej. Rol.MEDICO)
     * @return lista de usuarios con ese rol (puede estar vacía)
     */
    public List<Usuario> listarPorRol(Rol rol) {
        return usuarioRepository.findByRol(rol);
    }

    /**
     * Devuelve todos los usuarios registrados en el sistema, sin filtrar por
     * estado activo/inactivo. Usado en la pantalla de Listado de Usuarios (CU-01).
     *
     * @return lista completa de usuarios
     */
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /**
     * Busca un usuario por su id.
     *
     * @param id id del usuario
     * @return el Usuario encontrado
     * @throws IllegalArgumentException si no existe ningún usuario con ese id
     */
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }

    /**
     * Busca un usuario según un campo y valor determinados (usado en el buscador
     * de la pantalla de Listado de Usuarios). Si no se especifica campo o valor,
     * devuelve la lista completa sin filtrar.
     *
     * @param campo nombre del campo por el que se busca: "ID", "NOMBRE", "CORREO",
     *              "ROL", "USERNAME" o "DPI" (no distingue mayúsculas/minúsculas)
     * @param valor texto o número a buscar dentro de ese campo
     * @return lista de usuarios que coinciden con el criterio (puede estar vacía)
     */
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

    /**
     * Busca un usuario por su número de DPI. Usado principalmente en el flujo
     * de recepción y caja (CU-05, CU-06), donde el personal busca al paciente
     * escribiendo su DPI en vez de su id interno.
     *
     * @param dpi número de DPI de 13 dígitos
     * @return el Usuario encontrado
     * @throws IllegalArgumentException si no existe ningún usuario con ese DPI
     */
    public Usuario buscarPorDpi(String dpi) {
        return usuarioRepository.findByDpi(dpi)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ningún paciente con ese DPI."));
    }

    /**
     * Actualiza los datos de un usuario existente (pantalla de edición, CU-01 FA04).
     * A diferencia de registrarUsuario, aquí cada campo es opcional: solo se
     * actualiza (y valida) el campo si viene con un valor distinto de nulo/vacío
     * en datosActualizados, dejando los demás campos sin tocar.
     *
     * @param id id del usuario a actualizar
     * @param datosActualizados objeto Usuario con únicamente los campos que se desean cambiar
     * @return el Usuario ya actualizado y guardado
     * @throws IllegalArgumentException si algún campo enviado no cumple las validaciones,
     *         o si el nuevo username/correo ya está en uso por otro usuario
     */
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

        // La contraseña solo se cambia si el usuario escribió una nueva (campo opcional en el formulario)
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

    /**
     * Desactiva (elimina lógicamente) un usuario. No borra la fila de la base
     * de datos: solo marca el campo "activo" en false, para no romper las
     * relaciones con citas, pagos, etc. que ya existan hacia ese usuario.
     *
     * @param id id del usuario a desactivar
     * @throws IllegalArgumentException si no existe ningún usuario con ese id
     */
    public void desactivarUsuario(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }
}