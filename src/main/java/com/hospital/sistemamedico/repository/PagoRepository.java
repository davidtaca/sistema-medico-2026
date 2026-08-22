package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByCitaId(Long citaId);

    boolean existsByCitaId(Long citaId);
}
