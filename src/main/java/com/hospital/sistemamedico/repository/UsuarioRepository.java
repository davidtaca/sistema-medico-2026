package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByDpi(String dpi);

    boolean existsByUsername(String username);

    boolean existsByCorreo(String correo);

    boolean existsByDpi(String dpi);

    java.util.List<Usuario> findByRol(Rol rol);

    java.util.List<Usuario> findByNombreCompletoContainingIgnoreCase(String nombre);

    java.util.List<Usuario> findByCorreoContainingIgnoreCase(String correo);

    java.util.List<Usuario> findByUsernameContainingIgnoreCase(String username);

    java.util.List<Usuario> findByDpiContaining(String dpi);
}
