package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SucursalService {

    @Autowired
    private SucursalRepository sucursalRepository;

    public Sucursal crear(Sucursal sucursal) {
        if (sucursalRepository.findByNombre(sucursal.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre.");
        }
        sucursal.setActivo(true);
        return sucursalRepository.save(sucursal);
    }

    public List<Sucursal> listarActivas() {
        return sucursalRepository.findByActivoTrue();
    }

    public List<Sucursal> listarTodas() {
        return sucursalRepository.findAll();
    }

    public Sucursal buscarPorId(Long id) {
        return sucursalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada."));
    }

    public void desactivar(Long id) {
        Sucursal sucursal = buscarPorId(id);
        sucursal.setActivo(false);
        sucursalRepository.save(sucursal);
    }
}