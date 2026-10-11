package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.HistorialCambio;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * Escritura y lectura de la trazabilidad (`Historial_Ticket`).
 * Cada mutacion de un ticket debe insertar su linea aqui dentro de la misma
 * transaccion: o se aplican las dos o no se aplica ninguna.
 */
public class HistorialDao {

    private final ConexionDB db;

    public HistorialDao(ConexionDB db) {
        this.db = db;
    }

    public void registrar(Connection conn, int idTicket, int idUsuario, String campo,
                          String valorAnterior, String valorNuevo) throws SQLException {
        String sql = "INSERT INTO Historial_Ticket "
                + "(id_ticket, id_usuario, campo, valor_anterior, valor_nuevo) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            ps.setInt(2, idUsuario);
            ps.setString(3, campo);
            if (valorAnterior == null) {
                ps.setObject(4, null);
            } else {
                ps.setString(4, valorAnterior);
            }
            ps.setString(5, valorNuevo);
            ps.executeUpdate();
        }
    }

    /** Historial completo de un ticket, en orden cronologico. */
    public List<HistorialCambio> porTicket(int idTicket) {
        String sql = "SELECT h.id_historico, h.id_ticket, h.id_usuario, "
                + "u.nombre_completo AS autor, h.campo, h.valor_anterior, h.valor_nuevo, "
                + "h.fecha_cambio "
                + "FROM Historial_Ticket h JOIN Usuarios u ON u.id_usuario = h.id_usuario "
                + "WHERE h.id_ticket = ? ORDER BY h.fecha_cambio, h.id_historico";
        List<HistorialCambio> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTicket);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new HistorialCambio(
                            rs.getInt("id_historico"),
                            rs.getInt("id_ticket"),
                            rs.getInt("id_usuario"),
                            rs.getString("autor"),
                            rs.getString("campo"),
                            rs.getString("valor_anterior"),
                            rs.getString("valor_nuevo"),
                            rs.getTimestamp("fecha_cambio").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new HelpdeskException(
                    "No se pudo leer el historial del ticket #" + idTicket, e);
        }
        return resultado;
    }
}
