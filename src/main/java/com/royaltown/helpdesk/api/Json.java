package com.royaltown.helpdesk.api;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Serializador JSON propio, sin ninguna dependencia externa (el proyecto no
 * quiere bibliotecas nuevas solo para exponer una API de demostracion).
 *
 * Soporta lo que necesita la API y un poco mas: null, booleanos, numeros,
 * caracteres, cadenas, fechas y horas ISO-8601, enumeraciones, listas y
 * colecciones, arrays, mapas y —lo importante— **records**, que se vuelcan
 * campo a campo usando los componentes del record. Ese es el mecanismo que
 * usa la capa de modelo inmutable del proyecto: basta con pasar el record
 * a {@code Json.de(...)} para obtener su representacion.
 *
 * TODA cadena pasa por el escapado obligatorio de {@code cadena(...)}:
 * comillas dobles, barras invertidas y caracteres de control (< 0x20) se
 * codifican con sus secuencias JSON escapadas. Ningun campo puede volcarse
 * por concatenacion cruda, asi que un texto de usuario con comillas nunca
 * puede romper la estructura del documento ni inyectarle campos.
 */
public final class Json {

    private Json() {
    }

    /** Devuelve la representacion JSON del valor recibido. */
    public static String de(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            cadena(sb, s);
        } else if (valor instanceof Boolean || valor instanceof Byte
                || valor instanceof Short || valor instanceof Integer || valor instanceof Long
                || valor instanceof BigInteger || valor instanceof BigDecimal) {
            sb.append(valor);
        } else if (valor instanceof Double d) {
            escribirDouble(sb, d.isNaN() || d.isInfinite() ? null : valor);
        } else if (valor instanceof Float f) {
            escribirDouble(sb, f.isNaN() || f.isInfinite() ? null : valor);
        } else if (valor instanceof Character c) {
            cadena(sb, String.valueOf(c));
        } else if (valor instanceof LocalDateTime ldt) {
            cadena(sb, ldt.toString());   // ISO-8601: 2026-01-31T12:34:56
        } else if (valor instanceof LocalDate ld) {
            cadena(sb, ld.toString());
        } else if (valor instanceof LocalTime lt) {
            cadena(sb, lt.toString());
        } else if (valor instanceof Instant instant) {
            cadena(sb, instant.toString());
        } else if (valor instanceof Enum<?> e) {
            cadena(sb, e.name());
        } else if (valor instanceof Record registro) {
            objeto(sb, registro);
        } else if (valor instanceof Map<?, ?> mapa) {
            mapaJson(sb, mapa);
        } else if (valor instanceof Iterable<?> iterable) {
            array(sb, iterable);
        } else if (valor.getClass().isArray()) {
            array(sb, valor);
        } else {
            // Ultimo recurso seguro: tambien pasa por el escapado.
            cadena(sb, valor.toString());
        }
    }

    private static void escribirDouble(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");   // JSON no admite NaN ni Infinity
        } else {
            sb.append(valor);
        }
    }

    /** Un record se vuelca como objeto: nombre del componente -> valor. */
    private static void objeto(StringBuilder sb, Record registro) {
        sb.append('{');
        RecordComponent[] componentes = registro.getClass().getRecordComponents();
        for (int i = 0; i < componentes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            cadena(sb, componentes[i].getName());
            sb.append(':');
            try {
                escribir(sb, componentes[i].getAccessor().invoke(registro));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "No se pudo leer el campo " + componentes[i].getName()
                                + " del record " + registro.getClass().getSimpleName(), e);
            }
        }
        sb.append('}');
    }

    private static void mapaJson(StringBuilder sb, Map<?, ?> mapa) {
        sb.append('{');
        boolean primero = true;
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            cadena(sb, String.valueOf(entrada.getKey()));
            sb.append(':');
            escribir(sb, entrada.getValue());
        }
        sb.append('}');
    }

    private static void array(StringBuilder sb, Iterable<?> iterable) {
        sb.append('[');
        boolean primero = true;
        for (Object elemento : iterable) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribir(sb, elemento);
        }
        sb.append(']');
    }

    private static void array(StringBuilder sb, Object array) {
        sb.append('[');
        for (int i = 0; i < Array.getLength(array); i++) {
            if (i > 0) {
                sb.append(',');
            }
            escribir(sb, Array.get(array, i));
        }
        sb.append(']');
    }

    /** Escapado JSON obligatorio de toda cadena. */
    private static void cadena(StringBuilder sb, String texto) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
