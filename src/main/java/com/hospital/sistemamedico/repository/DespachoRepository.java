package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Despacho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    List<Despacho> findTop20BySucursalIdOrderByFechaDesc(Long sucursalId);

    List<Despacho> findByRecetaCitaPacienteIdOrderByFechaDesc(Long pacienteId);
}
