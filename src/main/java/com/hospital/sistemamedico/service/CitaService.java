package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public Cita agendarCita(Long pacienteId, Long medicoId, Long sucursalId, Long especialidadId,
                            java.time.LocalDateTime fechaHora, String motivoConsulta, boolean emergencia,
                            String documentoAdjunto) {

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

        if (medico.getEspecialidad() == null || !medico.getEspecialidad().getId().equals(especialidadId)) {
            throw new IllegalArgumentException("El médico no pertenece a la especialidad indicada.");
        }
        if (medico.getSucursal() == null || !medico.getSucursal().getId().equals(sucursalId)) {
            throw new IllegalArgumentException("El médico no atiende en esa sucursal.");
        }

        // RN-CU03-05: Fecha y Hora obligatoria y futura
        if (fechaHora == null || !fechaHora.isAfter(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.");
        }

        // RN-CU03-03: Motivo de Visita
        if (motivoConsulta == null || motivoConsulta.isBlank()) {
            throw new IllegalArgumentException("El motivo debe contener entre 10 y 2000 caracteres. Usted ingresó 0 caracteres.");
        }
        int longitudMotivo = motivoConsulta.trim().length();
        if (longitudMotivo < 10 || longitudMotivo > 2000) {
            throw new IllegalArgumentException("El motivo debe contener entre 10 y 2000 caracteres. Usted ingresó " + longitudMotivo + " caracteres.");
        }

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
        cita.setDocumentoAdjunto(documentoAdjunto);
        cita.setEstado(EstadoCita.PENDIENTE_PAGO);

        return citaRepository.save(cita);
    }

    public Cita confirmarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.CONFIRMADA);
        return citaRepository.save(cita);
    }

    public Cita marcarPacientePresente(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.CONFIRMADA) {
            throw new IllegalArgumentException("La cita debe estar confirmada antes de marcar la presencia del paciente.");
        }
        cita.setEstado(EstadoCita.PACIENTE_PRESENTE);
        return citaRepository.save(cita);
    }

    public Cita cancelarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.CANCELADA);
        return citaRepository.save(cita);
    }

    public Cita buscarPorId(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada."));
    }

    public List<Cita> listarPorPaciente(Long pacienteId) {
        return citaRepository.findByPacienteId(pacienteId);
    }

    public List<Cita> listarPorMedico(Long medicoId) {
        return citaRepository.findByMedicoId(medicoId);
    }

    public List<Cita> listarTodas() {
        return citaRepository.findAll();
    }
    public Cita llamarParaSignosVitales(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.PACIENTE_PRESENTE) {
            throw new IllegalArgumentException("La cita debe estar en estado 'Paciente Presente' para llamar al paciente.");
        }
        cita.setEstado(EstadoCita.SIGNOS_VITALES);
        return citaRepository.save(cita);
    }
    public Cita completarSignosVitales(Long citaId, boolean emergencia) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.PACIENTE_PRESENTE);
        cita.setEmergencia(emergencia || cita.isEmergencia());
        return citaRepository.save(cita);
    }
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
}