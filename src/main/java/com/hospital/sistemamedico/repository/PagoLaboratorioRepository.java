package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.PagoLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoLaboratorioRepository extends JpaRepository<PagoLaboratorio, Long> {

    Optional<PagoLaboratorio> findByOrdenId(Long ordenId);

    boolean existsByOrdenId(Long ordenId);
}
