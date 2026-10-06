package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.ConsultaHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaHistorialRepository extends JpaRepository<ConsultaHistorial, Long> {

    List<ConsultaHistorial> findByConsultaIdOrderByVersion(Long consultaId);
}
