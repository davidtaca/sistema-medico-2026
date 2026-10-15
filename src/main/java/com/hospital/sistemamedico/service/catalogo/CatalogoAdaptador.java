package com.hospital.sistemamedico.service.catalogo;

import com.hospital.sistemamedico.model.Usuario;

import java.util.List;
import java.util.Map;

/**
 * Un catálogo administrable desde el mantenimiento de catálogos (CU-15).
 * Cada implementación sabe describir sus campos (para que la pantalla genere
 * el formulario y la tabla), listar sus registros y crear, actualizar o
 * eliminar (de forma lógica) un registro, aplicando sus propias validaciones.
 */
public interface CatalogoAdaptador {

    /** Identificador usado en las rutas, por ejemplo "especialidades". */
    String clave();

    /** Nombre mostrado en el menú, por ejemplo "Especialidades". */
    String titulo();

    /** Posición en el menú lateral. */
    int orden();

    /** Texto aclaratorio opcional que se muestra sobre la tabla (por ejemplo, si el catálogo es solo informativo). */
    default String nota() {
        return null;
    }

    /** Campos del formulario: nombre, etiqueta, tipo, obligatorio, máximo y, si es selección, de dónde salen las opciones. */
    List<Map<String, Object>> campos();

    /** Columnas de la tabla, además del ID y del estado. */
    List<Map<String, Object>> columnas();

    /** Todos los registros del catálogo, activos e inactivos, con "id", "activo" y los valores de las columnas. */
    List<Map<String, Object>> filas();

    /** Valores de un registro para precargar el formulario de edición. */
    Map<String, Object> obtener(Long id);

    /** Crea un registro. @return el nombre del registro creado, para el mensaje de éxito */
    String crear(Map<String, Object> datos, Usuario admin);

    /** Actualiza un registro. @return el nombre del registro */
    String actualizar(Long id, Map<String, Object> datos, Usuario admin);

    /** Elimina de forma lógica (estado inactivo). @return el nombre del registro */
    String eliminar(Long id, Usuario admin);

    /** Opciones de un campo de selección: lista de {id, etiqueta}. */
    default List<Map<String, Object>> opciones(String campo) {
        throw new IllegalArgumentException("El campo indicado no tiene opciones.");
    }
}
