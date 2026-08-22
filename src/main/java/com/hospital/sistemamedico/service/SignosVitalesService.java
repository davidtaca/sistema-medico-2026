package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.SignosVitalesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SignosVitalesService {

    @Autowired
    private SignosVitalesRepository signosVitalesRepository;

    @Autowired
    private CitaService citaService;

    @Autowired
    private UsuarioService usuarioService;

    public SignosVitales registrar(Long citaId, Long enfermeroId, Integer presionSistolica, Integer presionDiastolica,
                                   Double temperatura, Double peso, Double talla, Integer frecuenciaCardiaca,
                                   boolean emergencia) {

        Cita cita = citaService.buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.SIGNOS_VITALES) {
            throw new IllegalArgumentException("La cita no está en proceso de toma de signos vitales.");
        }
        if (signosVitalesRepository.existsByCitaId(citaId)) {
            throw new IllegalArgumentException("Esta cita ya tiene signos vitales registrados.");
        }

        Usuario enfermero = usuarioService.buscarPorId(enfermeroId);
        if (enfermero.getRol() != Rol.ENFERMERO) {
            throw new IllegalArgumentException("El usuario indicado no es personal de enfermería.");
        }

        // Validación de rangos de captura (RN-CU07-01 a RN-CU07-05) — bloqueantes
        if (presionSistolica == null || presionSistolica < 60 || presionSistolica > 250) {
            throw new IllegalArgumentException("La presión sistólica debe estar entre 60 y 250 mmHg.");
        }
        if (presionDiastolica == null || presionDiastolica < 40 || presionDiastolica > 150) {
            throw new IllegalArgumentException("La presión diastólica debe estar entre 40 y 150 mmHg.");
        }
        if (temperatura == null || temperatura < 34 || temperatura > 42) {
            throw new IllegalArgumentException("La temperatura debe estar entre 34 y 42 °C.");
        }
        if (peso == null || peso < 0.5 || peso > 300) {
            throw new IllegalArgumentException("El peso debe estar entre 0.5 y 300 kg.");
        }
        if (talla == null || talla < 30 || talla > 250) {
            throw new IllegalArgumentException("La talla debe estar entre 30 y 250 cm.");
        }
        if (frecuenciaCardiaca == null || frecuenciaCardiaca < 30 || frecuenciaCardiaca > 220) {
            throw new IllegalArgumentException("La frecuencia cardíaca debe estar entre 30 y 220 lpm.");
        }

        SignosVitales sv = new SignosVitales();
        sv.setCita(cita);
        sv.setEnfermero(enfermero);
        sv.setPresionSistolica(presionSistolica);
        sv.setPresionDiastolica(presionDiastolica);
        sv.setTemperatura(temperatura);
        sv.setPeso(peso);
        sv.setTalla(talla);
        sv.setFrecuenciaCardiaca(frecuenciaCardiaca);
        sv.setEmergencia(emergencia);

        SignosVitales guardado = signosVitalesRepository.save(sv);

        // El paciente regresa a la sala de espera (o queda marcado con prioridad de emergencia)
        // a la espera de ser llamado por el médico. El CU-08 (consulta médica) usará el campo
        // 'emergencia' guardado en signos_vitales para dar prioridad.
        citaService.completarSignosVitales(citaId, emergencia);

        return guardado;
    }

    public SignosVitales buscarPorCita(Long citaId) {
        return signosVitalesRepository.findByCitaId(citaId)
                .orElseThrow(() -> new IllegalArgumentException("No hay signos vitales registrados para esta cita."));
    }
}
