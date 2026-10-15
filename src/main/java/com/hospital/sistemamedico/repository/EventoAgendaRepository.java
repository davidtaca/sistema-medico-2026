package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.EventoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventoAgendaRepository extends JpaRepository<EventoAgenda, Long> {

    /** Eventos del médico que se cruzan con el rango [desde, hasta). */
    List<EventoAgenda> findByMedicoIdAndInicioLessThanAndFinGreaterThanOrderByInicio(Long medicoId, LocalDateTime hasta, LocalDateTime desde);

    /** Eventos del médico que cubren un instante (inicio <= momento < fin). */
    List<EventoAgenda> findByMedicoIdAndInicioLessThanEqualAndFinGreaterThan(Long medicoId, LocalDateTime momento, LocalDateTime momentoFin);
}
