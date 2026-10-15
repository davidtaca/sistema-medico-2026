package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra los catálogos del CU-15 que solo tienen nombre, descripción y
 * estado: especialidades, estados de cita, laboratorios y roles.
 */
@Configuration
public class CatalogosSimplesConfig {

    @Bean
    public CatalogoAdaptador catalogoEspecialidades(EspecialidadRepository repo) {
        return new CatalogoSimple<>("especialidades", "Especialidades", 1, null, repo, 200, 500, true, true,
                Especialidad::new, Especialidad::getId, Especialidad::getNombre, Especialidad::setNombre,
                Especialidad::getDescripcion, Especialidad::setDescripcion, Especialidad::isActivo, Especialidad::setActivo);
    }

    @Bean
    public CatalogoAdaptador catalogoEstadosCita(EstadoCitaCatalogoRepository repo) {
        return new CatalogoSimple<>("estados-cita", "Estados de Cita", 3,
                "Catálogo informativo: el flujo de la cita sigue dependiendo de sus estados internos.", repo, 50, 200, false, false,
                EstadoCitaCatalogo::new, EstadoCitaCatalogo::getId, EstadoCitaCatalogo::getNombre, EstadoCitaCatalogo::setNombre,
                EstadoCitaCatalogo::getDescripcion, EstadoCitaCatalogo::setDescripcion, EstadoCitaCatalogo::isActivo, EstadoCitaCatalogo::setActivo);
    }

    @Bean
    public CatalogoAdaptador catalogoLaboratorios(LaboratorioRepository repo) {
        return new CatalogoSimple<>("laboratorios", "Laboratorios", 4, null, repo, 200, 500, false, false,
                Laboratorio::new, Laboratorio::getId, Laboratorio::getNombre, Laboratorio::setNombre,
                Laboratorio::getDescripcion, Laboratorio::setDescripcion, Laboratorio::isActivo, Laboratorio::setActivo);
    }

    @Bean
    public CatalogoAdaptador catalogoRoles(RolCatalogoRepository repo) {
        return new CatalogoSimple<>("roles", "Roles", 7,
                "Catálogo informativo: los permisos de cada rol los define el sistema.", repo, 200, 500, false, false,
                RolCatalogo::new, RolCatalogo::getId, RolCatalogo::getNombre, RolCatalogo::setNombre,
                RolCatalogo::getDescripcion, RolCatalogo::setDescripcion, RolCatalogo::isActivo, RolCatalogo::setActivo);
    }
}
