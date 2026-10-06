package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio del despacho de medicamentos (CU-11):
 * búsqueda de recetas, validación de vigencia (máximo 7 días, RN-CU10-01),
 * consulta de inventario por sucursal, sustitución de medicamentos (FA02),
 * registro del despacho con descuento automático de inventario y alertas de
 * stock mínimo (RN-CU10-03). El cobro físico se realiza fuera del sistema.
 */
@Service
public class DespachoService {

    /** Días máximos de vigencia de una receta desde su emisión (RN-CU10-01). */
    public static final int DIAS_VIGENCIA = 7;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private InventarioMedicamentoRepository inventarioRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private DespachoRepository despachoRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EmailService emailService;

    // ------------------------------------------------------------ búsqueda

    /**
     * Busca recetas activas (paso 2). Sin criterios devuelve las más recientes.
     *
     * @param recetaId filtro por ID de receta (opcional)
     * @param consultaId filtro por ID de consulta (opcional)
     * @return hasta 50 recetas activas, la más reciente primero
     */
    public List<Receta> buscarRecetas(Long recetaId, Long consultaId) {
        Long citaDeConsulta = null;
        if (consultaId != null) {
            citaDeConsulta = consultaRepository.findById(consultaId).map(c -> c.getCita().getId()).orElse(-1L);
        }
        final Long citaFiltro = citaDeConsulta;
        return recetaRepository.findByActivaTrueOrderByFechaDesc().stream()
                .filter(r -> recetaId == null || r.getId().equals(recetaId))
                .filter(r -> citaFiltro == null || r.getCita().getId().equals(citaFiltro))
                .limit(50)
                .collect(Collectors.toList());
    }

    /** Días transcurridos desde la emisión de la receta. */
    public long diasTranscurridos(Receta receta) {
        return ChronoUnit.DAYS.between(receta.getFecha().toLocalDate(), LocalDate.now());
    }

    /** true si la receta aún está dentro de sus 7 días de vigencia. */
    public boolean estaVigente(Receta receta) {
        return diasTranscurridos(receta) <= DIAS_VIGENCIA;
    }

