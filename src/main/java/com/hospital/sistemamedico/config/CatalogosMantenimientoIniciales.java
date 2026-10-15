package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Completa, al arrancar, los datos que necesita el mantenimiento de catálogos
 * (CU-15) y que no existían cuando se crearon los catálogos originales:
 * un laboratorio por defecto para los exámenes que no tienen, la unidad de
 * los medicamentos, y los catálogos informativos de estados de cita y roles.
 * Solo agrega lo que falta; no modifica lo que el administrador ya editó.
 */
@Component
@Order(20)
public class CatalogosMantenimientoIniciales implements CommandLineRunner {

    @Autowired
    private LaboratorioRepository laboratorioRepository;

    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private EstadoCitaCatalogoRepository estadoCitaRepository;

    @Autowired
    private RolCatalogoRepository rolRepository;

    private static final Map<EstadoCita, String[]> ESTADOS = Map.of(
            EstadoCita.PENDIENTE_PAGO, new String[]{"Pendiente de pago", "Cita agendada, esperando el pago."},
            EstadoCita.CONFIRMADA, new String[]{"Confirmada", "El pago se registró y la cita está confirmada."},
            EstadoCita.PACIENTE_PRESENTE, new String[]{"Paciente presente", "El paciente llegó y espera ser atendido."},
            EstadoCita.SIGNOS_VITALES, new String[]{"Signos vitales", "Enfermería está tomando los signos vitales."},
            EstadoCita.EN_CONSULTA, new String[]{"En consulta", "El médico está atendiendo al paciente."},
            EstadoCita.EVALUADO_PENDIENTE_CIERRE, new String[]{"Evaluado - pendiente de cierre", "La consulta terminó y falta cerrar la atención."},
            EstadoCita.ATENCION_FINALIZADA, new String[]{"Atención finalizada", "La atención del paciente terminó por completo."},
            EstadoCita.NO_ASISTIO, new String[]{"No asistió", "El paciente no se presentó a su cita."},
            EstadoCita.COMPLETADA, new String[]{"Completada", "Estado de cierre general de la cita."},
            EstadoCita.CANCELADA, new String[]{"Cancelada", "La cita fue cancelada manual o automáticamente."});

    private static final Map<Rol, String> ROLES = Map.of(
            Rol.ADMINISTRADOR, "Configura catálogos, usuarios y sedes del sistema.",
            Rol.MEDICO, "Atiende consultas, genera órdenes y recetas, y gestiona su agenda.",
            Rol.PACIENTE, "Agenda y paga sus citas desde el portal.",
            Rol.CAJERO, "Cobra consultas y órdenes de laboratorio en caja.",
            Rol.RECEPCIONISTA, "Verifica la llegada de pacientes y registra citas presenciales.",
            Rol.ENFERMERO, "Toma los signos vitales de los pacientes.",
            Rol.LABORATORISTA, "Procesa órdenes de laboratorio y publica resultados.",
            Rol.FARMACEUTICO, "Despacha medicamentos y controla el inventario de farmacia.");

    @Override
    public void run(String... args) {
        // Laboratorio por defecto para los exámenes que todavía no tienen uno
        if (laboratorioRepository.count() == 0) {
            laboratorioRepository.save(new Laboratorio("Laboratorio Central", "Laboratorio clínico de la clínica."));
        }
        Laboratorio porDefecto = laboratorioRepository.findAll().stream().filter(Laboratorio::isActivo).findFirst().orElse(null);
        if (porDefecto != null) {
            for (Examen e : examenRepository.findAll()) {
                if (e.getLaboratorio() == null) {
                    e.setLaboratorio(porDefecto);
                    examenRepository.save(e);
                }
            }
        }

        // Unidad de los medicamentos que todavía no la tienen
        for (Medicamento m : medicamentoRepository.findAll()) {
            if (m.getUnidad() == null || m.getUnidad().isBlank()) {
                String nombre = m.getNombre().toLowerCase();
                m.setUnidad(nombre.contains("inhalador") ? "inhalador" : nombre.contains("sales") ? "sobre" : "tableta");
                medicamentoRepository.save(m);
            }
        }

        if (estadoCitaRepository.count() == 0) {
            for (EstadoCita e : EstadoCita.values()) {
                String[] d = ESTADOS.get(e);
                estadoCitaRepository.save(new EstadoCitaCatalogo(d[0], d[1]));
            }
        }

        if (rolRepository.count() == 0) {
            for (Rol r : Rol.values()) {
                String nombre = r.name().charAt(0) + r.name().substring(1).toLowerCase();
                nombre = nombre.equals("Medico") ? "Médico" : nombre.equals("Farmaceutico") ? "Farmacéutico" : nombre;
                rolRepository.save(new RolCatalogo(nombre, ROLES.get(r)));
            }
        }
        // Corrige los nombres que se sembraron sin tilde en una versión anterior (solo si no fueron editados)
        for (RolCatalogo r : rolRepository.findAll()) {
            if (r.getNombre().equals("Medico")) {
                r.setNombre("Médico");
                rolRepository.save(r);
            } else if (r.getNombre().equals("Farmaceutico")) {
                r.setNombre("Farmacéutico");
                rolRepository.save(r);
            }
        }
    }
}
