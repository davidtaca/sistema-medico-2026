package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hospital.sistemamedico.service.catalogo.CatalogoBase.*;

/**
 * Catálogo de sucursales (CU-15, RN-CU15-04): nombre (100), teléfono de
 * exactamente 8 dígitos (opcional), dirección (500, opcional) y descripción
 * (250, opcional).
 */
@Component
public class SucursalesCatalogo implements CatalogoAdaptador {

    @Autowired
    private SucursalRepository repositorio;

    @Override public String clave() { return "sucursales"; }
    @Override public String titulo() { return "Sucursales"; }
    @Override public int orden() { return 2; }

    @Override
    public List<Map<String, Object>> campos() {
        return List.of(
                campo("nombre", "Nombre", "texto", true, 100),
                campo("telefono", "Teléfono", "texto", false, 8),
                campo("direccion", "Dirección", "area", false, 500),
                campo("descripcion", "Descripción", "area", false, 250));
    }

    @Override
    public List<Map<String, Object>> columnas() {
        return List.of(columna("nombre", "Nombre"), columna("telefono", "Teléfono"), columna("direccion", "Dirección"));
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
        String nombre = nombre(datos, 100);
        String telefono = telefono(datos);
        String direccion = largo(datos, "direccion", "La dirección", 500);
        String descripcion = largo(datos, "descripcion", "La descripción", 250);

        List<Sucursal> mismas = repositorio.findAll().stream().filter(s -> s.getNombre().equalsIgnoreCase(nombre)).collect(Collectors.toList());
        if (mismas.stream().anyMatch(Sucursal::isActivo)) {
            throw new IllegalArgumentException(yaExiste(nombre));
        }
        Sucursal s = mismas.isEmpty() ? new Sucursal() : mismas.get(0);
        aplicar(s, nombre, telefono, direccion, descripcion, true);
        repositorio.save(s);
        return nombre;
    }

    @Override
    public String actualizar(Long id, Map<String, Object> datos, Usuario admin) {
        Sucursal s = buscar(id);
        String nombre = nombre(datos, 100);
        String telefono = telefono(datos);
        String direccion = largo(datos, "direccion", "La dirección", 500);
        String descripcion = largo(datos, "descripcion", "La descripción", 250);
        boolean activo = activo(datos);

        List<Sucursal> otras = repositorio.findAll().stream()
                .filter(o -> !o.getId().equals(id) && o.getNombre().equalsIgnoreCase(nombre)).collect(Collectors.toList());
        // la base de datos exige nombres distintos incluso entre inactivas
        if (!otras.isEmpty()) {
            throw new IllegalArgumentException(yaExiste(nombre));
        }
        aplicar(s, nombre, telefono, direccion, descripcion, activo);
        repositorio.save(s);
        return nombre;
    }

    @Override
    public String eliminar(Long id, Usuario admin) {
        Sucursal s = buscar(id);
        if (!s.isActivo()) {
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        }
        s.setActivo(false);
        repositorio.save(s);
        return s.getNombre();
    }

    /** RN-CU15-04: si se ingresa, el teléfono debe tener exactamente 8 dígitos numéricos. */
    private String telefono(Map<String, Object> datos) {
        String t = texto(datos, "telefono");
        if (t.isEmpty()) {
            return null;
        }
        if (!t.matches("\\d{8}")) {
            throw new IllegalArgumentException("El teléfono debe tener exactamente 8 dígitos.");
        }
        return t;
    }

    private String largo(Map<String, Object> datos, String clave, String etiqueta, int max) {
        String v = texto(datos, clave);
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > max) {
            throw new IllegalArgumentException(etiqueta + " no puede exceder " + max + " caracteres.");
        }
        return v;
    }

    private void aplicar(Sucursal s, String nombre, String telefono, String direccion, String descripcion, boolean activo) {
        s.setNombre(nombre);
        s.setTelefono(telefono);
        s.setDireccion(direccion);
        s.setDescripcion(descripcion);
        s.setActivo(activo);
    }

    private Sucursal buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new IllegalArgumentException("El registro no existe."));
    }

    private Map<String, Object> aMapa(Sucursal s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("nombre", s.getNombre());
        m.put("telefono", s.getTelefono());
        m.put("direccion", s.getDireccion());
        m.put("descripcion", s.getDescripcion());
        m.put("activo", s.isActivo());
        return m;
    }
}
