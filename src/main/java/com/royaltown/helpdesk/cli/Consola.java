package com.royaltown.helpdesk.cli;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

import com.royaltown.helpdesk.exception.HelpdeskException;

/**
 * Utilidades de presentacion de la CLI: entrada validada, mensajes con
 * formato y tablas alineadas. Sin dependencias externas.
 */
public final class Consola {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Scanner ENTRADA = new Scanner(System.in);

    private Consola() {
    }

    /* --- Entrada ------------------------------------------------------- */

    public static String linea(String prompt) {
        System.out.print(prompt);
        if (!ENTRADA.hasNextLine()) {
            throw new HelpdeskException("Entrada finalizada.");
        }
        return ENTRADA.nextLine().trim();
    }

    /** Entero dentro de un rango, repitiendo la pregunta mientras sea invalido. */
    public static int entero(String prompt, int min, int max) {
        while (true) {
            String texto = linea(prompt);
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= min && valor <= max) {
                    return valor;
                }
            } catch (NumberFormatException ignorado) {
                // se vuelve a pedir
            }
            System.out.println("  Introduce un numero entre " + min + " y " + max + ".");
        }
    }

    public static int enteroObligatorio(String prompt) {
        while (true) {
            String texto = linea(prompt);
            try {
                return Integer.parseInt(texto);
            } catch (NumberFormatException ignorado) {
                System.out.println("  Eso no es un numero de ticket valido.");
            }
        }
    }

    /** Entero sin rango (id de ticket, 0 incluido). */
    public static int enteroLibre(String prompt) {
        while (true) {
            String texto = linea(prompt);
            try {
                return Integer.parseInt(texto.trim());
            } catch (NumberFormatException ignorado) {
                System.out.println("  Introduce un numero.");
            }
        }
    }

    public static void pausa() {
        System.out.print("\nPulsa ENTER para continuar...");
        if (ENTRADA.hasNextLine()) {
            ENTRADA.nextLine();
        }
    }

    /* --- Salida -------------------------------------------------------- */

    public static void cabecera(String texto) {
        System.out.println();
        System.out.println("== " + texto + " ".repeat(Math.max(0, 58 - texto.length())) + "==");
    }

    public static void subtitulo(String texto) {
        System.out.println("\n-- " + texto);
    }

    public static void ok(String texto) {
        System.out.println("  [ok] " + texto);
    }

    public static void aviso(String texto) {
        System.out.println("  [!] " + texto);
    }

    public static void error(String texto) {
        System.out.println("  [error] " + texto);
    }

    public static void vacio(String texto) {
        System.out.println("  (" + texto + ")");
    }

    public static void imprimirTabla(String[] cabeceras, List<String[]> filas) {
        if (filas.isEmpty()) {
            vacio("sin resultados");
            return;
        }
        int[] anchos = new int[cabeceras.length];
        for (int i = 0; i < cabeceras.length; i++) {
            anchos[i] = cabeceras[i].length();
        }
        for (String[] fila : filas) {
            for (int i = 0; i < fila.length && i < anchos.length; i++) {
                anchos[i] = Math.max(anchos[i], fila[i].length());
            }
        }

        StringBuilder separador = new StringBuilder("  ");
        for (int ancho : anchos) {
            separador.append("-".repeat(ancho + 2)).append("+");
        }

        System.out.println("  " + String.join(" | ", repetir(cabeceras, anchos)));
        System.out.println(separador);
        for (String[] fila : filas) {
            System.out.println("  " + String.join(" | ", repetir(fila, anchos)));
        }
    }

    private static String[] repetir(String[] valores, int[] anchos) {
        String[] salida = new String[anchos.length];
        for (int i = 0; i < anchos.length; i++) {
            String valor = i < valores.length && valores[i] != null ? valores[i] : "";
            salida[i] = valor + " ".repeat(Math.max(0, anchos[i] - valor.length()));
        }
        return salida;
    }

    public static String fecha(LocalDateTime momento) {
        return momento == null ? "-" : momento.format(FECHA);
    }
}
