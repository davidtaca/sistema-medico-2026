package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.InventarioMedicamentoRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.MovimientoInventarioRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio de la bitácora de movimientos de
 * inventario de farmacia (CU-16): listado con filtros, registro manual de
 * movimientos (compra, devolución, venta, reclamo y ajustes) con sus reglas por
 * tipo, validación de stock suficiente con control de concurrencia optimista
 * (RN-CU13-02, RNF-025), activar o desactivar registros y resumen mensual. Los
 * movimientos de tipo Despacho los crea automáticamente DespachoService.
 */
@Service
public class BitacoraInventarioService {

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private InventarioMedicamentoRepository inventarioRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private UsuarioService usuarioService;

    // ------------------------------------------------------------ listado (pasos 1 y 2)

    /**
     * Lista los movimientos, el más reciente primero, con filtros por tipo,
     * medicamento, sucursal, número de referencia, usuario y rango de fechas.
     *
     * @param tipo nombre del tipo (por ejemplo COMPRA); vacío = todos
     * @param referencia texto contenido en la referencia (sin distinguir mayúsculas)
     * @param usuario texto contenido en el nombre de quien registró
     * @param desde fecha inicial inclusive (AAAA-MM-DD); vacío = sin límite
     * @param hasta fecha final inclusive (AAAA-MM-DD); vacío = sin límite
     * @return página con "contenido", "pagina", "tamano", "totalElementos" y "totalPaginas"
     * @throws IllegalArgumentException si algún filtro no es válido
     */
    public Map<String, Object> listar(String tipo, Long medicamentoId, Long sucursalId, String referencia, String usuario,
                                      String desde, String hasta, int pagina, int tamano) {
        TipoMovimientoInventario filtroTipo = null;
        if (tipo != null && !tipo.isBlank()) {
            try {
                filtroTipo = TipoMovimientoInventario.valueOf(tipo.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("El tipo de movimiento indicado no es válido.");
            }
        }
        LocalDate fechaDesde = parsearFecha(desde);
        LocalDate fechaHasta = parsearFecha(hasta);
        if (fechaDesde != null && fechaHasta != null && fechaHasta.isBefore(fechaDesde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        final TipoMovimientoInventario t = filtroTipo;
        String ref = referencia == null ? "" : referencia.trim().toLowerCase();
        String usr = usuario == null ? "" : usuario.trim().toLowerCase();

        List<MovimientoInventario> filtrados = movimientoRepository.findAllByOrderByFechaDescIdDesc().stream()
                .filter(m -> t == null || m.getTipo() == t)
                .filter(m -> medicamentoId == null || m.getMedicamento().getId().equals(medicamentoId))
                .filter(m -> sucursalId == null || m.getSucursal().getId().equals(sucursalId))
                .filter(m -> ref.isEmpty() || (m.getReferencia() != null && m.getReferencia().toLowerCase().contains(ref)))
                .filter(m -> usr.isEmpty() || m.getUsuario().getNombreCompleto().toLowerCase().contains(usr))
                .filter(m -> fechaDesde == null || !m.getFecha().toLocalDate().isBefore(fechaDesde))
                .filter(m -> fechaHasta == null || !m.getFecha().toLocalDate().isAfter(fechaHasta))
                .collect(Collectors.toList());

        int tam = Math.max(1, Math.min(tamano, 100));
        int pag = Math.max(0, pagina);
        int total = filtrados.size();
        int inicio = Math.min(pag * tam, total);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("contenido", filtrados.subList(inicio, Math.min(inicio + tam, total)).stream().map(this::aMapa).collect(Collectors.toList()));
        r.put("pagina", pag);
        r.put("tamano", tam);
        r.put("totalElementos", total);
        r.put("totalPaginas", (int) Math.ceil(total / (double) tam));
        return r;
    }

    /** Detalle de un movimiento (acción "Ver"). */
    public Map<String, Object> detalle(Long id) {
        return aMapa(buscar(id));
    }

    // ------------------------------------------------------------ formulario (paso 4)

    /** Tipos que se pueden registrar manualmente: todos menos Despacho. */
    public List<Map<String, Object>> tiposManuales() {
        return Arrays.stream(TipoMovimientoInventario.values()).filter(TipoMovimientoInventario::esManual).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("valor", t.name());
            m.put("etiqueta", t.getEtiqueta());
            m.put("salida", t.esSalida());
            m.put("etiquetaReferencia", t.getTipoReferencia());
            return m;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> opcionesMedicamentos() {
        return medicamentoRepository.findAll().stream().filter(Medicamento::isActivo)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(m -> Map.<String, Object>of("id", m.getId(), "etiqueta", m.getNombre())).collect(Collectors.toList());
    }

    public List<Map<String, Object>> opcionesSucursales() {
        return sucursalRepository.findAll().stream().filter(Sucursal::isActivo)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(s -> Map.<String, Object>of("id", s.getId(), "etiqueta", s.getNombre())).collect(Collectors.toList());
    }

    /**
     * Panel informativo de inventario en tiempo real (RN-CU13-03): al elegir
     * medicamento y sucursal muestra el stock actual y el mínimo.
     */
    public Map<String, Object> panel(Long medicamentoId, Long sucursalId) {
        Medicamento med = medicamentoRepository.findById(medicamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar un medicamento."));
        Sucursal suc = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar una sucursal."));
        Optional<InventarioMedicamento> inv = inventarioRepository.findByMedicamentoIdAndSucursalId(medicamentoId, sucursalId)
                .filter(InventarioMedicamento::isActivo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medicamento", med.getNombre());
        m.put("sucursal", suc.getNombre());
        m.put("existeInventario", inv.isPresent());
        m.put("stockActual", inv.map(InventarioMedicamento::getStockActual).orElse(0));
        m.put("stockMinimo", med.getStockMinimo());
        return m;
    }

    // ------------------------------------------------------------ registro (pasos 5 a 9)

    /**
     * Registra un movimiento manual y actualiza el stock del inventario.
     *
     * @param usuarioId personal de farmacia o administrador que lo registra
     * @param datos medicamentoId, sucursalId, tipo, cantidad, costoUnitario (solo compras),
     *        referencia (opcional) y notas (según el tipo)
     * @return el movimiento registrado, el mensaje de éxito y, si aplica, la alerta de stock mínimo
     * @throws IllegalArgumentException si falta o es inválido algún dato (RN-CU13-01), si una salida
     *         excede el stock (RN-CU13-02, FA02) o si cambió el inventario mientras se registraba
     */
    @Transactional
    public Map<String, Object> registrar(Long usuarioId, Map<String, Object> datos) {
        Usuario usuario = validarPersonal(usuarioId);

        Long medId = idSeleccion(datos.get("medicamentoId"));
        if (medId == null) {
            throw new IllegalArgumentException("Debe seleccionar un medicamento.");
        }
        Long sucId = idSeleccion(datos.get("sucursalId"));
        if (sucId == null) {
            throw new IllegalArgumentException("Debe seleccionar una sucursal.");
        }
        TipoMovimientoInventario tipo;
        try {
            tipo = TipoMovimientoInventario.valueOf(texto(datos.get("tipo")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de movimiento.");
        }
        if (!tipo.esManual()) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de movimiento.");
        }
        Medicamento medicamento = medicamentoRepository.findById(medId).filter(Medicamento::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar un medicamento."));
        Sucursal sucursal = sucursalRepository.findById(sucId).filter(Sucursal::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar una sucursal."));

        int cantidad = cantidad(datos.get("cantidad"));
        BigDecimal costo = costo(tipo, datos.get("costoUnitario"));
        String notas = notas(tipo, datos.get("notas"));
        String referencia = referencia(tipo, datos.get("referencia"));

        // Inventario de la combinación medicamento-sucursal
        InventarioMedicamento inv = inventarioRepository.findByMedicamentoIdAndSucursalId(medId, sucId).orElse(null);
        boolean activo = inv != null && inv.isActivo();
        int anterior = activo ? inv.getStockActual() : 0;

        if (tipo.esSalida()) {
            if (!activo) {
                throw new IllegalArgumentException("Sin inventario registrado de " + medicamento.getNombre() + " en la sucursal.");
            }
            // RN-CU13-02 / FA02: la salida no puede exceder el stock actual
            if (cantidad > anterior) {
                throw new IllegalArgumentException("Stock insuficiente. Stock actual: " + anterior
                        + ". No se puede registrar una salida de " + cantidad + " unidades.");
            }
        } else if (inv == null) {
            // Una compra, devolución o ajuste positivo puede dar inicio al inventario de la sucursal
            inv = new InventarioMedicamento(medicamento, sucursal, 0);
        } else if (!inv.isActivo()) {
            inv.setActivo(true);
            inv.setStockActual(0);
        }

        int nuevo = tipo.esSalida() ? anterior - cantidad : anterior + cantidad;
        inv.setStockActual(nuevo);
        try {
            inventarioRepository.saveAndFlush(inv);
        } catch (OptimisticLockingFailureException e) {
            // RNF-025: otro movimiento cambió el stock al mismo tiempo
            throw new IllegalArgumentException("El inventario de " + medicamento.getNombre()
                    + " cambió mientras se registraba el movimiento. Vuelva a cargar el stock e intente de nuevo.");
        }

        MovimientoInventario mov = new MovimientoInventario();
        mov.setTipo(tipo);
        mov.setMedicamento(medicamento);
        mov.setSucursal(sucursal);
        mov.setCantidad(cantidad);
        mov.setStockAnterior(anterior);
        mov.setStockNuevo(nuevo);
        mov.setCostoUnitario(costo);
        mov.setReferencia(referencia);
        mov.setNotas(notas);
        mov.setUsuario(usuario);
        MovimientoInventario guardado = movimientoRepository.save(mov);

        Map<String, Object> r = new LinkedHashMap<>(aMapa(guardado));
        r.put("mensaje", "Movimiento registrado exitosamente. Medicamento: " + medicamento.getNombre() + ". Tipo: "
                + tipo.getEtiqueta() + ". Cantidad: " + cantidad + ". Stock actualizado: " + nuevo + ".");
        Integer minimo = medicamento.getStockMinimo();
        r.put("alerta", minimo != null && nuevo <= minimo
                ? "ALERTA: El medicamento " + medicamento.getNombre() + " ha alcanzado el nivel de stock mínimo (" + nuevo
                + " unidades restantes). Se recomienda generar orden de reabastecimiento."
                : null);
        return r;
    }

    /**
     * Activa o desactiva un registro de la bitácora (acción "Desactivar/Activar"
     * de la tabla). Solo cambia el estado del registro; no modifica el stock.
     */
    @Transactional
    public Map<String, Object> cambiarEstado(Long usuarioId, Long id, boolean activo) {
        validarPersonal(usuarioId);
        MovimientoInventario m = buscar(id);
        m.setActivo(activo);
        return aMapa(movimientoRepository.save(m));
    }

    // ------------------------------------------------------------ resumen mensual (RN-CU13-03)

    /**
     * Resumen de los movimientos activos de un mes, por tipo, con el total de
     * entradas, salidas y el neto de unidades.
     *
     * @param mes mes en formato AAAA-MM
     * @param medicamentoId filtra por medicamento (opcional)
     * @param sucursalId filtra por sucursal (opcional)
     */
    public Map<String, Object> resumenMensual(String mes, Long medicamentoId, Long sucursalId) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(mes);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("El mes indicado no es válido. Use el formato AAAA-MM.");
        }
        List<MovimientoInventario> del = movimientoRepository.findAllByOrderByFechaDescIdDesc().stream()
                .filter(MovimientoInventario::isActivo)
                .filter(m -> YearMonth.from(m.getFecha()).equals(ym))
                .filter(m -> medicamentoId == null || m.getMedicamento().getId().equals(medicamentoId))
                .filter(m -> sucursalId == null || m.getSucursal().getId().equals(sucursalId))
                .collect(Collectors.toList());

        List<Map<String, Object>> porTipo = new ArrayList<>();
        int entradas = 0, salidas = 0;
        for (TipoMovimientoInventario t : TipoMovimientoInventario.values()) {
            List<MovimientoInventario> deTipo = del.stream().filter(m -> m.getTipo() == t).collect(Collectors.toList());
            int unidades = deTipo.stream().mapToInt(MovimientoInventario::getCantidad).sum();
            if (t.esSalida()) {
                salidas += unidades;
            } else {
                entradas += unidades;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", t.name());
            m.put("etiqueta", t.getEtiqueta());
            m.put("salida", t.esSalida());
            m.put("movimientos", deTipo.size());
            m.put("unidades", unidades);
            porTipo.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mes", ym.toString());
        r.put("totalMovimientos", del.size());
        r.put("entradas", entradas);
        r.put("salidas", salidas);
        r.put("neto", entradas - salidas);
        r.put("porTipo", porTipo);
        return r;
    }

    // ------------------------------------------------------------ validaciones de campos (RN-CU13-01)

    private int cantidad(Object valor) {
        String t = texto(valor);
        try {
            int n = Integer.parseInt(t);
            if (n > 0) {
                return n;
            }
        } catch (NumberFormatException e) {
            // se informa abajo
        }
        throw new IllegalArgumentException("La cantidad debe ser un número entero positivo.");
    }

    /** El costo unitario solo se exige (y solo se guarda) en las compras. */
    private BigDecimal costo(TipoMovimientoInventario tipo, Object valor) {
        if (tipo != TipoMovimientoInventario.COMPRA) {
            return null;
        }
        String t = texto(valor);
        if (t.isEmpty()) {
            throw new IllegalArgumentException("El costo unitario es obligatorio para compras");
        }
        BigDecimal c;
        try {
            c = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor a 0");
        }
        if (c.signum() <= 0) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor a 0");
        }
        if (c.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El costo unitario debe tener máximo 2 decimales");
        }
        return c.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Motivo o notas según el tipo: obligatorio (10 a 500 caracteres) en ajustes,
     * devoluciones y reclamos; opcional en compras; no aplica en ventas.
     */
    private String notas(TipoMovimientoInventario tipo, Object valor) {
        if (tipo == TipoMovimientoInventario.VENTA) {
            return null;
        }
        String n = texto(valor);
        boolean obligatorio = tipo == TipoMovimientoInventario.AJUSTE_POSITIVO || tipo == TipoMovimientoInventario.AJUSTE_NEGATIVO
                || tipo == TipoMovimientoInventario.DEVOLUCION || tipo == TipoMovimientoInventario.RECLAMO;
        if (n.isEmpty() && !obligatorio) {
            return null;
        }
        if (n.length() < 10 || n.length() > 500) {
            throw new IllegalArgumentException("El motivo debe contener entre 10 y 500 caracteres.");
        }
        return n;
    }

    /** Número de referencia opcional; solo aplica a compra, devolución, venta y reclamo. */
    private String referencia(TipoMovimientoInventario tipo, Object valor) {
        if (tipo.getTipoReferencia() == null) {
            return null;
        }
        String r = texto(valor);
        if (r.isEmpty()) {
            return null;
        }
        if (r.length() > 50) {
            throw new IllegalArgumentException("El número de referencia no puede exceder 50 caracteres.");
        }
        return r;
    }

    // ------------------------------------------------------------ helpers

    private Usuario validarPersonal(Long usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Debe indicar el usuario.");
        }
        Usuario u = usuarioService.buscarPorId(usuarioId);
        if (u.getRol() != Rol.FARMACEUTICO && u.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("Solo el personal de farmacia o un administrador puede registrar movimientos de inventario.");
        }
        return u;
    }

    private MovimientoInventario buscar(Long id) {
        return movimientoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("El movimiento no existe."));
    }

    private LocalDate parsearFecha(String f) {
        if (f == null || f.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(f.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha indicada no es válida.");
        }
    }

    private Long idSeleccion(Object valor) {
        String t = texto(valor);
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString().trim();
    }

    private Map<String, Object> aMapa(MovimientoInventario m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("tipo", m.getTipo().name());
        r.put("tipoEtiqueta", m.getTipo().getEtiqueta());
        r.put("salida", m.getTipo().esSalida());
        r.put("medicamentoId", m.getMedicamento().getId());
        r.put("medicamento", m.getMedicamento().getNombre());
        r.put("sucursalId", m.getSucursal().getId());
        r.put("sucursal", m.getSucursal().getNombre());
        r.put("cantidad", m.getCantidad());
        r.put("stockAnterior", m.getStockAnterior());
        r.put("stockNuevo", m.getStockNuevo());
        r.put("costoUnitario", m.getCostoUnitario());
        r.put("referencia", m.getReferencia());
        r.put("tipoReferencia", m.getTipo().getTipoReferencia());
        r.put("notas", m.getNotas());
        r.put("usuario", m.getUsuario().getNombreCompleto());
        r.put("fecha", m.getFecha().toString());
        r.put("activo", m.isActivo());
        return r;
    }
}
