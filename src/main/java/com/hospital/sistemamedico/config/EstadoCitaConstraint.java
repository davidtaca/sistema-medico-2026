package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.model.EstadoCita;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Mantiene al día la restricción CHECK de la columna "estado" de la tabla
 * "citas". Hibernate la crea con los valores del enum EstadoCita que existían
 * al crearse la tabla y, con ddl-auto=update, nunca la actualiza; por eso, al
 * agregar estados nuevos (EN_CONSULTA, EVALUADO_PENDIENTE_CIERRE, NO_ASISTIO...)
 * la base de datos rechazaba el cambio de estado. Al arrancar se recrea la
 * restricción con todos los valores actuales del enum.
 */
@Component
@Order(0)
public class EstadoCitaConstraint implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        String valores = Arrays.stream(EstadoCita.values())
                .map(e -> "'" + e.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_estado_check");
            jdbcTemplate.execute("ALTER TABLE citas ADD CONSTRAINT citas_estado_check CHECK (estado IN (" + valores + "))");
        } catch (Exception e) {
            // No debe impedir el arranque; si falla, solo se avisa en consola
            System.err.println("No se pudo actualizar la restricción citas_estado_check: " + e.getMessage());
        }
    }
}
