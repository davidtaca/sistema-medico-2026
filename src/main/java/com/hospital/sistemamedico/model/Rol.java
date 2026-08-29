package com.hospital.sistemamedico.model;

/**
 * Catálogo de roles que puede tener un usuario del sistema. Un mismo usuario
 * siempre tiene exactamente un rol, que determina qué pantallas y funciones
 * puede usar (ver menu.html en el frontend). PACIENTE es el único rol que
 * corresponde a un usuario externo (CU-02); todos los demás son personal
 * interno del hospital, creado mediante CU-01.
 */
public enum Rol {
    ADMINISTRADOR,
    MEDICO,
    PACIENTE,
    CAJERO,
    RECEPCIONISTA,
    ENFERMERO,
    LABORATORISTA,
    FARMACEUTICO
}