package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Catalogo;

/** Acceso a la tabla Categorias. */
public class CategoriaDao {

    private final Supplier<Catalogo> catalogo;

    public CategoriaDao(Supplier<Catalogo> catalogo) {
        this.catalogo = catalogo;
    }

    public Optional<Categoria> porId(int id) {
        return catalogo.get().categoria(id);
    }

    /** Alta de categoria (solo administrador, la valida la capa de servicios). */
    public Categoria crear(Connection conn, String nombre, String descripcion) throws SQLException {
        String sql = "INSERT INTO Categorias (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No se devolvio el id de la categoria creada");
                }
                return new Categoria(rs.getInt(1), nombre, descripcion);
            }
        }
    }

    public List<Categoria> todas() {
        return catalogo.get().categorias();
    }
}
