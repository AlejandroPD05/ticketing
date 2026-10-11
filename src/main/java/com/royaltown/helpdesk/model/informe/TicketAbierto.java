package com.royaltown.helpdesk.model.informe;

import java.time.LocalDateTime;

/** Fila de vw_tickets_abiertos: la cola activa de soporte. */
public record TicketAbierto(int idTicket, String titulo, String categoria,
                            String prioridad, String estado, String tecnico,
                            LocalDateTime fechaCreacion, LocalDateTime fechaLimite) {
}
