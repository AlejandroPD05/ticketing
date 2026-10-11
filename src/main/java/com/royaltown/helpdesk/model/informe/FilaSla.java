package com.royaltown.helpdesk.model.informe;

import java.time.LocalDateTime;

/** Fila de vw_sla_incumplido: ticket sin resolver y fuera de plazo. */
public record FilaSla(int idTicket, String titulo, String prioridad, int slaHoras,
                      LocalDateTime fechaCreacion, LocalDateTime fechaLimite,
                      String tecnico) {
}
