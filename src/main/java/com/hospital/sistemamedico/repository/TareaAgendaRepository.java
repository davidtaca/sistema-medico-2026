package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.TareaAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TareaAgendaRepository extends JpaRepository<TareaAgenda, Long> {

    List<TareaAgenda> findByMedicoIdOrderByFechaLimiteAscIdAsc(Long medicoId);
}
