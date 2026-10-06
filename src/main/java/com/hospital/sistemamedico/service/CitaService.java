package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio con la lógica de negocio relacionada con las citas médicas.
 * Cubre: CU-03 (agendar cita), CU-05 (recepción: registrar llegada y
 * reasignar médico), CU-06 (cancelación automática de citas vencidas)
 * CU-07 (transición hacia/desde toma de signos vitales) y CU-08 (inicio de
 * consulta, no asistió, evaluada y cierre de la atención).
 */
@Service
public class CitaService {

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private com.hospital.sistemamedico.repository.SignosVitalesRepository signosVitalesRepository;

    @Autowired
    private com.hospital.sistemamedico.repository.ConsultaRepository consultaRepository;

    @Autowired
    private EmailService emailService;

    /** Estados de una cita que ya no ocupan el horario del médico. */
    private static final List<EstadoCita> ESTADOS_QUE_LIBERAN_HORARIO = List.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    /** Horario de atención usado para ofrecer citas de seguimiento (hasta que exista la agenda médica, CU-16). */
    private static final java.time.LocalTime PRIMERA_HORA = java.time.LocalTime.of(8, 0);
    private static final java.time.LocalTime ULTIMA_HORA = java.time.LocalTime.of(16, 30);

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private EspecialidadService especialidadService;

    /**
     * Agenda una nueva cita médica (CU-03). Valida que el paciente y el médico
     * tengan los roles correctos, que el médico realmente atienda esa
     * especialidad y sucursal, que no exista ya otra cita para ese médico en
     * el mismo horario, que la fecha sea futura y que el motivo tenga una
     * longitud válida. La cita queda creada con estado PENDIENTE_PAGO.
     *
     * @param pacienteId id del usuario que agenda la cita (debe tener rol PACIENTE)
     * @param medicoId id del médico seleccionado (debe tener rol MEDICO)
     * @param sucursalId id de la sucursal donde se atenderá
     * @param especialidadId id de la especialidad de la consulta
     * @param fechaHora fecha y hora de la cita (debe ser futura)
     * @param motivoConsulta texto describiendo el motivo de la consulta (10-2000 caracteres)
     * @param emergencia true si el paciente marcó la cita como emergencia
     * @param documentoAdjunto nombre del archivo PDF ya subido (puede ser null si no adjuntó nada)
     * @param agendadaPorPaciente true si la agenda el propio paciente desde el portal;
     *        false si es una cita "walk-in" creada por personal interno (recepción/caja),
     *        ya que estas últimas no se cancelan automáticamente por falta de pago
     * @return la Cita recién creada y guardada
     * @throws IllegalArgumentException si cualquiera de las validaciones anteriores falla
     */
    public Cita agendarCita(Long pacienteId, Long medicoId, Long sucursalId, Long especialidadId,
                            java.time.LocalDateTime fechaHora, String motivoConsulta, boolean emergencia,
                            String documentoAdjunto, boolean agendadaPorPaciente) {
        return agendarCita(pacienteId, medicoId, sucursalId, especialidadId, fechaHora, motivoConsulta,
                emergencia, documentoAdjunto, agendadaPorPaciente, null);
    }

