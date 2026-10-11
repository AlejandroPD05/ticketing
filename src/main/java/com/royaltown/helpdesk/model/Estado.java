package com.royaltown.helpdesk.model;

/**
 * Fila de la tabla lookup `Estados`. El campo `orden` respeta el ciclo de
 * vida y `esFinal` distingue los estados que cierran el ticket.
 */
public record Estado(int id, String nombre, int orden, boolean esFinal) {

    @Override
    public String toString() {
        return nombre;
    }
}
