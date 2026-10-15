package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    /** Todos los movimientos, el más reciente primero. */
    List<MovimientoInventario> findAllByOrderByFechaDescIdDesc();
}
