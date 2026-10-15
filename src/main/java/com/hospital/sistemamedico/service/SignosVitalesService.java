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
        if (enfermero.getRol() != Rol.ENFERMERO && enfermero.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("El usuario indicado no es personal de enfermería.");
        }

        // Validación de campos obligatorios y rangos de captura (RN-CU07-01 a RN-CU07-05) — bloqueantes,
        // a diferencia de las alertas clínicas (RN-CU07-06), que solo advierten y no impiden el guardado
        if (presionSistolica == null || presionSistolica < 60 || presionSistolica > 250
                || presionDiastolica == null || presionDiastolica < 40 || presionDiastolica > 150) {
            throw new IllegalArgumentException("La presión arterial debe ingresarse en formato sistólica/diastólica (ej: 120/80) dentro de rangos válidos.");
        }
        if (temperatura == null || temperatura < 34.0 || temperatura > 42.0 || decimales(temperatura) > 1) {
            throw new IllegalArgumentException("La temperatura debe estar entre 34.0 y 42.0°C con un decimal.");
        }
        if (peso == null || peso < 0.5 || peso > 300 || decimales(peso) > 2) {
            throw new IllegalArgumentException("El peso debe estar entre 0.5 y 300 kg con dos decimales.");
        }
        if (talla == null || talla < 30 || talla > 250 || decimales(talla) > 2) {
            throw new IllegalArgumentException("La talla debe estar entre 30 y 250 cm con dos decimales.");
        }
        if (frecuenciaCardiaca == null || frecuenciaCardiaca < 30 || frecuenciaCardiaca > 220) {
            throw new IllegalArgumentException("La frecuencia cardíaca debe estar entre 30 y 220 latidos por minuto.");
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
        sv.setAlertas(String.join(" | ", alertasClinicas(presionSistolica, presionDiastolica, temperatura, frecuenciaCardiaca)));

        SignosVitales guardado = signosVitalesRepository.save(sv);

        // El paciente regresa a la sala de espera (o queda marcado con prioridad de emergencia)
        // a la espera de ser llamado por el médico. El CU-08 (consulta médica) usará el campo
        // 'emergencia' guardado en la cita para dar prioridad.
        citaService.completarSignosVitales(citaId, emergencia);

        return guardado;
    }

    /** true si la cita ya tiene signos vitales registrados. */
    public boolean tieneSignos(Long citaId) {
        return signosVitalesRepository.existsByCitaId(citaId);
    }

    /** Cantidad de decimales con que se escribió un valor (ignorando ceros finales, por ejemplo 36.50 tiene 1). */
    private int decimales(double valor) {
        return Math.max(0, java.math.BigDecimal.valueOf(valor).stripTrailingZeros().scale());
    }

    /**
     * Alertas clínicas de RN-CU07-06: valores dentro del rango de captura pero fuera del rango normal.
     * Presión arterial normal 90/60 a 140/90 mmHg, temperatura 36.0 a 37.5 °C y frecuencia cardíaca 60 a 100 lpm.
     *
     * @return la lista de mensajes de alerta (vacía si todo es normal)
     */
    public java.util.List<String> alertasClinicas(int sistolica, int diastolica, double temperatura, int frecuencia) {
        java.util.List<String> alertas = new java.util.ArrayList<>();
        if (sistolica < 90 || sistolica > 140 || diastolica < 60 || diastolica > 90) {
            alertas.add("Presión arterial fuera de rango normal.");
        }
        if (temperatura < 36.0 || temperatura > 37.5) {
            alertas.add("Temperatura fuera de rango normal.");
        }
        if (frecuencia < 60 || frecuencia > 100) {
            alertas.add("Frecuencia cardíaca fuera de rango normal.");
        }
        return alertas;
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