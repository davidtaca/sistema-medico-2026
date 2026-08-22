package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.SignosVitales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignosVitalesRepository extends JpaRepository<SignosVitales, Long> {

    Optional<SignosVitales> findByCitaId(Long citaId);

    boolean existsByCitaId(Long citaId);
}