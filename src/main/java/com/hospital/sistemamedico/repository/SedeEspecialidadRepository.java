package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.SedeEspecialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SedeEspecialidadRepository extends JpaRepository<SedeEspecialidad, Long> {

    boolean existsBySucursalIdAndEspecialidadId(Long sucursalId, Long especialidadId);

    /** Asignaciones activas de una sede (las que ve el paciente en el portal de citas). */
    List<SedeEspecialidad> findBySucursalIdAndActivoTrue(Long sucursalId);

    boolean existsBySucursalIdAndEspecialidadIdAndActivoTrue(Long sucursalId, Long especialidadId);
}
