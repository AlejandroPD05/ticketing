package com.royaltown.helpdesk.model;

import java.time.LocalDateTime;

/** Usuario autenticado + momento de inicio de sesion. */
public record Sesion(Usuario usuario, LocalDateTime inicio) {

    public int idUsuario() {
        return usuario.id();
    }

    public boolean esSoporte() {
        return usuario.esSoporte();
    }

    public boolean esAdministrador() {
        return usuario.esAdministrador();
    }
}
