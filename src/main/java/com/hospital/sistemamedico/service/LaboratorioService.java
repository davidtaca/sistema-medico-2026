package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.OrdenLaboratorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio de la gestión de laboratorio (CU-09):
 * listado y detalle de órdenes, registro de resultados por examen y
 * publicación individual de cada resultado. El cobro de la orden (CU-10) lo
 * deja "En proceso" mediante {@link #marcarEnProceso(Long)}; hasta entonces no
 * se pueden registrar resultados (RN-CU09-01).
 */
@Service
public class LaboratorioService {

    @Autowired
    private OrdenLaboratorioRepository ordenRepository;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Lista las órdenes de laboratorio, la más reciente primero (paso 1).
     *
     * @param estado filtro por estado (PENDIENTE, EN_PROCESO, COMPLETADA); null o vacío = todos
     * @param paciente filtro por nombre o DPI del paciente (contiene, sin distinguir mayúsculas); null o vacío = todos
     * @param medico filtro por nombre del médico (contiene, sin distinguir mayúsculas); null o vacío = todos
     * @throws IllegalArgumentException si el estado indicado no existe
     */
    public List<OrdenLaboratorio> listar(String estado, String paciente, String medico) {
        EstadoOrdenLab filtroEstado = null;
        if (estado != null && !estado.isBlank()) {
            try {
                filtroEstado = EstadoOrdenLab.valueOf(estado.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("El estado indicado no es válido.");
            }
        }
        final EstadoOrdenLab estadoFinal = filtroEstado;
        String p = paciente == null ? "" : paciente.trim().toLowerCase();
        String m = medico == null ? "" : medico.trim().toLowerCase();

        return ordenRepository.findAllByOrderByFechaDesc().stream()
                .filter(o -> estadoFinal == null || o.getEstado() == estadoFinal)
                .filter(o -> p.isEmpty()
                        || o.getCita().getPaciente().getNombreCompleto().toLowerCase().contains(p)
                        || (o.getCita().getPaciente().getDpi() != null && o.getCita().getPaciente().getDpi().contains(p)))
                .filter(o -> m.isEmpty() || o.getMedico().getNombreCompleto().toLowerCase().contains(m))
                .collect(Collectors.toList());
    }

    /**
     * Busca una orden por su id.
     *
     * @throws IllegalArgumentException si no existe
     */
    public OrdenLaboratorio buscarPorId(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden de laboratorio no encontrada."));
    }

    /**
     * Deja la orden "En proceso" una vez cobrada (CU-09 paso 6 / CU-10 paso 9).
     * Lo invoca el cobro de laboratorio en caja.
     *
     * @throws IllegalArgumentException si la orden es externa o no está pendiente de pago
     */
    @Transactional
    public OrdenLaboratorio marcarEnProceso(Long ordenId) {
        OrdenLaboratorio orden = buscarPorId(ordenId);
        if (orden.isExterna()) {
            throw new IllegalArgumentException("Las órdenes externas no se cobran en esta clínica.");
        }
        if (orden.getEstado() != EstadoOrdenLab.PENDIENTE) {
            throw new IllegalArgumentException("La orden no está pendiente de pago.");
        }
        orden.setEstado(EstadoOrdenLab.EN_PROCESO);
        return ordenRepository.save(orden);
    }

    /**
     * Guarda el resultado de un examen (pasos 9 y 10, FA02). Se puede volver a
     * guardar mientras no esté publicado.
     *
     * @param ordenId id de la orden
     * @param examenId id del examen dentro de la orden (OrdenExamen)
     * @param personalId id del usuario de laboratorio que registra
     * @param datos valor, unidad, fechaResultado (AAAA-MM-DD), fueraDeRango y notas
     * @return el examen de la orden con su resultado guardado
     * @throws IllegalArgumentException si la orden es externa o no está "En proceso" (RN-CU09-01),
     *         si el resultado ya fue publicado, o si falta o es inválido algún campo
     */
    @Transactional
    public OrdenExamen guardarResultado(Long ordenId, Long examenId, Long personalId, Map<String, Object> datos) {
        validarPersonal(personalId);
        OrdenLaboratorio orden = buscarPorId(ordenId);
        validarOrdenProcesable(orden);
        OrdenExamen item = buscarItem(orden, examenId);

        if (item.isPublicado()) {
            throw new IllegalArgumentException("El resultado ya fue publicado y no puede modificarse sin autorización de un supervisor.");
        }

        String valor = texto(datos.get("valor"));
        String unidad = texto(datos.get("unidad"));
        String fecha = texto(datos.get("fechaResultado"));
        String notas = texto(datos.get("notas"));

        if (valor.isEmpty()) {
            throw new IllegalArgumentException("El valor del resultado es obligatorio.");
        }
        if (valor.length() > 500) {
            throw new IllegalArgumentException("El valor del resultado no puede exceder 500 caracteres.");
        }
        if (unidad.isEmpty()) {
            throw new IllegalArgumentException("La unidad de medida es obligatoria.");
        }
        if (unidad.length() > 50) {
            throw new IllegalArgumentException("La unidad de medida no puede exceder 50 caracteres.");
        }
        if (fecha.isEmpty()) {
            throw new IllegalArgumentException("La fecha del resultado es obligatoria.");
        }
        LocalDate fechaResultado;
        try {
            fechaResultado = LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha del resultado no es válida.");
        }
        if (fechaResultado.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del resultado no puede ser futura.");
        }
        if (notas.length() > 1000) {
            throw new IllegalArgumentException("Las notas del resultado no pueden exceder 1000 caracteres.");
        }

        item.setValorResultado(valor);
        item.setUnidadResultado(unidad);
        item.setFechaResultado(fechaResultado);
        item.setFueraDeRango(datos.get("fueraDeRango") != null && Boolean.parseBoolean(datos.get("fueraDeRango").toString()));
        item.setNotasResultado(notas.isEmpty() ? null : notas);
        item.setRegistradoPor(usuarioService.buscarPorId(personalId));
        ordenRepository.save(orden);
        return item;
    }

    /**
     * Publica el resultado de un examen (pasos 11 a 14). Es individual por
     * examen. Cuando todos los exámenes de la orden quedan publicados, la
     * orden pasa a "Completada".
     *
     * @return la orden actualizada
     * @throws IllegalArgumentException si la orden no está "En proceso", el examen no tiene
     *         resultado guardado o ya estaba publicado
     */
    @Transactional
    public OrdenLaboratorio publicarResultado(Long ordenId, Long examenId, Long personalId) {
        validarPersonal(personalId);
        OrdenLaboratorio orden = buscarPorId(ordenId);
        validarOrdenProcesable(orden);
        OrdenExamen item = buscarItem(orden, examenId);

        if (item.isPublicado()) {
            throw new IllegalArgumentException("Este resultado ya fue publicado.");
        }
        if (!item.tieneResultado()) {
            throw new IllegalArgumentException("Debe guardar el resultado del examen antes de publicarlo.");
        }
        item.setPublicado(true);
        item.setFechaPublicacion(LocalDateTime.now());

        if (orden.getItems().stream().allMatch(OrdenExamen::isPublicado)) {
            orden.setEstado(EstadoOrdenLab.COMPLETADA);
        }
        return ordenRepository.save(orden);
    }

    /**
     * Convierte una orden en un mapa para el frontend, sin exponer datos sensibles de los usuarios.
     *
     * @param soloPublicados true para mostrar únicamente los resultados ya publicados (vista del médico);
     *        false para mostrar todos los resultados guardados (vista del laboratorio)
     */
    public Map<String, Object> aMapa(OrdenLaboratorio o, boolean soloPublicados) {
        Usuario paciente = o.getCita().getPaciente();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("numeroOrden", o.getNumeroOrden());
        m.put("citaId", o.getCita().getId());
        m.put("paciente", paciente.getNombreCompleto());
        m.put("dpi", paciente.getDpi());
        m.put("medico", o.getMedico().getNombreCompleto());
        m.put("estado", o.getEstado().name());
        m.put("estadoEtiqueta", o.getEstado().getEtiqueta());
        m.put("montoTotal", o.getMontoTotal());
        m.put("externa", o.isExterna());
        m.put("observaciones", o.getObservaciones());
        m.put("fecha", o.getFecha().toString());
        m.put("cantidadExamenes", o.getItems().size());
        m.put("examenes", o.getItems().stream().map(i -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", i.getId());
            e.put("nombre", i.getExamen().getNombre());
            e.put("monto", i.getPrecio());
            e.put("rangoReferencia", i.getExamen().getRangoReferencia());
            e.put("unidadSugerida", i.getExamen().getUnidad());
            e.put("publicado", i.isPublicado());
            boolean mostrarResultado = i.tieneResultado() && (!soloPublicados || i.isPublicado());
            e.put("tieneResultado", mostrarResultado);
            if (mostrarResultado) {
                e.put("valor", i.getValorResultado());
                e.put("unidad", i.getUnidadResultado());
                e.put("fechaResultado", i.getFechaResultado().toString());
                e.put("fueraDeRango", i.isFueraDeRango());
                e.put("notas", i.getNotasResultado());
            }
            return e;
        }).collect(Collectors.toList()));
        return m;
    }

    // ---------------------------------------------------------------- helpers

    private void validarPersonal(Long personalId) {
        if (personalId == null) {
            throw new IllegalArgumentException("Debe indicar el usuario de laboratorio.");
        }
        Usuario u = usuarioService.buscarPorId(personalId);
        if (u.getRol() != Rol.LABORATORISTA && u.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("El usuario indicado no es personal de laboratorio.");
        }
    }

    /** Solo se procesan órdenes propias, ya pagadas y no completadas. */
    private void validarOrdenProcesable(OrdenLaboratorio orden) {
        if (orden.isExterna()) {
            throw new IllegalArgumentException("Es una orden externa: los exámenes se realizan en un laboratorio externo.");
        }
        if (orden.getEstado() == EstadoOrdenLab.PENDIENTE) {
            // RN-CU09-01
            throw new IllegalArgumentException("El pago debe estar completado antes de proceder con la toma de muestras.");
        }
        if (orden.getEstado() == EstadoOrdenLab.COMPLETADA) {
            throw new IllegalArgumentException("La orden ya está completada.");
        }
    }

    private OrdenExamen buscarItem(OrdenLaboratorio orden, Long examenId) {
        return orden.getItems().stream()
                .filter(i -> i.getId().equals(examenId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El examen no pertenece a esta orden."));
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString().trim();
    }
}
