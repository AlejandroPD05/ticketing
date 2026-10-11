package com.royaltown.helpdesk.model;

import java.time.LocalDateTime;

/**
 * Linea de la tabla `Historial_Ticket`: un campo concreto del ticket cambió
 * de `valorAnterior` a `valorNuevo`, autor y fecha incluidos. Es la pieza que
 * hace el ciclo de vida trazable de principio a fin.
 */
public record HistorialCambio(int id, int idTicket, int idUsuario, String autorNombre,
                              String campo, String valorAnterior, String valorNuevo,
                              LocalDateTime fecha) {
}
