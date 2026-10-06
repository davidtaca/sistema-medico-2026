package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Examen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamenRepository extends JpaRepository<Examen, Long> {

    List<Examen> findByActivoTrueOrderByNombre();
}
