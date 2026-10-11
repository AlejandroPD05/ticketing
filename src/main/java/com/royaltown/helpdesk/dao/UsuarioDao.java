package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Rol;
import com.royaltown.helpdesk.model.Usuario;
import com.royaltown.helpdesk.util.ConexionDB;

/** Acceso a la tabla Usuarios (el rol llega con JOIN a Roles). */
public class UsuarioDao {

    private static final String COLUMNAS =
            "SELECT u.id_usuario, u.nombre_completo, u.email, u.password_hash, "
                    + "r.nombre AS rol, u.departamento, u.activo "
                    + "FROM Usuarios u JOIN Roles r ON r.id_rol = u.id_rol ";

    private final ConexionDB db;

    public UsuarioDao(ConexionDB db) {
        this.db = db;
    }

    public Optional<Usuario> porEmail(String email) {
        String sql = COLUMNAS + "WHERE LOWER(u.email) = LOWER(?)";
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo buscar el usuario " + email, e);
        }
    }

    public Optional<Usuario> porId(int id) {
        String sql = COLUMNAS + "WHERE u.id_usuario = ?";
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el usuario " + id, e);
        }
    }

    /** Tecnicos activos, para elegir a quien se asigna un ticket. */
    public List<Usuario> tecnicos() {
        String sql = COLUMNAS
                + "WHERE r.nombre = 'tecnico' AND u.activo = TRUE ORDER BY u.nombre_completo";
        List<Usuario> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudieron leer los tecnicos", e);
        }
        return resultado;
    }

    public List<Usuario> porRol(Rol rol) {
        String sql = COLUMNAS + "WHERE r.nombre = ? AND u.activo = TRUE ORDER BY u.nombre_completo";
        List<Usuario> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rol.nombre());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudieron leer los usuarios del rol " + rol, e);
        }
        return resultado;
    }

    static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id_usuario"),
                rs.getString("nombre_completo"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Rol.desdeNombre(rs.getString("rol")),
                rs.getString("departamento"),
                rs.getBoolean("activo"));
    }
}
