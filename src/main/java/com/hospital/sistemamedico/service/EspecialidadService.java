package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Especialidad;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EspecialidadService {

    @Autowired
    private EspecialidadRepository especialidadRepository;

    public Especialidad crear(Especialidad especialidad) {
        if (especialidadRepository.findByNombre(especialidad.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una especialidad con ese nombre.");
        }
        especialidad.setActivo(true);
        return especialidadRepository.save(especialidad);
    }

    public List<Especialidad> listarActivas() {
        return especialidadRepository.findByActivoTrue();
    }

    public List<Especialidad> listarTodas() {
        return especialidadRepository.findAll();
    }

    public Especialidad buscarPorId(Long id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Especialidad no encontrada."));
    }

    public void desactivar(Long id) {
        Especialidad especialidad = buscarPorId(id);
        especialidad.setActivo(false);
        especialidadRepository.save(especialidad);
    }
}