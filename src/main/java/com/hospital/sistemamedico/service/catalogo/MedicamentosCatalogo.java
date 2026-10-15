package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.Medicamento;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hospital.sistemamedico.service.catalogo.CatalogoBase.*;

/**
 * Catálogo de medicamentos (CU-15, RN-CU15-02): nombre (200), descripción
 * obligatoria (500), precio mayor a 0, unidad obligatoria (50), indicador de
 * medicamento controlado (su despacho queda en la auditoría especial, RNF-017)
 * y stock mínimo opcional para las alertas de reabastecimiento.
 */
@Component
public class MedicamentosCatalogo implements CatalogoAdaptador {

    @Autowired
    private MedicamentoRepository repositorio;

    @Override public String clave() { return "medicamentos"; }
    @Override public String titulo() { return "Medicamentos"; }
    @Override public int orden() { return 6; }

    @Override
    public List<Map<String, Object>> campos() {
        return List.of(
                campo("nombre", "Nombre", "texto", true, 200),
                campo("descripcion", "Descripción", "area", true, 500),
                campo("precio", "Precio (Q)", "decimal", true, null),
                campo("unidad", "Unidad (tableta, ml, etc.)", "texto", true, 50),
                campo("controlado", "Medicamento controlado", "booleano", false, null),
                campo("stockMinimo", "Stock mínimo", "entero", false, null));
    }

    @Override
    public List<Map<String, Object>> columnas() {
        return List.of(columna("nombre", "Nombre"), columna("precio", "Precio (Q)"), columna("unidad", "Unidad"),
                columna("controlado", "Controlado"), columna("stockMinimo", "Stock mínimo"));
    }

    @Override
    public List<Map<String, Object>> filas() {
        return repositorio.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).map(this::aMapa).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> obtener(Long id) {
        return aMapa(buscar(id));
    }

    private record Datos(String nombre, String descripcion, BigDecimal precio, String unidad, boolean controlado, Integer minimo) {}

    private Datos validar(Map<String, Object> datos) {
        String nombre = nombre(datos, 200);
        String descripcion = descripcion(datos, true, 500);
        BigDecimal precio = precio(datos, "precio", "El precio debe ser mayor a 0.");
        String unidad = texto(datos, "unidad");
        if (unidad.isEmpty()) {
            throw new IllegalArgumentException("La unidad es obligatoria.");
        }
        if (unidad.length() > 50) {
            throw new IllegalArgumentException("La unidad no puede exceder 50 caracteres.");
        }
        Integer minimo = enteroNoNegativo(datos, "stockMinimo", false, "El stock mínimo debe ser un número entero mayor o igual a 0.");
        return new Datos(nombre, descripcion, precio, unidad, booleano(datos, "controlado"), minimo);
    }

    @Override
    public String crear(Map<String, Object> datos, Usuario admin) {
        Datos d = validar(datos);
        List<Medicamento> mismos = repositorio.findAll().stream().filter(m -> m.getNombre().equalsIgnoreCase(d.nombre)).collect(Collectors.toList());
        if (mismos.stream().anyMatch(Medicamento::isActivo)) {
            throw new IllegalArgumentException(yaExiste(d.nombre));
        }
        Medicamento m = mismos.isEmpty() ? new Medicamento() : mismos.get(0);
        aplicar(m, d, true);
        repositorio.save(m);
        return d.nombre;
    }

    @Override
    public String actualizar(Long id, Map<String, Object> datos, Usuario admin) {
        Medicamento m = buscar(id);
        Datos d = validar(datos);
        boolean activo = activo(datos);
        List<Medicamento> otros = repositorio.findAll().stream()
                .filter(o -> !o.getId().equals(id) && o.getNombre().equalsIgnoreCase(d.nombre)).collect(Collectors.toList());
        if (!otros.isEmpty()) {
            throw new IllegalArgumentException(yaExiste(d.nombre));
        }
        aplicar(m, d, activo);
        repositorio.save(m);
        return d.nombre;
    }

    @Override
    public String eliminar(Long id, Usuario admin) {
        Medicamento m = buscar(id);
        if (!m.isActivo()) {
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        }
        m.setActivo(false);
        repositorio.save(m);
        return m.getNombre();
    }

    private void aplicar(Medicamento m, Datos d, boolean activo) {
        m.setNombre(d.nombre);
        m.setDescripcion(d.descripcion);
        m.setPrecio(d.precio);
        m.setUnidad(d.unidad);
        m.setControlado(d.controlado);
        m.setStockMinimo(d.minimo);
        m.setActivo(activo);
    }

    private Medicamento buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new IllegalArgumentException("El registro no existe."));
    }

    private Map<String, Object> aMapa(Medicamento m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("nombre", m.getNombre());
        r.put("descripcion", m.getDescripcion());
        r.put("precio", m.getPrecio());
        r.put("unidad", m.getUnidad());
        r.put("controlado", m.isControlado());
        r.put("stockMinimo", m.getStockMinimo());
        r.put("activo", m.isActivo());
        return r;
    }
}
