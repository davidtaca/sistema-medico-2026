package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.Rol;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.service.catalogo.CatalogoAdaptador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio del mantenimiento de catálogos (CU-15). Recibe las peticiones del
 * administrador, las dirige al catálogo correspondiente y registra cada alta,
 * cambio o baja en la bitácora de auditoría. Los mensajes de éxito son los
 * del documento.
 */
@Service
public class CatalogoService {

    private final Map<String, CatalogoAdaptador> catalogos = new LinkedHashMap<>();

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    public CatalogoService(List<CatalogoAdaptador> adaptadores) {
        adaptadores.stream().sorted(Comparator.comparingInt(CatalogoAdaptador::orden))
                .forEach(a -> catalogos.put(a.clave(), a));
    }

    /** Catálogos disponibles para el menú lateral. */
    public List<Map<String, Object>> listarCatalogos() {
        return catalogos.values().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", a.clave());
            m.put("titulo", a.titulo());
            return m;
        }).collect(Collectors.toList());
    }

    /** Descripción de un catálogo para que la pantalla arme la tabla y el formulario. */
    public Map<String, Object> esquema(String clave) {
        CatalogoAdaptador a = adaptador(clave);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", a.clave());
        m.put("titulo", a.titulo());
        m.put("nota", a.nota());
        m.put("campos", a.campos());
        m.put("columnas", a.columnas());
        return m;
    }

    /**
     * Listado con filtros y paginación (paso 2).
     *
     * @param texto texto a buscar en los valores del registro (opcional)
     * @param estado "ACTIVO", "INACTIVO" o vacío para todos
     * @param pagina número de página desde 0
     * @param tamano registros por página (1 a 100)
     */
    public Map<String, Object> listar(String clave, String texto, String estado, int pagina, int tamano) {
        CatalogoAdaptador a = adaptador(clave);
        String filtroEstado = estado == null ? "" : estado.trim().toUpperCase();
        if (!filtroEstado.isEmpty() && !filtroEstado.equals("ACTIVO") && !filtroEstado.equals("INACTIVO")) {
            throw new IllegalArgumentException("El filtro de estado no es válido.");
        }
        String q = texto == null ? "" : texto.trim().toLowerCase();
        if (q.length() > 100) {
            throw new IllegalArgumentException("El texto de búsqueda no puede exceder 100 caracteres.");
        }

        List<Map<String, Object>> filtradas = a.filas().stream()
                .filter(f -> filtroEstado.isEmpty() || (Boolean.TRUE.equals(f.get("activo")) == filtroEstado.equals("ACTIVO")))
                .filter(f -> q.isEmpty() || f.values().stream().anyMatch(v -> v != null && v.toString().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        int t = Math.max(1, Math.min(tamano, 100));
        int p = Math.max(0, pagina);
        int total = filtradas.size();
        int desde = Math.min(p * t, total);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contenido", filtradas.subList(desde, Math.min(desde + t, total)));
        m.put("pagina", p);
        m.put("tamano", t);
        m.put("totalElementos", total);
        m.put("totalPaginas", (int) Math.ceil(total / (double) t));
        return m;
    }

    public Map<String, Object> obtener(String clave, Long id) {
        return adaptador(clave).obtener(id);
    }

    public List<Map<String, Object>> opciones(String clave, String campo) {
        return adaptador(clave).opciones(campo);
    }

    /** Crea un registro (pasos 3 a 8). */
    @Transactional
    public String crear(Long adminId, String clave, Map<String, Object> datos) {
        Usuario admin = validarAdministrador(adminId);
        CatalogoAdaptador a = adaptador(clave);
        String nombre = a.crear(datos, admin);
        auditoriaService.registrar(admin, "CREAR_CATALOGO", a.titulo(), null, "Se creó el registro " + nombre + " en el catálogo " + a.titulo() + ".");
        return "El registro " + nombre + " ha sido creado exitosamente.";
    }

    /** Edita un registro (FA02). */
    @Transactional
    public String actualizar(Long adminId, String clave, Long id, Map<String, Object> datos) {
        Usuario admin = validarAdministrador(adminId);
        CatalogoAdaptador a = adaptador(clave);
        String nombre = a.actualizar(id, datos, admin);
        auditoriaService.registrar(admin, "ACTUALIZAR_CATALOGO", a.titulo(), id, "Se actualizó el registro " + nombre + " del catálogo " + a.titulo() + ".");
        return "El registro " + nombre + " ha sido actualizado correctamente.";
    }

    /** Elimina de forma lógica (FA03). */
    @Transactional
    public String eliminar(Long adminId, String clave, Long id) {
        Usuario admin = validarAdministrador(adminId);
        CatalogoAdaptador a = adaptador(clave);
        String nombre = a.eliminar(id, admin);
        auditoriaService.registrar(admin, "ELIMINAR_CATALOGO", a.titulo(), id, "Se eliminó (baja lógica) el registro " + nombre + " del catálogo " + a.titulo() + ".");
        return "El registro " + nombre + " ha sido eliminado correctamente.";
    }

    private CatalogoAdaptador adaptador(String clave) {
        CatalogoAdaptador a = catalogos.get(clave);
        if (a == null) {
            throw new IllegalArgumentException("El catálogo indicado no existe.");
        }
        return a;
    }

    private Usuario validarAdministrador(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Debe indicar el usuario administrador.");
        }
        Usuario u = usuarioService.buscarPorId(adminId);
        if (u.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("Solo un administrador puede mantener los catálogos del sistema.");
        }
        return u;
    }
}
