package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Cita;
import com.hospital.sistemamedico.model.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    List<Cita> findByPacienteId(Long pacienteId);

    List<Cita> findByMedicoId(Long medicoId);

    List<Cita> findByEstado(EstadoCita estado);

    List<Cita> findByMedicoIdAndFechaHoraBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);

    boolean existsByMedicoIdAndFechaHora(Long medicoId, LocalDateTime fechaHora);
}