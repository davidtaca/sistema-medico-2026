package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.SignosVitalesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Servicio con la lógica de negocio de la toma de signos vitales (CU-07).
 * Valida que todos los valores capturados estén dentro de rangos fisiológicos
 * razonables antes de guardarlos (validación bloqueante); las alertas
 * clínicas por valores fuera de lo "normal" (por ejemplo presión alta) se
 * manejan del lado del frontend, ya que no impiden guardar el registro, solo
 * advierten a la enfermera.
 */
@Service
public class SignosVitalesService {

    @Autowired
    private SignosVitalesRepository signosVitalesRepository;

    @Autowired
    private CitaService citaService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Registra los signos vitales de un paciente para una cita específica.
     * La cita debe estar en estado SIGNOS_VITALES (es decir, la enfermera ya
     * llamó al paciente con CitaService.llamarParaSignosVitales). Al terminar,
     * la cita regresa automáticamente al estado PACIENTE_PRESENTE, quedando
     * lista para que la atienda el médico.
     *
     * @param citaId id de la cita
     * @param enfermeroId id del usuario que realiza el registro (debe tener rol ENFERMERO)
     * @param presionSistolica presión arterial sistólica en mmHg (rango válido: 60-250)
     * @param presionDiastolica presión arterial diastólica en mmHg (rango válido: 40-150)
     * @param temperatura temperatura corporal en °C (rango válido: 34-42)
     * @param peso peso corporal en kg (rango válido: 0.5-300)
     * @param talla talla/estatura en cm (rango válido: 30-250)
     * @param frecuenciaCardiaca frecuencia cardíaca en latidos por minuto (rango válido: 30-220)
     * @param emergencia true si la enfermera detectó o confirmó que el caso amerita
     *        prioridad de emergencia (se propaga a la cita para que el médico lo priorice)
     * @return el registro de SignosVitales ya guardado
     * @throws IllegalArgumentException si la cita no está en el estado correcto, si ya
     *         tiene signos vitales registrados, si el usuario no es enfermero, o si
     *         algún valor capturado está fuera del rango permitido
     */
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

        // Validación de rangos de captura (RN-CU07-01 a RN-CU07-05) — bloqueantes,
        // a diferencia de las alertas clínicas (presión alta, fiebre, etc.) que
        // solo se muestran como advertencia en el frontend sin impedir el guardado
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
        // 'emergencia' guardado en la cita para dar prioridad.
        citaService.completarSignosVitales(citaId, emergencia);

        return guardado;
    }

    /**
     * Busca el registro de signos vitales asociado a una cita.
     *
     * @param citaId id de la cita
     * @return el registro de SignosVitales encontrado
     * @throws IllegalArgumentException si esa cita no tiene signos vitales registrados
     */
    public SignosVitales buscarPorCita(Long citaId) {
        return signosVitalesRepository.findByCitaId(citaId)
                .orElseThrow(() -> new IllegalArgumentException("No hay signos vitales registrados para esta cita."));
    }
}