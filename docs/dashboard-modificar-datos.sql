-- ============================================================================
-- DASHBOARD: cómo cambiar lo que muestran las gráficas desde la base de datos
-- ============================================================================
-- Las gráficas NO guardan números propios: en cada actualización la aplicación
-- cuenta lo que hay en estas tablas de PostgreSQL. Si cambias los registros y
-- pulsas "Actualizar ahora" en el dashboard (o esperas 30 segundos), la gráfica cambia.
--
-- Ejecuta estas sentencias en pgAdmin / DBeaver / Azure Data Studio conectado a la
-- base "sistema_medico". Antes de modificar, ejecuta primero el SELECT para ver qué vas a tocar.
--
-- Tablas que alimentan cada gráfica:
--   1. Médicos con más consultas ......... citas            (estado, fecha_hora, medico_id)
--   2. Medicamentos más despachados ...... despacho_items   (cantidad, medicamento_entregado_id) y despachos (estado)
--   3. Métodos de pago ................... pagos (forma_pago) y pagos_laboratorio (forma_pago)
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 3. MÉTODOS DE PAGO  (ejemplo: "pagaron 10 con Visa y ahora piden 15")
-- ----------------------------------------------------------------------------
-- Valores válidos de forma_pago: 'EFECTIVO', 'VISA', 'MASTERCARD', 'DEBITO'
-- ('TARJETA' = pago en línea anterior a que se guardara la marca; la gráfica lo muestra aparte).

-- a) Ver cuántos hay por forma de pago (lo mismo que cuenta el dashboard, solo pagos de citas):
SELECT forma_pago, COUNT(*) AS pagos FROM pagos GROUP BY forma_pago ORDER BY pagos DESC;

-- b) Pasar de 10 a 15 pagos con Visa: cambiar 5 pagos de otra forma a VISA.
--    Primero mira cuáles son (id, forma actual):
SELECT id, cita_id, forma_pago FROM pagos WHERE forma_pago <> 'VISA' ORDER BY id LIMIT 5;
--    Y cámbialos (pon aquí los ids que salieron arriba):
UPDATE pagos SET forma_pago = 'VISA' WHERE id IN (1, 2, 3, 4, 5);

-- c) Lo mismo para laboratorio (tabla pagos_laboratorio):
SELECT forma_pago, COUNT(*) FROM pagos_laboratorio GROUP BY forma_pago;
UPDATE pagos_laboratorio SET forma_pago = 'VISA' WHERE id IN (1, 2);

-- d) Para BAJAR de 15 a 10 con Visa, devuelve 5 a otra forma (por ejemplo EFECTIVO):
UPDATE pagos SET forma_pago = 'EFECTIVO' WHERE id IN (1, 2, 3, 4, 5);

-- Nota: el dashboard cuenta una fila de pagos = un pago. Si quieres AÑADIR pagos nuevos en lugar de
-- reclasificar los existentes, lo más sencillo es crearlos desde la aplicación (caja o pago en línea).
-- Una cita solo puede tener un pago.


-- ----------------------------------------------------------------------------
-- 2. MEDICAMENTOS MÁS DESPACHADOS
-- ----------------------------------------------------------------------------
-- Ver el ranking tal como lo calcula el dashboard:
SELECT m.nombre, SUM(i.cantidad) AS unidades
FROM despacho_items i
JOIN despachos d ON d.id = i.despacho_id
JOIN medicamentos m ON m.id = i.medicamento_entregado_id
WHERE d.estado = 'REGISTRADO'
GROUP BY m.nombre ORDER BY unidades DESC;

-- Cambiar la cantidad de un despacho (ejemplo: que el ítem 12 pase a 40 unidades):
UPDATE despacho_items SET cantidad = 40, subtotal = precio_unitario * 40 WHERE id = 12;


-- ----------------------------------------------------------------------------
-- 1. MÉDICOS CON MÁS CONSULTAS EN LOS DÍAS DE MAYOR DEMANDA
-- ----------------------------------------------------------------------------
-- Una consulta cuenta cuando la cita está en: EN_CONSULTA, EVALUADO_PENDIENTE_CIERRE,
-- ATENCION_FINALIZADA o COMPLETADA. Ver consultas por día y médico:
SELECT CAST(c.fecha_hora AS date) AS dia, u.nombre_completo, COUNT(*) AS consultas
FROM citas c JOIN usuarios u ON u.id = c.medico_id
WHERE c.estado IN ('EN_CONSULTA', 'EVALUADO_PENDIENTE_CIERRE', 'ATENCION_FINALIZADA', 'COMPLETADA')
GROUP BY dia, u.nombre_completo ORDER BY consultas DESC;

-- Marcar una cita como atendida para que cuente en la gráfica (ejemplo: cita 25):
UPDATE citas SET estado = 'ATENCION_FINALIZADA' WHERE id = 25;

-- Cambiar de médico una cita (ejemplo: la cita 25 pasa al médico con id 3):
UPDATE citas SET medico_id = 3 WHERE id = 25;
