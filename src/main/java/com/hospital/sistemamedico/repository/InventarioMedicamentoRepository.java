package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.InventarioMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventarioMedicamentoRepository extends JpaRepository<InventarioMedicamento, Long> {

    Optional<InventarioMedicamento> findByMedicamentoIdAndSucursalId(Long medicamentoId, Long sucursalId);

    List<InventarioMedicamento> findBySucursalId(Long sucursalId);
}
