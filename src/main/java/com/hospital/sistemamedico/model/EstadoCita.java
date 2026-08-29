package com.hospital.sistemamedico.model;

/**
 * Estados posibles del ciclo de vida de una cita médica. La cita avanza por
 * estos estados en orden a medida que el paciente pasa por cada caso de uso:
 *
 * PENDIENTE_PAGO   → recién agendada (CU-03), esperando el pago
 * CONFIRMADA       → el pago ya se registró (CU-04 o CU-06)
 * PACIENTE_PRESENTE→ el paciente llegó y recepción registró su llegada (CU-05);
 *                    también es el estado al que se regresa tras terminar
 *                    signos vitales, mientras espera ser llamado por el médico
 * SIGNOS_VITALES   → la enfermera está tomando los signos vitales (CU-07)
 * EN_CONSULTA      → el médico está atendiendo al paciente (CU-08, pendiente)
 * EVALUADO_PENDIENTE_CIERRE → el médico ya evaluó pero falta cerrar la atención (CU-08, pendiente)
 * ATENCION_FINALIZADA → la consulta terminó por completo (CU-08, pendiente)
 * NO_ASISTIO       → el paciente no se presentó a su cita
 * COMPLETADA       → estado de cierre general (uso histórico/alternativo a ATENCION_FINALIZADA)
 * CANCELADA        → la cita fue cancelada, ya sea manualmente o automáticamente
 *                    por vencimiento del tiempo de pago (CU-06)
 */
public enum EstadoCita {
    PENDIENTE_PAGO,
    CONFIRMADA,
    PACIENTE_PRESENTE,
    SIGNOS_VITALES,
    EN_CONSULTA,
    EVALUADO_PENDIENTE_CIERRE,
    ATENCION_FINALIZADA,
    NO_ASISTIO,
    COMPLETADA,
    CANCELADA
}