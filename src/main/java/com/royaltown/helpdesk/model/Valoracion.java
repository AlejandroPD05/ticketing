package com.royaltown.helpdesk.model;

import java.time.LocalDateTime;

/** Valoracion de satisfaccion que deja el creador al cerrarse el ticket. */
public record Valoracion(int id, int idTicket, int puntuacion, String comentario,
                         LocalDateTime fecha) {
}
