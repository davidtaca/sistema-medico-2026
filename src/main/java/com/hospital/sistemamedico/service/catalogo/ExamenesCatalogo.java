package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.Examen;
import com.hospital.sistemamedico.model.Laboratorio;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.ExamenRepository;
import com.hospital.sistemamedico.repository.LaboratorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hospital.sistemamedico.service.catalogo.CatalogoBase.*;

/**
 * Catálogo de exámenes de laboratorio (CU-15, RN-CU15-03): nombre (200),
 * descripción (500, opcional), precio base mayor a 0 con 2 decimales,
 * laboratorio activo (obligatorio), y unidad y rango de referencia opcionales
 * que el CU-09 usa para marcar resultados fuera de rango.
 */
@Component
public class ExamenesCatalogo implements CatalogoAdaptador {

    @Autowired
    private ExamenRepository repositorio;

    @Autowired
    private LaboratorioRepository laboratorioRepository;

    @Override public String clave() { return "examenes"; }
    @Override public String titulo() { return "Exámenes de Laboratorio"; }
    @Override public int orden() { return 5; }

    @Override
    public List<Map<String, Object>> campos() {
        Map<String, Object> lab = campo("laboratorioId", "Laboratorio", "seleccion", true, null);
        return List.of(
                campo("nombre", "Nombre", "texto", true, 200),
                campo("descripcion", "Descripción", "area", false, 500),
                campo("precio", "Precio base (Q)", "decimal", true, null),
                lab,
                campo("unidad", "Unidad", "texto", false, 50),
                campo("rangoReferencia", "Rango de referencia", "texto", false, 100));
    }

    @Override
    public List<Map<String, Object>> columnas() {
        return List.of(columna("nombre", "Nombre"), columna("laboratorio", "Laboratorio"), columna("precio", "Precio (Q)"),
                columna("unidad", "Unidad"), columna("rangoReferencia", "Rango de referencia"));
    }

    @Override
    public List<Map<String, Object>> opciones(String campo) {
        if (!"laboratorioId".equals(campo)) {
            throw new IllegalArgumentException("El campo indicado no tiene opciones.");
        }
        return laboratorioRepository.findAll().stream().filter(Laboratorio::isActivo)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(l -> Map.<String, Object>of("id", l.getId(), "etiqueta", l.getNombre())).collect(Collectors.toList());
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
        Datos d = validar(datos);
        List<Examen> mismos = repositorio.findAll().stream().filter(e -> e.getNombre().equalsIgnoreCase(d.nombre)).collect(Collectors.toList());
        if (mismos.stream().anyMatch(Examen::isActivo)) {
            throw new IllegalArgumentException(yaExiste(d.nombre));
        }
        Examen e = mismos.isEmpty() ? new Examen() : mismos.get(0);
        aplicar(e, d, true);
        repositorio.save(e);
        return d.nombre;
    }

    @Override
    public String actualizar(Long id, Map<String, Object> datos, Usuario admin) {
        Examen e = buscar(id);
        Datos d = validar(datos);
        boolean activo = activo(datos);
        List<Examen> otros = repositorio.findAll().stream()
                .filter(o -> !o.getId().equals(id) && o.getNombre().equalsIgnoreCase(d.nombre)).collect(Collectors.toList());
        if (!otros.isEmpty()) {
            throw new IllegalArgumentException(yaExiste(d.nombre));
        }
        aplicar(e, d, activo);
        repositorio.save(e);
        return d.nombre;
    }

    @Override
    public String eliminar(Long id, Usuario admin) {
        Examen e = buscar(id);
        if (!e.isActivo()) {
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        }
        e.setActivo(false);
        repositorio.save(e);
        return e.getNombre();
    }

    private record Datos(String nombre, String descripcion, java.math.BigDecimal precio, Laboratorio laboratorio, String unidad, String rango) {}

    private Datos validar(Map<String, Object> datos) {
        String nombre = nombre(datos, 200);
        String descripcion = descripcion(datos, false, 500);
        java.math.BigDecimal precio = precio(datos, "precio", "El precio base debe ser mayor a 0.");
        Long labId = idSeleccion(datos, "laboratorioId");
        if (labId == null) {
            throw new IllegalArgumentException("Debe seleccionar un laboratorio.");
        }
        Laboratorio lab = laboratorioRepository.findById(labId).filter(Laboratorio::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Debe seleccionar un laboratorio."));
        String unidad = texto(datos, "unidad");
        String rango = texto(datos, "rangoReferencia");
        if (unidad.length() > 50) {
            throw new IllegalArgumentException("La unidad no puede exceder 50 caracteres.");
        }
        if (rango.length() > 100) {
            throw new IllegalArgumentException("El rango de referencia no puede exceder 100 caracteres.");
        }
        return new Datos(nombre, descripcion, precio, lab, unidad.isEmpty() ? null : unidad, rango.isEmpty() ? null : rango);
    }

    private void aplicar(Examen e, Datos d, boolean activo) {
        e.setNombre(d.nombre);
        e.setDescripcion(d.descripcion);
        e.setPrecio(d.precio);
        e.setLaboratorio(d.laboratorio);
        e.setUnidad(d.unidad);
        e.setRangoReferencia(d.rango);
        e.setActivo(activo);
    }

    private Examen buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new IllegalArgumentException("El registro no existe."));
    }

    private Map<String, Object> aMapa(Examen e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("nombre", e.getNombre());
        m.put("descripcion", e.getDescripcion());
        m.put("precio", e.getPrecio());
        m.put("laboratorioId", e.getLaboratorio() == null ? null : e.getLaboratorio().getId());
        m.put("laboratorio", e.getLaboratorio() == null ? null : e.getLaboratorio().getNombre());
        m.put("unidad", e.getUnidad());
        m.put("rangoReferencia", e.getRangoReferencia());
        m.put("activo", e.isActivo());
        return m;
    }
}
