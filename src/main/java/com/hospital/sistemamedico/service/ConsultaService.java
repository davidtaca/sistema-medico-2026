package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio con la lógica de negocio de la consulta médica (CU-08): registro
 * de la consulta (motivo, hallazgos, CIE-10, diagnóstico, plan), órdenes de
 * laboratorio (FA01), recetas médicas (FA04) y el historial clínico del
 * paciente. Los cambios de estado de la cita (iniciar consulta, no asistió,
 * cierre de atención) viven en CitaService.
 */
@Service
public class ConsultaService {

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private ConsultaHistorialRepository consultaHistorialRepository;

    @Autowired
    private Cie10Repository cie10Repository;

    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private OrdenLaboratorioRepository ordenLaboratorioRepository;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private CitaService citaService;

    /**
     * Guarda el formulario de consulta médica (CU-08, pasos 4 a 9). Si el
     * estado indicado es "En curso" solo exige el motivo de la visita; si es
     * "Finalizada" aplica todas las reglas de cierre (RN-CU08-01 y RN-CU08-02)
     * y la cita pasa a "Evaluados - Pendiente de cierre". Cada guardado crea
     * una nueva versión en el historial de auditoría (RNF-026).
     *
     * @param citaId id de la cita (debe estar EN_CONSULTA)
     * @param medicoId id del médico asignado a la cita
     * @param datos campos del formulario: motivoVisita, hallazgosClinicos, codigoCie10,
     *        diagnostico, planTratamiento, notasAdicionales
     * @param finalizar true si el médico eligió el estado "Finalizada"
     * @return la Consulta guardada
     * @throws IllegalArgumentException si la cita no está en consulta, el médico no es el asignado,
     *         falta el motivo, el código CIE-10 no existe o, al finalizar, falta el diagnóstico
     *         (FA05) o algún campo obligatorio
     */
    @Transactional
    public Consulta guardar(Long citaId, Long medicoId, Map<String, Object> datos, boolean finalizar) {
        Cita cita = citaService.buscarPorId(citaId);
        citaService.validarMedicoDeLaCita(cita, medicoId);
        if (cita.getEstado() != EstadoCita.EN_CONSULTA) {
            throw new IllegalArgumentException("La cita debe estar en consulta médica para registrar la consulta.");
        }

        String motivo = texto(datos.get("motivoVisita"));
        String hallazgos = texto(datos.get("hallazgosClinicos"));
        String codigoCie10 = texto(datos.get("codigoCie10"));
        String diagnostico = texto(datos.get("diagnostico"));
        String plan = texto(datos.get("planTratamiento"));
        String notas = texto(datos.get("notasAdicionales"));

        if (motivo.isEmpty()) {
            throw new IllegalArgumentException("El motivo de la visita es obligatorio.");
        }
        if (motivo.length() > 2000) {
            throw new IllegalArgumentException("El motivo de la visita no puede exceder 2000 caracteres.");
        }
        if (hallazgos.length() > 5000 || plan.length() > 5000 || notas.length() > 5000) {
            throw new IllegalArgumentException("Los hallazgos, el plan de tratamiento y las notas no pueden exceder 5000 caracteres.");
        }
        // RN-CU08-01: el CIE-10 es opcional, pero si se indica debe existir en el catálogo
        if (!codigoCie10.isEmpty() && !cie10Repository.existsById(codigoCie10)) {
            throw new IllegalArgumentException("El código CIE-10 indicado no existe en el catálogo.");
        }

        if (finalizar) {
            // FA05: intento de finalizar sin diagnóstico
            if (diagnostico.isEmpty()) {
                throw new IllegalArgumentException("No es posible finalizar la consulta sin registrar un diagnóstico. El campo Diagnóstico es obligatorio.");
            }
            // RN-CU08-01: longitud del diagnóstico
            if (diagnostico.length() < 10 || diagnostico.length() > 5000) {
                throw new IllegalArgumentException("El diagnóstico es obligatorio. Debe contener entre 10 y 5000 caracteres.");
            }
            // RN-CU08-02: campos obligatorios para cerrar
            if (hallazgos.isEmpty() || plan.isEmpty()) {
                throw new IllegalArgumentException("Debe completar todos los campos obligatorios para cerrar la consulta.");
            }
        } else if (diagnostico.length() > 5000) {
            throw new IllegalArgumentException("El diagnóstico es obligatorio. Debe contener entre 10 y 5000 caracteres.");
        }

        Consulta consulta = consultaRepository.findByCitaId(citaId).orElse(null);
        if (consulta == null) {
            consulta = new Consulta();
            consulta.setCita(cita);
            consulta.setMedico(cita.getMedico());
        } else {
            consulta.setVersion(consulta.getVersion() + 1);
        }
        consulta.setMotivoVisita(motivo);
        consulta.setHallazgosClinicos(hallazgos);
        consulta.setCodigoCie10(codigoCie10.isEmpty() ? null : codigoCie10);
        consulta.setDiagnostico(diagnostico);
        consulta.setPlanTratamiento(plan);
        consulta.setNotasAdicionales(notas);
        consulta.setFechaActualizacion(LocalDateTime.now());
        if (finalizar) {
            consulta.setFinalizada(true);
            consulta.setFechaFinalizacion(LocalDateTime.now());
        }

        Consulta guardada = consultaRepository.save(consulta);
        consultaHistorialRepository.save(new ConsultaHistorial(guardada));

        if (finalizar) {
            citaService.marcarEvaluada(citaId);
        }
        return guardada;
    }

