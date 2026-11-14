package com.hospital.sistemamedico.repository;

import com.hospital.sistemamedico.model.Despacho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    List<Despacho> findTop20BySucursalIdOrderByFechaDesc(Long sucursalId);

    List<Despacho> findByRecetaCitaPacienteIdOrderByFechaDesc(Long pacienteId);

    /** Dashboard: unidades despachadas por medicamento entregado (solo despachos registrados), el más despachado primero. */
    @org.springframework.data.jpa.repository.Query("select i.medicamentoEntregado.nombre, sum(i.cantidad) from DespachoItem i "
            + "where i.despacho.estado = com.hospital.sistemamedico.model.EstadoDespacho.REGISTRADO "
            + "group by i.medicamentoEntregado.nombre order by sum(i.cantidad) desc")
    List<Object[]> unidadesDespachadasPorMedicamento();
}
