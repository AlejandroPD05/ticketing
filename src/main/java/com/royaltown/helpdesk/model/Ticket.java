package com.royaltown.helpdesk.model;

import java.time.LocalDateTime;

/**
 * Ticket con su ciclo de vida completo. Es inmutable: las operaciones del
 * servicio devuelven una copia actualizada (`conEstado`, `conTecnico`...), lo
 * que deja trazas claras de cada cambio en el codigo.
 */
public record Ticket(int id, String titulo, String descripcion,
                     Estado estado, Prioridad prioridad,
                     LocalDateTime fechaCreacion, LocalDateTime fechaLimite,
                     LocalDateTime fechaCierre,
                     Categoria categoria, int usuarioCreador,
                     Integer tecnicoAsignado) {

    public boolean estaAbierto() {
        return !estado.esFinal();
    }

    public boolean estaCerrado() {
        return estado.esFinal();
    }

    public Ticket conEstado(Estado nuevo) {
        return new Ticket(id, titulo, descripcion, nuevo, prioridad,
                fechaCreacion, fechaLimite, fechaCierre, categoria,
                usuarioCreador, tecnicoAsignado);
    }

    public Ticket conEstadoYFechaCierre(Estado nuevo, LocalDateTime cierre) {
        return new Ticket(id, titulo, descripcion, nuevo, prioridad,
                fechaCreacion, fechaLimite, cierre, categoria,
                usuarioCreador, tecnicoAsignado);
    }

    public Ticket conTecnico(Integer nuevoTecnico) {
        return new Ticket(id, titulo, descripcion, estado, prioridad,
                fechaCreacion, fechaLimite, fechaCierre, categoria,
                usuarioCreador, nuevoTecnico);
    }

    public Ticket conPrioridad(Prioridad nueva, LocalDateTime nuevoLimite) {
        return new Ticket(id, titulo, descripcion, estado, nueva,
                fechaCreacion, nuevoLimite, fechaCierre, categoria,
                usuarioCreador, tecnicoAsignado);
    }
}
