package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.SedeEspecialidad;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import com.hospital.sistemamedico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Migración inicial de la configuración de especialidades por sede (CU-13).
 * Antes del CU-13 el portal de citas derivaba las especialidades de cada sede
 * a partir de los médicos que trabajan en ella. Si todavía no hay ninguna
 * asignación registrada, se crea una por cada combinación sede-especialidad
 * de los médicos existentes, para que los pacientes sigan viendo las mismas
 * opciones. Si ya hay asignaciones, no se toca nada.
 */
@Component
@Order(10)
public class AsignacionesIniciales implements CommandLineRunner {

    @Autowired
    private SedeEspecialidadRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getRol() == Rol.MEDICO && u.isActivo() && u.getSucursal() != null && u.getEspecialidad() != null
                    && u.getSucursal().isActivo() && u.getEspecialidad().isActivo()
                    && !repository.existsBySucursalIdAndEspecialidadId(u.getSucursal().getId(), u.getEspecialidad().getId())) {
                repository.save(new SedeEspecialidad(u.getSucursal(), u.getEspecialidad()));
            }
        }
    }
}
