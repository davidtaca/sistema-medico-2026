package com.hospital.sistemamedico.service.catalogo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades de validación compartidas por los catálogos (RN-CU15-01 a 04):
 * lectura de textos, nombre obligatorio con longitud máxima, descripción,
 * precios, enteros y descriptores de campos y columnas para la pantalla.
 */
public final class CatalogoBase {

    private CatalogoBase() {}

    /** Valor de texto sin espacios en los extremos; "" si no viene. */
    public static String texto(Map<String, Object> datos, String clave) {
        Object v = datos.get(clave);
        return v == null ? "" : v.toString().trim();
    }

    /** RN-CU15-01: el nombre es obligatorio y no puede exceder el máximo del catálogo. */
    public static String nombre(Map<String, Object> datos, int max) {
        String n = texto(datos, "nombre");
        if (n.isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (n.length() > max) {
            throw new IllegalArgumentException("El nombre no puede exceder " + max + " caracteres.");
        }
        return n;
    }

    /** Descripción: obligatoria solo en algunos catálogos; con longitud máxima. Devuelve null si está vacía y es opcional. */
    public static String descripcion(Map<String, Object> datos, boolean obligatoria, int max) {
        String d = texto(datos, "descripcion");
        if (d.isEmpty()) {
            if (obligatoria) {
                throw new IllegalArgumentException("La descripción es obligatoria.");
            }
            return null;
        }
        if (d.length() > max) {
            throw new IllegalArgumentException("La descripción no puede exceder " + max + " caracteres.");
        }
        return d;
    }

    /** Estado: por defecto Activo. Acepta true/false, 1/0 y "ACTIVO"/"INACTIVO". */
    public static boolean activo(Map<String, Object> datos) {
        Object v = datos.get("activo");
        if (v == null || v.toString().isBlank()) {
            return true;
        }
        String s = v.toString().trim().toUpperCase();
        return s.equals("TRUE") || s.equals("1") || s.equals("ACTIVO");
    }

    /** Booleano opcional (por defecto false). */
    public static boolean booleano(Map<String, Object> datos, String clave) {
        Object v = datos.get(clave);
        return v != null && (v.toString().equalsIgnoreCase("true") || v.toString().equals("1"));
    }

    /** Precio obligatorio, mayor a 0 y con precisión (10,2): máximo 2 decimales y 8 enteros. */
    public static BigDecimal precio(Map<String, Object> datos, String clave, String mensajeMayorCero) {
        String t = texto(datos, clave);
        BigDecimal p;
        try {
            p = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(mensajeMayorCero);
        }
        if (p.signum() <= 0) {
            throw new IllegalArgumentException(mensajeMayorCero);
        }
        if (p.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El precio debe tener máximo 2 decimales.");
        }
        if (p.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("El precio no puede exceder 99,999,999.99.");
        }
        return p.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Entero mayor o igual a 0. Si el campo viene vacío devuelve null (cuando es opcional) o falla (cuando es obligatorio). */
    public static Integer enteroNoNegativo(Map<String, Object> datos, String clave, boolean obligatorio, String mensaje) {
        String t = texto(datos, clave);
        if (t.isEmpty()) {
            if (obligatorio) {
                throw new IllegalArgumentException(mensaje);
            }
            return null;
        }
        try {
            int n = Integer.parseInt(t);
            if (n < 0) {
                throw new IllegalArgumentException(mensaje);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    /** Id de una selección; null si no se eligió nada. */
    public static Long idSeleccion(Map<String, Object> datos, String clave) {
        String t = texto(datos, clave);
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String yaExiste(String nombre) {
        return "Ya existe un registro con el nombre " + nombre + ".";
    }

    // ------------------------------------------------------------ descriptores para la pantalla

    /**
     * Descriptor de un campo del formulario.
     *
     * @param tipo texto, area, decimal, entero, booleano o seleccion
     */
    public static Map<String, Object> campo(String nombre, String etiqueta, String tipo, boolean obligatorio, Integer max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("etiqueta", etiqueta);
        m.put("tipo", tipo);
        m.put("obligatorio", obligatorio);
        if (max != null) {
            m.put("max", max);
        }
        return m;
    }

    /** Campo que solo se puede indicar al crear el registro (no se edita después). */
    public static Map<String, Object> soloCreacion(Map<String, Object> campo) {
        campo.put("soloCreacion", true);
        return campo;
    }

    public static Map<String, Object> columna(String campo, String etiqueta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campo", campo);
        m.put("etiqueta", etiqueta);
        return m;
    }
}
