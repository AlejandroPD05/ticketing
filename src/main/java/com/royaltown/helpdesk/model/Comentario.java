package com.royaltown.helpdesk.model;

import java.time.LocalDateTime;

/**
 * Entrada del hilo de conversacion de un ticket. `autorNombre` llega de un
 * JOIN con Usuarios para poder pintar la CLI sin llamadas extra.
 * `esInterno` marca notas visibles solo para soporte.
 */
public record Comentario(int id, int idTicket, int idUsuario, String autorNombre,
                         String texto, boolean esInterno, LocalDateTime fecha) {
}
