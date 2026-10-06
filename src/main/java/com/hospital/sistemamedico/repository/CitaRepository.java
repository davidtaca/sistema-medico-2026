package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Cita;
import com.hospital.sistemamedico.model.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    List<Cita> findByPacienteId(Long pacienteId);

    List<Cita> findByMedicoId(Long medicoId);

    List<Cita> findByEstado(EstadoCita estado);

    List<Cita> findByMedicoIdAndFechaHoraBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);

    boolean existsByMedicoIdAndFechaHora(Long medicoId, LocalDateTime fechaHora);

    /** Horario ocupado por una cita que sigue vigente (las canceladas o no asistidas liberan el horario). */
    boolean existsByMedicoIdAndFechaHoraAndEstadoNotIn(Long medicoId, LocalDateTime fechaHora, Collection<EstadoCita> estados);

    /** Citas de seguimiento próximas a las que aún no se les envió el recordatorio (RN-CU11-05). */
    List<Cita> findByTipoSeguimientoIsNotNullAndRecordatorioEnviadoFalseAndFechaHoraBetween(LocalDateTime desde, LocalDateTime hasta);
}