    /**
     * Detalle de la receta para despachar (pasos 4 y 5): medicamentos con
     * precio y disponibilidad en el inventario de la sucursal.
     *
     * @throws IllegalArgumentException si la receta no existe, no está activa o está vencida
     */
    public Map<String, Object> detalle(Long recetaId, Long farmaceuticoId) {
        Usuario farmaceutico = validarPersonal(farmaceuticoId);
        Receta receta = buscarRecetaDespachable(recetaId);
        Sucursal sucursal = sucursalDe(farmaceutico, receta);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recetaId", receta.getId());
        m.put("paciente", receta.getCita().getPaciente().getNombreCompleto());
        m.put("medico", receta.getMedico().getNombreCompleto());
        m.put("fechaEmision", receta.getFecha().toString());
        m.put("diasTranscurridos", diasTranscurridos(receta));
        m.put("sucursal", sucursal.getNombre());
        m.put("items", receta.getItems().stream().map(i -> {
            Medicamento med = i.getMedicamento();
            if (!med.isActivo() || med.getPrecio() == null) {
                // RN-CU10-01
                throw new IllegalArgumentException("La receta es inválida. Verifique que el medicamento exista, la dosis sea correcta y la receta no sea anterior a 7 días.");
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("recetaItemId", i.getId());
            e.put("medicamentoId", med.getId());
            e.put("medicamento", med.getNombre());
            e.put("dosis", i.getDosis());
            e.put("frecuencia", i.getFrecuencia());
            e.put("duracion", i.getDuracion());
            e.put("indicaciones", i.getIndicaciones());
            e.put("cantidadSugerida", 1);
            e.put("precioUnitario", med.getPrecio());
            e.putAll(disponibilidad(med, sucursal));
            return e;
        }).collect(Collectors.toList()));
        return m;
    }

    // ------------------------------------------------------------ despacho

    /**
     * Confirma el despacho (pasos 9 a 12): valida existencias, descuenta el
     * inventario con control de concurrencia, registra el detalle y deja la
     * receta como no activa. Los medicamentos con cantidad 0 se omiten (por
     * ejemplo, los que no tienen inventario, FA01).
     *
     * @param recetaId id de la receta
     * @param farmaceuticoId id del personal de farmacia
     * @param lineas por cada medicamento recetado: recetaItemId, cantidad, sustituir (FA02),
     *        medicamentoAlternativoId y razon (obligatorias al sustituir)
     * @return resumen con el despacho, el total, las sustituciones y las alertas de stock mínimo (FA04)
     * @throws IllegalArgumentException si la receta está vencida o inactiva, si falta stock o inventario,
     *         si la sustitución es inválida o si no se despacha ningún medicamento
     */
    @Transactional
    public Map<String, Object> confirmar(Long recetaId, Long farmaceuticoId, List<Map<String, Object>> lineas) {
        Usuario farmaceutico = validarPersonal(farmaceuticoId);
        Receta receta = buscarRecetaDespachable(recetaId);
        Sucursal sucursal = sucursalDe(farmaceutico, receta);

        Map<Long, Map<String, Object>> porItem = new HashMap<>();
        if (lineas != null) {
            for (Map<String, Object> l : lineas) {
                if (l.get("recetaItemId") != null) {
                    porItem.put(Long.valueOf(l.get("recetaItemId").toString()), l);
                }
            }
        }

        Despacho despacho = new Despacho();
        despacho.setReceta(receta);
        despacho.setSucursal(sucursal);
        despacho.setFarmaceutico(farmaceutico);
        despacho.setEstado(EstadoDespacho.REGISTRADO);

        BigDecimal total = BigDecimal.ZERO;
        List<String> sustituciones = new ArrayList<>();
        List<Map<String, Object>> descuentos = new ArrayList<>();

        for (RecetaItem item : receta.getItems()) {
            Map<String, Object> linea = porItem.get(item.getId());
            int cantidad = linea == null ? 0 : entero(linea.get("cantidad"));
            if (cantidad < 0) {
                throw new IllegalArgumentException("La cantidad debe ser un número entero positivo.");
            }
            if (cantidad == 0) {
                continue;
            }

            Medicamento recetado = item.getMedicamento();
            Medicamento entregado = recetado;
            boolean sustituir = linea.get("sustituir") != null && Boolean.parseBoolean(linea.get("sustituir").toString());
            String razon = linea.get("razon") == null ? "" : linea.get("razon").toString().trim();

            if (sustituir) {
                if (razon.isEmpty()) {
                    throw new IllegalArgumentException("La razón de sustitución es obligatoria.");
                }
                if (razon.length() > 500) {
                    throw new IllegalArgumentException("La razón de sustitución no puede exceder 500 caracteres.");
                }
                if (linea.get("medicamentoAlternativoId") == null) {
                    throw new IllegalArgumentException("Debe seleccionar el medicamento alternativo.");
                }
                entregado = medicamentoRepository.findById(Long.valueOf(linea.get("medicamentoAlternativoId").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("El medicamento alternativo no existe en el catálogo."));
                if (!entregado.isActivo() || entregado.getPrecio() == null) {
                    throw new IllegalArgumentException("El medicamento alternativo no está disponible.");
                }
                if (entregado.getId().equals(recetado.getId())) {
                    throw new IllegalArgumentException("El medicamento alternativo debe ser distinto al recetado.");
                }
                sustituciones.add("Medicamento " + recetado.getNombre() + " sustituido por " + entregado.getNombre()
                        + ". El médico tratante será notificado de la sustitución.");
            }

            if (recetado.getPrecio() == null || !recetado.isActivo()) {
                throw new IllegalArgumentException("La receta es inválida. Verifique que el medicamento exista, la dosis sea correcta y la receta no sea anterior a 7 días.");
            }

            // Existencias en la sucursal (FA01) y descuento con control optimista (RNF-025)
            final String nombreEntregado = entregado.getNombre();
            InventarioMedicamento inv = inventarioRepository
                    .findByMedicamentoIdAndSucursalId(entregado.getId(), sucursal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sin inventario registrado de " + nombreEntregado + " en la sucursal."));
            if (inv.getStockActual() < cantidad) {
                throw new IllegalArgumentException("Stock insuficiente de " + entregado.getNombre() + ". Disponible: " + inv.getStockActual() + ".");
            }
            int anterior = inv.getStockActual();
            inv.setStockActual(anterior - cantidad);
            try {
                inventarioRepository.saveAndFlush(inv);
            } catch (OptimisticLockingFailureException e) {
                throw new IllegalArgumentException("El inventario de " + entregado.getNombre()
                        + " cambió mientras se procesaba el despacho. Vuelva a cargar la receta e intente de nuevo.");
            }

            DespachoItem di = new DespachoItem();
            di.setDespacho(despacho);
            di.setMedicamentoRecetado(recetado);
            di.setMedicamentoEntregado(entregado);
            di.setCantidad(cantidad);
            di.setPrecioUnitario(entregado.getPrecio());
            di.setSubtotal(entregado.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
            di.setSustituido(sustituir);
            di.setRazonSustitucion(sustituir ? razon : null);
            despacho.getItems().add(di);
            total = total.add(di.getSubtotal());

            Map<String, Object> d = new HashMap<>();
            d.put("medicamento", entregado);
            d.put("cantidad", cantidad);
            d.put("anterior", anterior);
            d.put("nuevo", inv.getStockActual());
            descuentos.add(d);
        }

        if (despacho.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe despachar al menos un medicamento.");
        }

        despacho.setTotal(total);
        Despacho guardado = despachoRepository.save(despacho);

        // Bitácora de inventario: movimiento automático de tipo DESPACHO
        List<String> alertas = new ArrayList<>();
        for (Map<String, Object> d : descuentos) {
            Medicamento med = (Medicamento) d.get("medicamento");
            int nuevo = (int) d.get("nuevo");
            MovimientoInventario mov = new MovimientoInventario();
            mov.setTipo(TipoMovimientoInventario.DESPACHO);
            mov.setMedicamento(med);
            mov.setSucursal(sucursal);
            mov.setCantidad((int) d.get("cantidad"));
            mov.setStockAnterior((int) d.get("anterior"));
            mov.setStockNuevo(nuevo);
            mov.setReferencia(guardado.getNumeroDespacho());
            mov.setUsuario(farmaceutico);
            movimientoRepository.save(mov);

            // FA04: stock mínimo alcanzado tras el despacho
            if (med.getStockMinimo() != null && nuevo <= med.getStockMinimo()) {
                alertas.add("ALERTA: El medicamento " + med.getNombre() + " ha alcanzado el nivel de stock mínimo ("
                        + nuevo + " unidades restantes). Se recomienda generar orden de reabastecimiento.");
            }
        }

        receta.setActiva(false);
        recetaRepository.save(receta);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("despachoId", guardado.getId());
        r.put("numeroDespacho", guardado.getNumeroDespacho());
        r.put("recetaId", receta.getId());
        r.put("paciente", receta.getCita().getPaciente().getNombreCompleto());
        r.put("total", total);
        r.put("cantidadMedicamentos", guardado.getItems().size());
        r.put("items", guardado.getItems().stream().map(this::mapaItem).collect(Collectors.toList()));
        r.put("sustituciones", sustituciones);
        r.put("alertas", alertas);
        r.put("mensaje", "Despacho registrado exitosamente. " + guardado.getItems().size()
                + " medicamento(s) despachado(s). Total: Q" + total.setScale(2, java.math.RoundingMode.HALF_UP) + ".");
        return r;
    }

    /**
     * El médico tratante es notificado por correo de cada sustitución (FA02).
     * Se invoca después de que el despacho ya quedó guardado.
     */
    public void notificarSustituciones(Long despachoId) {
        Despacho d = despachoRepository.findById(despachoId).orElse(null);
        if (d == null) {
            return;
        }
        Usuario medico = d.getReceta().getMedico();
        for (DespachoItem i : d.getItems()) {
            if (i.isSustituido()) {
                emailService.enviarNotificacionSustitucion(medico.getCorreo(), medico.getNombreCompleto(),
                        d.getReceta().getCita().getPaciente().getNombreCompleto(), d.getReceta().getId(),
                        i.getMedicamentoRecetado().getNombre(), i.getMedicamentoEntregado().getNombre(),
                        i.getRazonSustitucion());
            }
        }
    }

    /**
     * El paciente no desea adquirir los medicamentos (FA03): se registra la
     * constancia sin mover inventario. La receta sigue activa mientras esté vigente.
     *
     * @return mensaje de constancia con el nombre del paciente y el número de receta
     */
    @Transactional
    public Map<String, Object> registrarNoAdquirido(Long recetaId, Long farmaceuticoId) {
        Usuario farmaceutico = validarPersonal(farmaceuticoId);
        Receta receta = buscarRecetaDespachable(recetaId);

        Despacho d = new Despacho();
        d.setReceta(receta);
        d.setSucursal(sucursalDe(farmaceutico, receta));
        d.setFarmaceutico(farmaceutico);
        d.setEstado(EstadoDespacho.NO_ADQUIRIDO);
        Despacho guardado = despachoRepository.save(d);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("despachoId", guardado.getId());
        r.put("mensaje", "Se ha registrado que el paciente " + receta.getCita().getPaciente().getNombreCompleto()
                + " no adquirió los medicamentos recetados en farmacia interna. Receta: " + receta.getId() + ".");
        return r;
    }

    // ------------------------------------------------------------ consultas

    /** Medicamentos de la sucursal del usuario con stock en o bajo el mínimo (alertas visibles, RN-CU10-03). */
    public List<Map<String, Object>> alertasStock(Long farmaceuticoId) {
        Usuario f = validarPersonal(farmaceuticoId);
        if (f.getSucursal() == null) {
            return List.of();
        }
        return inventarioRepository.findBySucursalId(f.getSucursal().getId()).stream()
                .filter(i -> i.getMedicamento().getStockMinimo() != null && i.getStockActual() <= i.getMedicamento().getStockMinimo())
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("medicamento", i.getMedicamento().getNombre());
                    m.put("disponible", i.getStockActual());
                    m.put("minimo", i.getMedicamento().getStockMinimo());
                    m.put("mensaje", i.getMedicamento().getNombre() + ": Stock bajo — disponible: " + i.getStockActual()
                            + " (mínimo: " + i.getMedicamento().getStockMinimo() + ").");
                    return m;
                }).collect(Collectors.toList());
    }

    /** Últimos despachos de la sucursal del usuario (panel de farmacia). */
    public List<Map<String, Object>> recientes(Long farmaceuticoId) {
        Usuario f = validarPersonal(farmaceuticoId);
        if (f.getSucursal() == null) {
            return List.of();
        }
        return despachoRepository.findTop20BySucursalIdOrderByFechaDesc(f.getSucursal().getId()).stream()
                .map(this::mapaDespacho).collect(Collectors.toList());
    }

    /** Historial de medicamentos despachados a un paciente (postcondición). */
    public List<Map<String, Object>> historialPaciente(Long pacienteId) {
        return despachoRepository.findByRecetaCitaPacienteIdOrderByFechaDesc(pacienteId).stream()
                .map(this::mapaDespacho).collect(Collectors.toList());
    }

    /** Resumen de una receta para la lista de resultados de búsqueda (paso 2). */
    public Map<String, Object> resumenReceta(Receta r) {
        long dias = diasTranscurridos(r);
        Consulta consulta = consultaRepository.findByCitaId(r.getCita().getId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("consultaId", consulta == null ? null : consulta.getId());
        m.put("paciente", r.getCita().getPaciente().getNombreCompleto());
        m.put("fechaEmision", r.getFecha().toString());
        m.put("diasTranscurridos", dias);
        m.put("vigente", dias <= DIAS_VIGENCIA);
        String notas = r.getItems().stream().map(RecetaItem::getIndicaciones)
                .filter(Objects::nonNull).collect(Collectors.joining("; "));
        m.put("notas", notas.isEmpty() ? null : notas);
        m.put("medicamentos", r.getItems().stream().map(i -> i.getMedicamento().getNombre()).collect(Collectors.toList()));
        return m;
    }

    // ------------------------------------------------------------ helpers

    private Receta buscarRecetaDespachable(Long recetaId) {
        Receta receta = recetaRepository.findById(recetaId)
                .orElseThrow(() -> new IllegalArgumentException("Receta no encontrada."));
        if (!receta.isActiva()) {
            throw new IllegalArgumentException("La receta #" + recetaId + " ya fue despachada o no está activa.");
        }
        if (!estaVigente(receta)) {
            // RN-CU10-01
            throw new IllegalArgumentException("Receta Vencida. La receta #" + recetaId + " fue emitida hace "
                    + diasTranscurridos(receta) + " días y ya no es válida para despacho.");
        }
        return receta;
    }

    private Usuario validarPersonal(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Debe indicar el usuario de farmacia.");
        }
        Usuario u = usuarioService.buscarPorId(id);
        if (u.getRol() != Rol.FARMACEUTICO && u.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("El usuario indicado no es personal de farmacia.");
        }
        return u;
    }

    /** El inventario que se usa es el de la sucursal del usuario; si no tiene, el de la sucursal de la cita. */
    private Sucursal sucursalDe(Usuario u, Receta r) {
        return u.getSucursal() != null ? u.getSucursal() : r.getCita().getSucursal();
    }

    /** Disponibilidad de un medicamento en una sucursal: SIN_INVENTARIO, STOCK_BAJO o DISPONIBLE. */
    private Map<String, Object> disponibilidad(Medicamento med, Sucursal sucursal) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<InventarioMedicamento> inv = inventarioRepository.findByMedicamentoIdAndSucursalId(med.getId(), sucursal.getId());
        if (inv.isEmpty()) {
            m.put("estadoInventario", "SIN_INVENTARIO");
            m.put("disponible", 0);
            m.put("mensajeInventario", "Sin inventario registrado");
            return m;
        }
        int stock = inv.get().getStockActual();
        m.put("disponible", stock);
        m.put("minimo", med.getStockMinimo());
        if (stock <= 0) {
            m.put("estadoInventario", "SIN_INVENTARIO");
            m.put("mensajeInventario", "Sin inventario registrado");
        } else if (med.getStockMinimo() != null && stock <= med.getStockMinimo()) {
            m.put("estadoInventario", "STOCK_BAJO");
            m.put("mensajeInventario", med.getNombre() + ": Stock bajo — disponible: " + stock + " (mínimo: " + med.getStockMinimo() + ").");
        } else {
            m.put("estadoInventario", "DISPONIBLE");
            m.put("mensajeInventario", "Disponible: " + stock);
        }
        return m;
    }

    private Map<String, Object> mapaItem(DespachoItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medicamentoRecetado", i.getMedicamentoRecetado().getNombre());
        m.put("medicamentoEntregado", i.getMedicamentoEntregado().getNombre());
        m.put("sustituido", i.isSustituido());
        m.put("razonSustitucion", i.getRazonSustitucion());
        m.put("cantidad", i.getCantidad());
        m.put("precioUnitario", i.getPrecioUnitario());
        m.put("subtotal", i.getSubtotal());
        return m;
    }

    private Map<String, Object> mapaDespacho(Despacho d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("numeroDespacho", d.getNumeroDespacho());
        m.put("estado", d.getEstado().name());
        m.put("recetaId", d.getReceta().getId());
        m.put("paciente", d.getReceta().getCita().getPaciente().getNombreCompleto());
        m.put("total", d.getTotal());
        m.put("fecha", d.getFecha().toString());
        m.put("farmaceutico", d.getFarmaceutico().getNombreCompleto());
        m.put("items", d.getItems().stream().map(this::mapaItem).collect(Collectors.toList()));
        return m;
    }

    private int entero(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(valor.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La cantidad debe ser un número entero positivo.");
        }
    }
}
