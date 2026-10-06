package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.OrdenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenLaboratorioRepository extends JpaRepository<OrdenLaboratorio, Long> {

    List<OrdenLaboratorio> findByCitaIdOrderByFecha(Long citaId);

    List<OrdenLaboratorio> findAllByOrderByFechaDesc();
}
