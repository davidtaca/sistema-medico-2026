package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio con la lógica de negocio del catálogo de sucursales (sedes) del hospital.
 * Usado, entre otros, al agendar una cita (CU-03) y al crear un médico (CU-01).
 */
@Service
public class SucursalService {

    @Autowired
    private SucursalRepository sucursalRepository;

    /**
     * Crea una nueva sucursal, validando que no exista ya otra con el mismo nombre.
     *
     * @param sucursal datos de la sucursal a crear
     * @return la Sucursal guardada, con estado activo por defecto
     * @throws IllegalArgumentException si ya existe una sucursal con ese nombre
     */
    public Sucursal crear(Sucursal sucursal) {
        if (sucursalRepository.findByNombre(sucursal.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre.");
        }
        sucursal.setActivo(true);
        return sucursalRepository.save(sucursal);
    }

    /**
     * Lista únicamente las sucursales activas. Usado en los formularios donde
     * el usuario debe elegir una sucursal (agendar cita, crear médico), para
     * no mostrar sedes que ya fueron dadas de baja.
     *
     * @return lista de sucursales activas
     */
    public List<Sucursal> listarActivas() {
        return sucursalRepository.findByActivoTrue();
    }

    /**
     * Lista absolutamente todas las sucursales, activas e inactivas.
     *
     * @return lista completa de sucursales
     */
    public List<Sucursal> listarTodas() {
        return sucursalRepository.findAll();
    }

    /**
     * Busca una sucursal por su id.
     *
     * @param id id de la sucursal
     * @return la Sucursal encontrada
     * @throws IllegalArgumentException si no existe ninguna sucursal con ese id
     */
    public Sucursal buscarPorId(Long id) {
        return sucursalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada."));
    }

    /**
     * Desactiva (elimina lógicamente) una sucursal, sin borrarla físicamente
     * para no afectar el historial de citas o médicos ya asociados a ella.
     *
     * @param id id de la sucursal a desactivar
     */
    public void desactivar(Long id) {
        Sucursal sucursal = buscarPorId(id);
        sucursal.setActivo(false);
        sucursalRepository.save(sucursal);
    }
}