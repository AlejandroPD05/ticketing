package com.royaltown.helpdesk.model;

/**
 * Roles del sistema. En base de datos son la tabla lookup `Roles`; el enum
 * fija el vocabulario con el que el codigo toma decisiones de permisos.
 */
public enum Rol {
    EMPLEADO("empleado"),
    TECNICO("tecnico"),
    ADMINISTRADOR("administrador");

    private final String nombre;

    Rol(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    /** @return true si el rol puede operar como soporte (asignar, transicionar...). */
    public boolean esSoporte() {
        return this == TECNICO || this == ADMINISTRADOR;
    }

    public static Rol desdeNombre(String nombre) {
        for (Rol rol : values()) {
            if (rol.nombre.equalsIgnoreCase(nombre)) {
                return rol;
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + nombre);
    }
}
