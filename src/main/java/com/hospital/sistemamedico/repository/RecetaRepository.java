package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Receta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecetaRepository extends JpaRepository<Receta, Long> {

    List<Receta> findByCitaIdOrderByFecha(Long citaId);

    List<Receta> findByActivaTrueOrderByFechaDesc();
}
