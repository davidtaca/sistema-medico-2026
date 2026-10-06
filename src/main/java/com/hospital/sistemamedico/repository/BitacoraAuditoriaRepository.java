package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.BitacoraAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Long> {

    List<BitacoraAuditoria> findTop50ByOrderByFechaDesc();

    List<BitacoraAuditoria> findTop50ByEntidadOrderByFechaDesc(String entidad);
}
