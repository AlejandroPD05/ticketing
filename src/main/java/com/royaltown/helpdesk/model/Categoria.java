package com.royaltown.helpdesk.model;

public record Categoria(int id, String nombre, String descripcion) {

    @Override
    public String toString() {
        return nombre;
    }
}
