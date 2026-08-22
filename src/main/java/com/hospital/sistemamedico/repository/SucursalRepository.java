package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    Optional<Sucursal> findByNombre(String nombre);

    List<Sucursal> findByActivoTrue();
}