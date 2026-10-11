package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Valoracion;
import com.royaltown.helpdesk.util.ConexionDB;

/** Valoraciones de satisfaccion (una por ticket, UNIQUE en base de datos). */
public class ValoracionDao {

    private final ConexionDB db;

    public ValoracionDao(ConexionDB db) {
        this.db = db;
    }

    public void insertar(Connection conn, int idTicket, int puntuacion,
                         String comentario) throws SQLException {
        String sql = "INSERT INTO Valoraciones (id_ticket, puntuacion, comentario) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            ps.setInt(2, puntuacion);
            ps.setString(3, comentario);
            ps.executeUpdate();
        }
    }

    public Optional<Valoracion> porTicket(int idTicket) {
        String sql = "SELECT id_valoracion, id_ticket, puntuacion, comentario, fecha_valoracion "
                + "FROM Valoraciones WHERE id_ticket = ?";
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Valoracion(
                        rs.getInt("id_valoracion"),
                        rs.getInt("id_ticket"),
                        rs.getInt("puntuacion"),
                        rs.getString("comentario"),
                        rs.getTimestamp("fecha_valoracion").toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new HelpdeskException(
                    "No se pudo leer la valoracion del ticket #" + idTicket, e);
        }
    }
}
