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
 * y CU-07 (transición hacia/desde toma de signos vitales).
 */
@Service
public class CitaService {

    @Autowired
    private CitaRepository citaRepository;

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
        if (citaRepository.existsByMedicoIdAndFechaHora(medicoId, fechaHora)) {
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