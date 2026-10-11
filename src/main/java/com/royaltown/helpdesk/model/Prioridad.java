package com.royaltown.helpdesk.model;

import java.time.Duration;

/**
 * Fila de la tabla lookup `Prioridades`: su `slaHoras` alimenta el
 * `fecha_limite` de cada ticket.
 */
public record Prioridad(int id, String nombre, int orden, int slaHoras) {

    public Duration sla() {
        return Duration.ofHours(slaHoras);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
