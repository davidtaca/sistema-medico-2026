package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

    List<Medicamento> findByActivoTrueOrderByNombre();
}