    /**
     * Igual que el método anterior, pero permite indicar que la cita es de
     * seguimiento de otra (CU-08, FA02): el médico la agenda para su paciente
     * desde la sección "Evaluados" del panel médico.
     *
     * @param citaOrigenId id de la cita original de la que proviene el seguimiento (null si no es de seguimiento)
     * @throws IllegalArgumentException además de las validaciones normales, si la cita
     *         original no existe o no pertenece al paciente indicado
     */
    public Cita agendarCita(Long pacienteId, Long medicoId, Long sucursalId, Long especialidadId,
                            java.time.LocalDateTime fechaHora, String motivoConsulta, boolean emergencia,
                            String documentoAdjunto, boolean agendadaPorPaciente, Long citaOrigenId) {

        if (citaOrigenId != null) {
            Cita origen = buscarPorId(citaOrigenId);
            if (!origen.getPaciente().getId().equals(pacienteId)) {
                throw new IllegalArgumentException("La cita de origen no pertenece al paciente indicado.");
            }
        }

        Usuario paciente = usuarioService.buscarPorId(pacienteId);
        if (paciente.getRol() != Rol.PACIENTE) {
            throw new IllegalArgumentException("El usuario indicado no es un paciente.");
        }

        Usuario medico = usuarioService.buscarPorId(medicoId);
        if (medico.getRol() != Rol.MEDICO) {
            throw new IllegalArgumentException("El usuario indicado no es un médico.");
        }

        Sucursal sucursal = sucursalService.buscarPorId(sucursalId);
        Especialidad especialidad = especialidadService.buscarPorId(especialidadId);

        // El médico debe realmente pertenecer a la especialidad y sucursal seleccionadas
        if (medico.getEspecialidad() == null || !medico.getEspecialidad().getId().equals(especialidadId)) {
            throw new IllegalArgumentException("El médico no pertenece a la especialidad indicada.");
        }
        if (medico.getSucursal() == null || !medico.getSucursal().getId().equals(sucursalId)) {
            throw new IllegalArgumentException("El médico no atiende en esa sucursal.");
        }

        // RN-CU03-05: la fecha y hora deben ser futuras
        if (fechaHora == null || !fechaHora.isAfter(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.");
        }

        // RN-CU03-03: longitud del motivo de consulta
        if (motivoConsulta == null || motivoConsulta.isBlank()) {
            throw new IllegalArgumentException("El motivo debe contener entre 10 y 2000 caracteres. Usted ingresó 0 caracteres.");
        }
        int longitudMotivo = motivoConsulta.trim().length();
        if (longitudMotivo < 10 || longitudMotivo > 2000) {
            throw new IllegalArgumentException("El motivo debe contener entre 10 y 2000 caracteres. Usted ingresó " + longitudMotivo + " caracteres.");
        }

        // Evita que un médico tenga dos citas distintas exactamente en el mismo horario
        if (citaRepository.existsByMedicoIdAndFechaHoraAndEstadoNotIn(medicoId, fechaHora, ESTADOS_QUE_LIBERAN_HORARIO)) {
            if (citaOrigenId != null) {
                // CU-12 FA01: el horario se ocupó entre la selección y la confirmación
                throw new IllegalArgumentException("El horario seleccionado ya no está disponible. Por favor, elija otro horario.");
            }
            throw new IllegalArgumentException("El médico ya tiene una cita agendada en ese horario.");
        }

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setSucursal(sucursal);
        cita.setEspecialidad(especialidad);
        cita.setFechaHora(fechaHora);
        cita.setMotivoConsulta(motivoConsulta);
        cita.setEmergencia(emergencia);
        cita.setEstado(EstadoCita.PENDIENTE_PAGO);
        cita.setDocumentoAdjunto(documentoAdjunto);
        cita.setAgendadaPorPaciente(agendadaPorPaciente);
        cita.setFechaCreacion(java.time.LocalDateTime.now());
        cita.setCitaOrigenId(citaOrigenId);

        return citaRepository.save(cita);
    }

    /**
     * Cambia el estado de una cita a CONFIRMADA. Se llama automáticamente
     * desde PagoService justo después de registrar un pago exitoso (CU-04, CU-06).
     *
     * @param citaId id de la cita a confirmar
     * @return la Cita actualizada
     */
    public Cita confirmarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.CONFIRMADA);
        return citaRepository.save(cita);
    }

    /**
     * Registra la llegada del paciente a recepción (CU-05), cambiando el
     * estado de la cita de CONFIRMADA a PACIENTE_PRESENTE.
     *
     * @param citaId id de la cita
     * @return la Cita actualizada
     * @throws IllegalArgumentException si la cita no está en estado CONFIRMADA
     */
    public Cita marcarPacientePresente(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.CONFIRMADA) {
            throw new IllegalArgumentException("La cita debe estar confirmada antes de marcar la presencia del paciente.");
        }
        cita.setEstado(EstadoCita.PACIENTE_PRESENTE);
        return citaRepository.save(cita);
    }

    /**
     * Cambia el estado de una cita a CANCELADA. Se usa tanto cuando el
     * paciente/personal cancela manualmente, como cuando expira el
     * temporizador de reserva de 5 minutos en la pantalla de pago (CU-03/CU-04).
     *
     * @param citaId id de la cita a cancelar
     * @return la Cita actualizada
     */
    public Cita cancelarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.CANCELADA);
        return citaRepository.save(cita);
    }

    /**
     * Llama al paciente para iniciar la toma de signos vitales (CU-07),
     * cambiando el estado de la cita de PACIENTE_PRESENTE a SIGNOS_VITALES.
     *
     * @param citaId id de la cita
     * @return la Cita actualizada
     * @throws IllegalArgumentException si la cita no está en estado PACIENTE_PRESENTE
     */
    public Cita llamarParaSignosVitales(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.PACIENTE_PRESENTE) {
            throw new IllegalArgumentException("La cita debe estar en estado 'Paciente Presente' para llamar al paciente.");
        }
        cita.setEstado(EstadoCita.SIGNOS_VITALES);
        return citaRepository.save(cita);
    }

    /**
     * Marca que ya se completó la toma de signos vitales de una cita (CU-07),
     * devolviendo su estado a PACIENTE_PRESENTE (queda de nuevo en la sala de
     * espera, ahora lista para que la atienda el médico). Se llama desde
     * SignosVitalesService justo después de guardar el registro de signos vitales.
     *
     * @param citaId id de la cita
     * @param emergencia true si durante la toma de signos vitales se detectó
     *        o confirmó que el caso es una emergencia (se conserva o activa la
     *        prioridad de emergencia de la cita)
     * @return la Cita actualizada
     */
    public Cita completarSignosVitales(Long citaId, boolean emergencia) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.PACIENTE_PRESENTE);
        cita.setEmergencia(emergencia || cita.isEmergencia());
        return citaRepository.save(cita);
    }

    /**
     * Indica si la cita está "En Espera de Consulta" (CU-08): el paciente ya
     * pasó por signos vitales (CU-07) y regresó a la sala de espera, es decir,
     * está en PACIENTE_PRESENTE y tiene signos vitales registrados.
     *
     * @param cita cita a evaluar
     * @return true si el médico ya puede iniciar la consulta
     */
    public boolean estaEnEsperaDeConsulta(Cita cita) {
        return cita.getEstado() == EstadoCita.PACIENTE_PRESENTE && signosVitalesRepository.existsByCitaId(cita.getId());
    }

    /**
     * Verifica que el médico indicado sea el asignado a la cita (CU-08).
     *
     * @throws IllegalArgumentException si la cita pertenece a otro médico
     */
    public void validarMedicoDeLaCita(Cita cita, Long medicoId) {
        if (medicoId == null || !cita.getMedico().getId().equals(medicoId)) {
            throw new IllegalArgumentException("La cita no está asignada a este médico.");
        }
    }

    /**
     * Inicia la consulta médica (CU-08, paso 2): la cita pasa de "En Espera" a
     * EN_CONSULTA. El anuncio por voz (TTS) lo realiza el frontend.
     *
     * @param citaId id de la cita
     * @param medicoId id del médico que inicia la consulta (debe ser el asignado)
     * @return la Cita actualizada
     * @throws IllegalArgumentException si la cita no está en espera de consulta
     *         (por ejemplo, no tiene signos vitales registrados) o es de otro médico
     */
    public Cita iniciarConsulta(Long citaId, Long medicoId) {
        Cita cita = buscarPorId(citaId);
        validarMedicoDeLaCita(cita, medicoId);
        if (!estaEnEsperaDeConsulta(cita)) {
            throw new IllegalArgumentException("La cita debe estar en espera de consulta, con los signos vitales ya registrados.");
        }
        cita.setEstado(EstadoCita.EN_CONSULTA);
        return citaRepository.save(cita);
    }

    /**
     * Marca que el paciente no se presentó cuando fue llamado a consulta
     * (CU-08, FA06). La cita queda cerrada en estado NO_ASISTIO.
     *
     * @param citaId id de la cita
     * @param medicoId id del médico (debe ser el asignado)
     * @return la Cita actualizada
     * @throws IllegalArgumentException si la cita no está en espera de consulta o es de otro médico
     */
    public Cita marcarNoAsistio(Long citaId, Long medicoId) {
        Cita cita = buscarPorId(citaId);
        validarMedicoDeLaCita(cita, medicoId);
        if (!estaEnEsperaDeConsulta(cita)) {
            throw new IllegalArgumentException("Solo se puede marcar 'No Asistió' a un paciente en espera de consulta.");
        }
        cita.setEstado(EstadoCita.NO_ASISTIO);
        return citaRepository.save(cita);
    }

    /**
     * Pasa la cita a "Evaluados - Pendiente de cierre" cuando el médico finaliza
     * la consulta (CU-08, paso 9). Lo llama ConsultaService.
     *
     * @param citaId id de la cita
     * @return la Cita actualizada
     */
    public Cita marcarEvaluada(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.EVALUADO_PENDIENTE_CIERRE);
        return citaRepository.save(cita);
    }

    /**
     * Cierra la atención de la cita (CU-08, pasos 11 y 12): de
     * EVALUADO_PENDIENTE_CIERRE a ATENCION_FINALIZADA.
     *
     * @param citaId id de la cita
     * @param medicoId id del médico (debe ser el asignado)
     * @return la Cita actualizada
     * @throws IllegalArgumentException si la consulta aún no fue finalizada o la cita es de otro médico
     */
    public Cita finalizarAtencion(Long citaId, Long medicoId) {
        Cita cita = buscarPorId(citaId);
        validarMedicoDeLaCita(cita, medicoId);
        if (cita.getEstado() != EstadoCita.EVALUADO_PENDIENTE_CIERRE) {
            throw new IllegalArgumentException("Solo se puede finalizar la atención de una cita cuya consulta ya fue finalizada.");
        }
        cita.setEstado(EstadoCita.ATENCION_FINALIZADA);
        return citaRepository.save(cita);
    }

    /**
     * Reasigna el médico de una cita ya confirmada o con el paciente presente
     * (CU-05, FA07). Solo permite elegir un médico que pertenezca exactamente
     * a la misma especialidad y sucursal que la cita original, para no romper
     * el motivo por el que el paciente eligió esa cita.
     *
     * @param citaId id de la cita a reasignar
     * @param nuevoMedicoId id del nuevo médico
     * @return la Cita actualizada con el nuevo médico
     * @throws IllegalArgumentException si la cita no está en un estado válido para
     *         reasignar, si el nuevo usuario no es médico, o si no coincide en
     *         especialidad/sucursal con la cita
     */
    public Cita reasignarMedico(Long citaId, Long nuevoMedicoId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.CONFIRMADA && cita.getEstado() != EstadoCita.PACIENTE_PRESENTE) {
            throw new IllegalArgumentException("Solo se puede reasignar el médico de citas en estado Confirmada o Paciente Presente.");
        }
        Usuario nuevoMedico = usuarioService.buscarPorId(nuevoMedicoId);
        if (nuevoMedico.getRol() != Rol.MEDICO) {
            throw new IllegalArgumentException("El usuario indicado no es un médico.");
        }
        if (nuevoMedico.getEspecialidad() == null || !nuevoMedico.getEspecialidad().getId().equals(cita.getEspecialidad().getId())) {
            throw new IllegalArgumentException("El médico seleccionado no pertenece a la especialidad de la cita.");
        }
        if (nuevoMedico.getSucursal() == null || !nuevoMedico.getSucursal().getId().equals(cita.getSucursal().getId())) {
            throw new IllegalArgumentException("El médico seleccionado no pertenece a la sucursal de la cita.");
        }
        cita.setMedico(nuevoMedico);
        return citaRepository.save(cita);
    }

    // ------------------------------------------------------------------ CU-12

    /**
     * Datos que se pre-cargan al agendar un seguimiento desde una consulta
     * (CU-12, paso 2): paciente, médico, especialidad y sucursal de la cita de
     * la consulta padre.
     *
     * @param consultaId id de la consulta médica padre
     * @throws IllegalArgumentException si la consulta no existe
     */
    public java.util.Map<String, Object> contextoSeguimiento(Long consultaId) {
        Consulta consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new IllegalArgumentException("Consulta médica no encontrada."));
        Cita origen = consulta.getCita();
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("consultaId", consulta.getId());
        m.put("citaOrigenId", origen.getId());
        m.put("pacienteId", origen.getPaciente().getId());
        m.put("paciente", origen.getPaciente().getNombreCompleto());
        m.put("medicoId", origen.getMedico().getId());
        m.put("medico", origen.getMedico().getNombreCompleto());
        m.put("especialidad", origen.getEspecialidad().getNombre());
        m.put("sucursal", origen.getSucursal().getNombre());
        m.put("tipos", java.util.Arrays.stream(TipoSeguimiento.values())
                .map(t -> java.util.Map.of("valor", t.name(), "etiqueta", t.getEtiqueta())).toList());
        m.put("prioridades", java.util.Arrays.stream(PrioridadSeguimiento.values())
                .map(t -> java.util.Map.of("valor", t.name(), "etiqueta", t.getEtiqueta())).toList());
        return m;
    }

    /**
     * Calendario de disponibilidad de un médico en un día (CU-12, paso 5):
     * horarios cada 30 minutos entre las 8:00 y las 16:30, marcando los que ya
     * pasaron o están ocupados por otra cita vigente.
     *
     * @param medicoId id del médico
     * @param fecha día a consultar (AAAA-MM-DD)
     * @return lista de horarios con su indicador de disponibilidad
     * @throws IllegalArgumentException si el médico no existe o la fecha no es válida
     */
    public List<java.util.Map<String, Object>> disponibilidad(Long medicoId, String fecha) {
        Usuario medico = usuarioService.buscarPorId(medicoId);
        if (medico.getRol() != Rol.MEDICO) {
            throw new IllegalArgumentException("El usuario indicado no es un médico.");
        }
        java.time.LocalDate dia;
        try {
            dia = java.time.LocalDate.parse(fecha);
        } catch (Exception e) {
            throw new IllegalArgumentException("La fecha indicada no es válida.");
        }
        java.util.Set<java.time.LocalDateTime> ocupados = citaRepository
                .findByMedicoIdAndFechaHoraBetween(medicoId, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                .filter(c -> !ESTADOS_QUE_LIBERAN_HORARIO.contains(c.getEstado()))
                .map(Cita::getFechaHora)
                .collect(java.util.stream.Collectors.toSet());

        List<java.util.Map<String, Object>> horarios = new java.util.ArrayList<>();
        for (java.time.LocalTime h = PRIMERA_HORA; !h.isAfter(ULTIMA_HORA); h = h.plusMinutes(30)) {
            java.time.LocalDateTime momento = dia.atTime(h);
            java.util.Map<String, Object> slot = new java.util.LinkedHashMap<>();
            slot.put("hora", h.toString());
            slot.put("fechaHora", momento.toString());
            slot.put("disponible", momento.isAfter(java.time.LocalDateTime.now()) && !ocupados.contains(momento));
            horarios.add(slot);
        }
        return horarios;
    }

    /**
     * Agenda una cita de seguimiento desde una consulta médica (CU-12). La cita
     * se crea para el mismo paciente, médico, especialidad y sucursal de la cita
     * original, y queda pendiente de pago. Al guardarla se notifica al paciente
     * por correo (RN-CU11-04); el recordatorio lo envía luego la tarea programada
     * (RN-CU11-05).
     *
     * @param citaOrigenId id de la cita cuya consulta origina el seguimiento
     * @param medicoId id del médico que agenda (debe ser el de la cita original)
     * @param tipo MONITOREO_TRATAMIENTO o REVISION_RESULTADOS_LABORATORIO
     * @param fechaHora fecha y hora elegidas, futuras y dentro de los horarios disponibles
     * @param observaciones motivo del seguimiento (10 a 2000 caracteres)
     * @param prioridad ALTA, MEDIA o BAJA (opcional; MEDIA por defecto)
     * @return la cita de seguimiento creada
     * @throws IllegalArgumentException si la consulta no está activa o recién finalizada, falta o es
     *         inválido algún dato (RN-CU11-01 a 03) o el horario ya fue ocupado (FA01)
     */
    public Cita agendarSeguimiento(Long citaOrigenId, Long medicoId, String tipo, java.time.LocalDateTime fechaHora,
                                   String observaciones, String prioridad) {
        Cita origen = buscarPorId(citaOrigenId);
        validarMedicoDeLaCita(origen, medicoId);
        if (origen.getEstado() != EstadoCita.EN_CONSULTA && origen.getEstado() != EstadoCita.EVALUADO_PENDIENTE_CIERRE) {
            throw new IllegalArgumentException("Solo se puede agendar un seguimiento desde una consulta activa o recién finalizada.");
        }
        if (consultaRepository.findByCitaId(citaOrigenId).isEmpty()) {
            throw new IllegalArgumentException("Guarde la consulta antes de agendar un seguimiento.");
        }

        // RN-CU11-01
        TipoSeguimiento tipoSeguimiento;
        try {
            tipoSeguimiento = TipoSeguimiento.valueOf(tipo == null ? "" : tipo.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de seguimiento.");
        }

        // RN-CU11-02
        if (fechaHora == null || !fechaHora.isAfter(java.time.LocalDateTime.now()) || !esHorarioDeAtencion(fechaHora)) {
            throw new IllegalArgumentException("Seleccione una fecha futura dentro de los horarios disponibles del médico.");
        }

        // RN-CU11-03
        String texto = observaciones == null ? "" : observaciones.trim();
        if (texto.length() < 10 || texto.length() > 2000) {
            throw new IllegalArgumentException("Las observaciones son obligatorias. Deben contener entre 10 y 2000 caracteres.");
        }

        PrioridadSeguimiento prioridadSeguimiento = PrioridadSeguimiento.MEDIA;
        if (prioridad != null && !prioridad.isBlank()) {
            try {
                prioridadSeguimiento = PrioridadSeguimiento.valueOf(prioridad.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("La prioridad indicada no es válida.");
            }
        }

        Cita cita = agendarCita(origen.getPaciente().getId(), origen.getMedico().getId(), origen.getSucursal().getId(),
                origen.getEspecialidad().getId(), fechaHora, texto, false, null, false, origen.getId());
        cita.setTipoSeguimiento(tipoSeguimiento);
        cita.setPrioridad(prioridadSeguimiento);
        Cita guardada = citaRepository.save(cita);

        emailService.enviarNotificacionSeguimiento(guardada.getPaciente().getCorreo(), guardada.getPaciente().getNombreCompleto(),
                guardada.getMedico().getNombreCompleto(), guardada.getSucursal().getNombre(), guardada.getFechaHora(),
                tipoSeguimiento.getEtiqueta(), texto);
        return guardada;
    }

    /** true si la hora cae en un horario de atención: cada 30 minutos entre las 8:00 y las 16:30. */
    private boolean esHorarioDeAtencion(java.time.LocalDateTime fechaHora) {
        java.time.LocalTime h = fechaHora.toLocalTime();
        return fechaHora.getSecond() == 0 && fechaHora.getNano() == 0 && h.getMinute() % 30 == 0
                && !h.isBefore(PRIMERA_HORA) && !h.isAfter(ULTIMA_HORA);
    }

    /**
     * Tarea programada que envía el recordatorio de las citas de seguimiento
     * próximas (RN-CU11-05): las que ocurren dentro de las siguientes 48 horas
     * y aún no tienen recordatorio. No se envía si la cita fue cancelada. Como
     * el estado "recordatorio enviado" se guarda en la base de datos, el proceso
     * no se pierde si el sistema se reinicia (RNF-020), y si el envío falla se
     * reintenta en la siguiente ejecución.
     */
    @org.springframework.scheduling.annotation.Scheduled(initialDelay = 60000, fixedRate = 600000)
    public void enviarRecordatoriosSeguimiento() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        List<Cita> proximas = citaRepository
                .findByTipoSeguimientoIsNotNullAndRecordatorioEnviadoFalseAndFechaHoraBetween(ahora, ahora.plusHours(48));
        for (Cita cita : proximas) {
            if (cita.getEstado() == EstadoCita.CANCELADA || cita.getEstado() == EstadoCita.NO_ASISTIO) {
                continue;
            }
            boolean enviado = emailService.enviarRecordatorioSeguimiento(cita.getPaciente().getCorreo(),
                    cita.getPaciente().getNombreCompleto(), cita.getMedico().getNombreCompleto(),
                    cita.getSucursal().getNombre(), cita.getFechaHora(), cita.getTipoSeguimiento().getEtiqueta());
            if (enviado) {
                cita.setRecordatorioEnviado(true);
                citaRepository.save(cita);
            }
        }
    }

    /**
     * Busca una cita por su id.
     *
     * @param id id de la cita
     * @return la Cita encontrada
     * @throws IllegalArgumentException si no existe ninguna cita con ese id
     */
    public Cita buscarPorId(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada."));
    }

    /**
     * Lista todas las citas de un paciente específico, sin importar su estado.
     * Usado en la pantalla "Mis Citas" del paciente.
     *
     * @param pacienteId id del paciente
     * @return lista de citas de ese paciente (puede estar vacía)
     */
    public List<Cita> listarPorPaciente(Long pacienteId) {
        return citaRepository.findByPacienteId(pacienteId);
    }

    /**
     * Lista todas las citas asignadas a un médico específico.
     *
     * @param medicoId id del médico
     * @return lista de citas de ese médico (puede estar vacía)
     */
    public List<Cita> listarPorMedico(Long medicoId) {
        return citaRepository.findByMedicoId(medicoId);
    }

    /**
     * Lista absolutamente todas las citas del sistema, sin filtrar.
     * Usado internamente, por ejemplo, en el panel de enfermería (CU-07)
     * para armar la lista de pacientes en espera.
     *
     * @return lista completa de citas
     */
    public List<Cita> listarTodas() {
        return citaRepository.findAll();
    }

    /**
     * Tarea programada que se ejecuta automáticamente cada 60 segundos
     * (CU-06, RN implícita de cancelación automática). Busca todas las citas
     * en estado PENDIENTE_PAGO que fueron agendadas directamente por el
     * paciente (no las walk-in creadas por personal interno) y que llevan
     * más de 10 minutos creadas sin haberse pagado, y las cancela
     * automáticamente para liberar el horario del médico.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void cancelarCitasPendientesVencidas() {
        java.time.LocalDateTime limite = java.time.LocalDateTime.now().minusMinutes(10);
        List<Cita> pendientes = citaRepository.findByEstado(EstadoCita.PENDIENTE_PAGO);
        for (Cita cita : pendientes) {
            if (cita.isAgendadaPorPaciente() && cita.getFechaCreacion() != null && cita.getFechaCreacion().isBefore(limite)) {
                cita.setEstado(EstadoCita.CANCELADA);
                citaRepository.save(cita);
            }
        }
    }
}