    /**
     * Genera una orden de laboratorio para la cita (CU-08, FA01). Se puede
     * generar durante la consulta o desde la sección "Evaluados".
     *
     * @param citaId id de la cita
     * @param medicoId id del médico asignado
     * @param examenIds ids de los exámenes del catálogo seleccionados (al menos uno)
     * @param observaciones observaciones para el laboratorio (opcional)
     * @return la OrdenLaboratorio guardada, con su número de orden
     * @throws IllegalArgumentException si la cita no está en consulta ni evaluada, el médico no es el
     *         asignado, no se seleccionó ningún examen o algún examen no existe o está inactivo
     */
    @Transactional
    public OrdenLaboratorio generarOrdenLaboratorio(Long citaId, Long medicoId, List<Long> examenIds, String observaciones) {
        Cita cita = validarCitaParaIndicaciones(citaId, medicoId);

        if (examenIds == null || examenIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un examen de laboratorio.");
        }
        if (observaciones != null && observaciones.length() > 2000) {
            throw new IllegalArgumentException("Las observaciones no pueden exceder 2000 caracteres.");
        }

        List<Examen> examenes = new ArrayList<>();
        for (Long id : examenIds.stream().distinct().toList()) {
            Examen examen = examenRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los exámenes seleccionados no existe."));
            if (!examen.isActivo()) {
                throw new IllegalArgumentException("El examen " + examen.getNombre() + " no está disponible.");
            }
            examenes.add(examen);
        }

        OrdenLaboratorio orden = new OrdenLaboratorio();
        orden.setCita(cita);
        orden.setMedico(cita.getMedico());
        orden.setExamenes(examenes);
        orden.setObservaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim());
        return ordenLaboratorioRepository.save(orden);
    }

    /**
     * Genera una receta médica para la cita (CU-08, FA04).
     *
     * @param citaId id de la cita
     * @param medicoId id del médico asignado
     * @param items medicamentos de la receta; cada uno con medicamentoId, dosis, frecuencia,
     *        duracion e indicaciones (opcional) — RN-CU08-03
     * @return la Receta guardada
     * @throws IllegalArgumentException si la cita no está en consulta ni evaluada, el médico no es el
     *         asignado, la receta no tiene medicamentos, falta algún campo obligatorio o algún
     *         medicamento no existe en el catálogo
     */
    @Transactional
    public Receta generarReceta(Long citaId, Long medicoId, List<Map<String, Object>> items) {
        Cita cita = validarCitaParaIndicaciones(citaId, medicoId);

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debe agregar al menos un medicamento a la receta.");
        }

        Receta receta = new Receta();
        receta.setCita(cita);
        receta.setMedico(cita.getMedico());

        for (Map<String, Object> dato : items) {
            String dosis = texto(dato.get("dosis"));
            String frecuencia = texto(dato.get("frecuencia"));
            String duracion = texto(dato.get("duracion"));
            String indicaciones = texto(dato.get("indicaciones"));
            Object medicamentoId = dato.get("medicamentoId");

            // RN-CU08-03: todos obligatorios excepto indicaciones especiales
            if (medicamentoId == null || texto(medicamentoId).isEmpty() || dosis.isEmpty()
                    || frecuencia.isEmpty() || duracion.isEmpty()) {
                throw new IllegalArgumentException("Todos los campos de la receta son obligatorios excepto indicaciones especiales.");
            }
            if (dosis.length() > 255 || frecuencia.length() > 255 || duracion.length() > 255 || indicaciones.length() > 1000) {
                throw new IllegalArgumentException("Dosis, frecuencia y duración no pueden exceder 255 caracteres; las indicaciones, 1000.");
            }

            Medicamento medicamento = medicamentoRepository.findById(Long.valueOf(texto(medicamentoId)))
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los medicamentos seleccionados no existe en el catálogo."));
            if (!medicamento.isActivo()) {
                throw new IllegalArgumentException("El medicamento " + medicamento.getNombre() + " no está disponible.");
            }

            RecetaItem item = new RecetaItem();
            item.setReceta(receta);
            item.setMedicamento(medicamento);
            item.setDosis(dosis);
            item.setFrecuencia(frecuencia);
            item.setDuracion(duracion);
            item.setIndicaciones(indicaciones.isEmpty() ? null : indicaciones);
            receta.getItems().add(item);
        }
        return recetaRepository.save(receta);
    }

    /**
     * Busca la consulta de una cita.
     *
     * @return la Consulta, o null si el médico aún no la ha guardado
     */
    public Consulta buscarPorCita(Long citaId) {
        return consultaRepository.findByCitaId(citaId).orElse(null);
    }

    /** Historial clínico del paciente: sus consultas finalizadas, la más reciente primero. */
    public List<Consulta> historialDelPaciente(Long pacienteId) {
        return consultaRepository.findByCitaPacienteIdAndFinalizadaTrueOrderByFechaFinalizacionDesc(pacienteId);
    }

    /** Versiones guardadas de una consulta, para auditoría (RNF-026). */
    public List<ConsultaHistorial> versionesDeConsulta(Long consultaId) {
        return consultaHistorialRepository.findByConsultaIdOrderByVersion(consultaId);
    }

    public List<OrdenLaboratorio> ordenesDeCita(Long citaId) {
        return ordenLaboratorioRepository.findByCitaIdOrderByFecha(citaId);
    }

    public List<Receta> recetasDeCita(Long citaId) {
        return recetaRepository.findByCitaIdOrderByFecha(citaId);
    }

    /**
     * Sugerencias de diagnósticos CIE-10 para el autocompletado (RNF-004).
     *
     * @param texto fragmento del código o de la descripción; con menos de 2 caracteres devuelve vacío
     */
    public List<Cie10> buscarCie10(String texto) {
        if (texto == null || texto.trim().length() < 2) {
            return List.of();
        }
        String q = texto.trim();
        return cie10Repository.findTop15ByCodigoContainingIgnoreCaseOrDescripcionContainingIgnoreCaseOrderByCodigo(q, q);
    }

    public List<Examen> listarExamenesActivos() {
        return examenRepository.findByActivoTrueOrderByNombre();
    }

    public List<Medicamento> listarMedicamentosActivos() {
        return medicamentoRepository.findByActivoTrueOrderByNombre();
    }

    /** Las órdenes y recetas solo se generan mientras la cita está en consulta o ya evaluada (pendiente de cierre). */
    private Cita validarCitaParaIndicaciones(Long citaId, Long medicoId) {
        Cita cita = citaService.buscarPorId(citaId);
        citaService.validarMedicoDeLaCita(cita, medicoId);
        if (cita.getEstado() != EstadoCita.EN_CONSULTA && cita.getEstado() != EstadoCita.EVALUADO_PENDIENTE_CIERRE) {
            throw new IllegalArgumentException("Solo se pueden generar órdenes y recetas para citas en consulta o evaluadas.");
        }
        return cita;
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString().trim();
    }
}
