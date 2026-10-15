package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Al iniciar la aplicación cifra con BCrypt las contraseñas que aún estén en
 * texto plano (cuentas creadas antes de que el sistema las cifrara). Los
 * usuarios siguen entrando con la misma contraseña de siempre.
 */
@Component
@Order(5)
public class CifradoContrasenasInicial implements CommandLineRunner {

    @Autowired
    private UsuarioService usuarioService;

    @Override
    public void run(String... args) {
        int cifradas = usuarioService.cifrarContrasenasEnTextoPlano();
        if (cifradas > 0) {
            System.out.println("Se cifraron " + cifradas + " contraseña(s) que estaban en texto plano.");
        }
    }
}
