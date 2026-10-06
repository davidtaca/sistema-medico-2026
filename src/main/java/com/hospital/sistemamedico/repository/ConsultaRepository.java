package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Consulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    Optional<Consulta> findByCitaId(Long citaId);

    /** Consultas finalizadas de un paciente, la más reciente primero (historial clínico). */
    List<Consulta> findByCitaPacienteIdAndFinalizadaTrueOrderByFechaFinalizacionDesc(Long pacienteId);
}
