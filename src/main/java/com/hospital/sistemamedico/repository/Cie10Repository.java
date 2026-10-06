package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Cie10;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Cie10Repository extends JpaRepository<Cie10, String> {

    /** Búsqueda para el autocompletado: coincidencia parcial en código o descripción, máximo 15 resultados. */
    List<Cie10> findTop15ByCodigoContainingIgnoreCaseOrDescripcionContainingIgnoreCaseOrderByCodigo(String codigo, String descripcion);
}
