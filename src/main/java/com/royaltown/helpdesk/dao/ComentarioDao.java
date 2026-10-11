package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Comentario;
import com.royaltown.helpdesk.util.ConexionDB;

/** Acceso al hilo de comentarios de cada ticket. */
public class ComentarioDao {

    private final ConexionDB db;

    public ComentarioDao(ConexionDB db) {
        this.db = db;
    }

    /** Inserta dentro de la transaccion abierta por la capa de servicios. */
    public void insertar(Connection conn, int idTicket, int idUsuario, String texto,
                         boolean esInterno) throws SQLException {
        String sql = "INSERT INTO Comentarios_Ticket (id_ticket, id_usuario, comentario, es_interno) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            ps.setInt(2, idUsuario);
            ps.setString(3, texto);
            ps.setBoolean(4, esInterno);
            ps.executeUpdate();
        }
    }

    /** Hilo completo, en orden cronologico. El autor llega via JOIN. */
    public List<Comentario> porTicket(int idTicket) {
        String sql = "SELECT c.id_comentario, c.id_ticket, c.id_usuario, "
                + "u.nombre_completo AS autor, c.comentario, c.es_interno, c.fecha_comentario "
                + "FROM Comentarios_Ticket c JOIN Usuarios u ON u.id_usuario = c.id_usuario "
                + "WHERE c.id_ticket = ? ORDER BY c.fecha_comentario, c.id_comentario";
        List<Comentario> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new Comentario(
                            rs.getInt("id_comentario"),
                            rs.getInt("id_ticket"),
                            rs.getInt("id_usuario"),
                            rs.getString("autor"),
                            rs.getString("comentario"),
                            rs.getBoolean("es_interno"),
                            rs.getTimestamp("fecha_comentario").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el hilo del ticket #" + idTicket, e);
        }
        return resultado;
    }
}
