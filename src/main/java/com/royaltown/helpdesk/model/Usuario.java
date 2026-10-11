package com.royaltown.helpdesk.model;

/**
 * Usuario del sistema. `passwordHash` viaja en el registro porque la CLI del
 * login necesita verificarlo; nunca se imprime en pantalla.
 */
public record Usuario(int id, String nombreCompleto, String email, String passwordHash,
                      Rol rol, String departamento, boolean activo) {

    public boolean esSoporte() {
        return rol.esSoporte();
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    @Override
    public String toString() {
        return nombreCompleto + " (" + rol.nombre() + ")";
    }
}
