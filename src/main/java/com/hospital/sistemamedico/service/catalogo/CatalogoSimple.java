package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hospital.sistemamedico.service.catalogo.CatalogoBase.*;

/**
 * Adaptador para los catálogos que solo tienen nombre, descripción y estado
 * (especialidades, estados de cita, laboratorios y roles). Aplica las
 * validaciones comunes de RN-CU15-01: nombre obligatorio, descripción
 * obligatoria solo donde corresponde y nombre único entre los registros
 * activos.
 *
 * @param <T> entidad del catálogo
 */
public class CatalogoSimple<T> implements CatalogoAdaptador {

    private final String clave;
    private final String titulo;
    private final int orden;
    private final String nota;
    private final JpaRepository<T, Long> repositorio;
    private final int maxNombre;
    private final int maxDescripcion;
    private final boolean descripcionObligatoria;
    /** true si la tabla tiene una restricción única de nombre en la base de datos (también cuenta los inactivos). */
    private final boolean nombreUnicoEnBd;
    private final Supplier<T> nuevo;
    private final Function<T, Long> getId;
    private final Function<T, String> getNombre;
    private final BiConsumer<T, String> setNombre;
    private final Function<T, String> getDescripcion;
    private final BiConsumer<T, String> setDescripcion;
    private final Predicate<T> esActivo;
    private final BiConsumer<T, Boolean> setActivo;

    public CatalogoSimple(String clave, String titulo, int orden, String nota, JpaRepository<T, Long> repositorio,
                          int maxNombre, int maxDescripcion, boolean descripcionObligatoria, boolean nombreUnicoEnBd,
                          Supplier<T> nuevo, Function<T, Long> getId, Function<T, String> getNombre, BiConsumer<T, String> setNombre,
                          Function<T, String> getDescripcion, BiConsumer<T, String> setDescripcion,
                          Predicate<T> esActivo, BiConsumer<T, Boolean> setActivo) {
        this.clave = clave;
        this.titulo = titulo;
        this.orden = orden;
        this.nota = nota;
        this.repositorio = repositorio;
        this.maxNombre = maxNombre;
        this.maxDescripcion = maxDescripcion;
        this.descripcionObligatoria = descripcionObligatoria;
        this.nombreUnicoEnBd = nombreUnicoEnBd;
        this.nuevo = nuevo;
        this.getId = getId;
        this.getNombre = getNombre;
        this.setNombre = setNombre;
        this.getDescripcion = getDescripcion;
        this.setDescripcion = setDescripcion;
        this.esActivo = esActivo;
        this.setActivo = setActivo;
    }

    @Override public String clave() { return clave; }
    @Override public String titulo() { return titulo; }
    @Override public int orden() { return orden; }
    @Override public String nota() { return nota; }

    @Override
    public List<Map<String, Object>> campos() {
        return List.of(
                campo("nombre", "Nombre", "texto", true, maxNombre),
                campo("descripcion", "Descripción", "area", descripcionObligatoria, maxDescripcion));
    }

    @Override
    public List<Map<String, Object>> columnas() {
        return List.of(columna("nombre", "Nombre"), columna("descripcion", "Descripción"));
    }

    @Override
    public List<Map<String, Object>> filas() {
        return repositorio.findAll().stream().sorted((a, b) -> getId.apply(a).compareTo(getId.apply(b))).map(this::aMapa).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> obtener(Long id) {
        return aMapa(buscar(id));
    }

    @Override
    public String crear(Map<String, Object> datos, Usuario admin) {
        String nombre = nombre(datos, maxNombre);
        String descripcion = descripcion(datos, descripcionObligatoria, maxDescripcion);

        List<T> mismos = repositorio.findAll().stream().filter(t -> getNombre.apply(t).equalsIgnoreCase(nombre)).collect(Collectors.toList());
        if (mismos.stream().anyMatch(esActivo)) {
            throw new IllegalArgumentException(yaExiste(nombre));
        }
        // Si existe uno dado de baja con el mismo nombre se vuelve a activar (la base no permite repetir el nombre)
        T t = mismos.isEmpty() ? nuevo.get() : mismos.get(0);
        setNombre.accept(t, nombre);
        setDescripcion.accept(t, descripcion);
        setActivo.accept(t, true);
        repositorio.save(t);
        return nombre;
    }

    @Override
    public String actualizar(Long id, Map<String, Object> datos, Usuario admin) {
        T t = buscar(id);
        String nombre = nombre(datos, maxNombre);
        String descripcion = descripcion(datos, descripcionObligatoria, maxDescripcion);
        boolean activo = activo(datos);

        List<T> otros = repositorio.findAll().stream()
                .filter(o -> !getId.apply(o).equals(id) && getNombre.apply(o).equalsIgnoreCase(nombre)).collect(Collectors.toList());
        if ((activo && otros.stream().anyMatch(esActivo)) || (nombreUnicoEnBd && !otros.isEmpty())) {
            throw new IllegalArgumentException(yaExiste(nombre));
        }
        setNombre.accept(t, nombre);
        setDescripcion.accept(t, descripcion);
        setActivo.accept(t, activo);
        repositorio.save(t);
        return nombre;
    }

    @Override
    public String eliminar(Long id, Usuario admin) {
        T t = buscar(id);
        if (!esActivo.test(t)) {
            throw new IllegalArgumentException("El registro ya fue eliminado.");
        }
        setActivo.accept(t, false);
        repositorio.save(t);
        return getNombre.apply(t);
    }

    private T buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new IllegalArgumentException("El registro no existe."));
    }

    private Map<String, Object> aMapa(T t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId.apply(t));
        m.put("nombre", getNombre.apply(t));
        m.put("descripcion", getDescripcion.apply(t));
        m.put("activo", esActivo.test(t));
        return m;
    }
}
