package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.InventarioMedicamentoRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.MovimientoInventarioRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hospital.sistemamedico.service.catalogo.CatalogoBase.*;

/**
 * Catálogo de inventario de medicamentos (CU-15): existencias de un
 * medicamento en una sucursal. Se elige el medicamento y la sucursal (solo al
 * crear) y el stock actual; la tabla marca con alerta los registros que están
 * en o bajo el stock mínimo del medicamento (RN-CU10-03). Cada cambio de
 * stock deja un movimiento de ajuste en la bitácora de inventario.
 */
@Component
public class InventarioCatalogo implements CatalogoAdaptador {

    private static final String MENSAJE_STOCK = "El stock debe ser un número entero mayor o igual a 0.";

    @Autowired
    private InventarioMedicamentoRepository repositorio;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Override public String clave() { return "inventario"; }
    @Override public String titulo() { return "Inventario de Medicamentos"; }
    @Override public int orden() { return 8; }

    @Override
    public String nota() {
        return "Los cambios de stock quedan registrados como ajustes en la bitácora de movimientos de inventario.";
    }

    @Override
    public List<Map<String, Object>> campos() {
        return List.of(
                soloCreacion(campo("medicamentoId", "Medicamento", "seleccion", true, null)),
                soloCreacion(campo("sucursalId", "Sucursal", "seleccion", true, null)),
                campo("stockActual", "Stock actual", "entero", true, null));
    }

    @Override
    public List<Map<String, Object>> columnas() {
        return List.of(columna("nombre", "Medicamento — Sucursal"), columna("stockActual", "Stock actual"),
                columna("stockMinimo", "Stock mínimo"), columna("alerta", "Alerta"));
    }

    @Override
    public List<Map<String, Object>> opciones(String campo) {
        if ("medicamentoId".equals(campo)) {
            return medicamentoRepository.findAll().stream().filter(Medicamento::isActivo)
                    .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                    .map(m -> Map.<String, Object>of("id", m.getId(), "etiqueta", m.getNombre())).collect(Collectors.toList());
        }
        if ("sucursalId".equals(campo)) {
            return sucursalRepository.findAll().stream().filter(Sucursal::isActivo)
                    .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                    .map(s -> Map.<String, Object>of("id", s.getId(), "etiqueta", s.getNombre())).collect(Collectors.toList());
        }
        throw new IllegalArgumentException("El campo indicado no tiene opciones.");
    }

    @Override
    public List<Map<String, Object>> filas() {
        return repositorio.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).map(this::aMapa).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> obtener(Long id) {
        return aMapa(buscar(id));
    }

    @Override
    public String crear(Map<String, Object> datos, Usuario admin) {
        Long medId = idSeleccion(datos, "medicamentoId");
        if (medId == null) {
            throw new IllegalArgumentException("Debe seleccionar un medicamento.");
        }
        Long sucId = idSeleccion(datos, "sucursalId");
        if (sucId == null) {
            throw new IllegalArgumentException("Debe seleccionar una sucursal.");
        }
        Medicamento medicamento = medicamentoRepository.findById(medId).filter(Medicamento::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar un medicamento."));
        Sucursal sucursal = sucursalRepository.findById(sucId).filter(Sucursal::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar una sucursal."));
        int stock = enteroNoNegativo(datos, "stockActual", true, MENSAJE_STOCK);

        String nombre = medicamento.getNombre() + " — " + sucursal.getNombre();
        InventarioMedicamento existente = repositorio.findByMedicamentoIdAndSucursalId(medId, sucId).orElse(null);
        if (existente != null && existente.isActivo()) {
            throw new IllegalArgumentException(yaExiste(nombre));
        }
        // Si hubo uno dado de baja se reutiliza (la combinación medicamento-sucursal es única)
        InventarioMedicamento inv = existente != null ? existente : new InventarioMedicamento(medicamento, sucursal, 0);
        int anterior = existente != null ? existente.getStockActual() : 0;
        inv.setActivo(true);
        inv.setStockActual(stock);
        guardar(inv, nombre);
        registrarAjuste(inv, anterior, stock, admin, "Alta del inventario desde el mantenimiento de catálogos.");
        return nombre;
    }

    @Override
    public String actualizar(Long id, Map<String, Object> datos, Usuario admin) {
        InventarioMedicamento inv = buscar(id);
        int stock = enteroNoNegativo(datos, "stockActual", true, MENSAJE_STOCK);
        boolean activo = activo(datos);
        String nombre = nombreDe(inv);
        int anterior = inv.getStockActual();
        inv.setStockActual(stock);
        inv.setActivo(activo);
        guardar(inv, nombre);
        if (stock != anterior) {
            registrarAjuste(inv, anterior, stock, admin, "Ajuste de stock desde el mantenimiento de catálogos.");
        }
        return nombre;
    }

    @Override
    public String eliminar(Long id, Usuario admin) {
        InventarioMedicamento inv = buscar(id);
        if (!inv.isActivo()) {
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        }
        inv.setActivo(false);
        guardar(inv, nombreDe(inv));
        return nombreDe(inv);
    }

    /** Guarda con control de concurrencia optimista (RNF-025). */
    private void guardar(InventarioMedicamento inv, String nombre) {
        try {
            repositorio.saveAndFlush(inv);
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalArgumentException("El inventario de " + nombre + " cambió mientras se editaba. Vuelva a cargarlo e intente de nuevo.");
        }
    }

    private void registrarAjuste(InventarioMedicamento inv, int anterior, int nuevo, Usuario admin, String nota) {
        if (nuevo == anterior) {
            return;
        }
        MovimientoInventario mov = new MovimientoInventario();
        mov.setTipo(nuevo > anterior ? TipoMovimientoInventario.AJUSTE_POSITIVO : TipoMovimientoInventario.AJUSTE_NEGATIVO);
        mov.setMedicamento(inv.getMedicamento());
        mov.setSucursal(inv.getSucursal());
        mov.setCantidad(Math.abs(nuevo - anterior));
        mov.setStockAnterior(anterior);
        mov.setStockNuevo(nuevo);
        mov.setReferencia("CATALOGO-" + inv.getId());
        mov.setNotas(nota);
        mov.setUsuario(admin);
        movimientoRepository.save(mov);
    }

    private String nombreDe(InventarioMedicamento i) {
        return i.getMedicamento().getNombre() + " — " + i.getSucursal().getNombre();
    }

    private InventarioMedicamento buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new IllegalArgumentException("El registro no existe."));
    }

    private Map<String, Object> aMapa(InventarioMedicamento i) {
        Integer minimo = i.getMedicamento().getStockMinimo();
        boolean bajo = minimo != null && i.getStockActual() <= minimo;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("nombre", nombreDe(i));
        m.put("medicamentoId", i.getMedicamento().getId());
        m.put("sucursalId", i.getSucursal().getId());
        m.put("stockActual", i.getStockActual());
        m.put("stockMinimo", minimo);
        m.put("alerta", bajo ? "Stock bajo" : "");
        m.put("alertaMensaje", bajo ? i.getMedicamento().getNombre() + ": Stock bajo — disponible: " + i.getStockActual() + " (mínimo: " + minimo + ")." : null);
        m.put("activo", i.isActivo());
        return m;
    }
}
