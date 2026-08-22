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
        if (usuarioRepository.existsByUsername(usuario.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }
        if (usuarioRepository.existsByCorreo(usuario.getCorreo())) {
            throw new IllegalArgumentException("El correo ya está registrado.");
        }
        if (usuario.getDpi() != null && usuarioRepository.existsByDpi(usuario.getDpi())) {
            throw new IllegalArgumentException("El DPI ya está registrado.");
        }
        if (usuario.getRol() == Rol.MEDICO) {
            if (usuario.getSucursal() == null || usuario.getEspecialidad() == null) {
                throw new IllegalArgumentException("Un médico debe tener sucursal y especialidad asignadas.");
            }
        }
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    public Usuario autenticar(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (!usuario.getPassword().equals(password)) {
            throw new IllegalArgumentException("Contraseña incorrecta.");
        }
        if (!usuario.isActivo()) {
            throw new IllegalArgumentException("Usuario inactivo.");
        }
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

    public Usuario actualizarUsuario(Long id, Usuario datosActualizados) {
        Usuario existente = buscarPorId(id);
        existente.setNombreCompleto(datosActualizados.getNombreCompleto());
        existente.setTelefono(datosActualizados.getTelefono());
        existente.setNit(datosActualizados.getNit());
        existente.setNumeroSeguro(datosActualizados.getNumeroSeguro());
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