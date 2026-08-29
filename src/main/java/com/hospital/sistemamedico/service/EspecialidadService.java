package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Especialidad;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio con la lógica de negocio del catálogo de especialidades médicas.
 * Usado, entre otros, al agendar una cita (CU-03) y al crear un médico (CU-01).
 */
@Service
public class EspecialidadService {

    @Autowired
    private EspecialidadRepository especialidadRepository;

    /**
     * Crea una nueva especialidad, validando que no exista ya otra con el mismo nombre.
     *
     * @param especialidad datos de la especialidad a crear
     * @return la Especialidad guardada, con estado activo por defecto
     * @throws IllegalArgumentException si ya existe una especialidad con ese nombre
     */
    public Especialidad crear(Especialidad especialidad) {
        if (especialidadRepository.findByNombre(especialidad.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una especialidad con ese nombre.");
        }
        especialidad.setActivo(true);
        return especialidadRepository.save(especialidad);
    }

    /**
     * Lista únicamente las especialidades activas. Usado en los formularios
     * donde el usuario debe elegir una especialidad (agendar cita, crear médico).
     *
     * @return lista de especialidades activas
     */
    public List<Especialidad> listarActivas() {
        return especialidadRepository.findByActivoTrue();
    }

    /**
     * Lista absolutamente todas las especialidades, activas e inactivas.
     *
     * @return lista completa de especialidades
     */
    public List<Especialidad> listarTodas() {
        return especialidadRepository.findAll();
    }

    /**
     * Busca una especialidad por su id.
     *
     * @param id id de la especialidad
     * @return la Especialidad encontrada
     * @throws IllegalArgumentException si no existe ninguna especialidad con ese id
     */
    public Especialidad buscarPorId(Long id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Especialidad no encontrada."));
    }

    /**
     * Desactiva (elimina lógicamente) una especialidad, sin borrarla
     * físicamente para no afectar el historial de citas o médicos ya
     * asociados a ella.
     *
     * @param id id de la especialidad a desactivar
     */
    public void desactivar(Long id) {
        Especialidad especialidad = buscarPorId(id);
        especialidad.setActivo(false);
        especialidadRepository.save(especialidad);
    }
}