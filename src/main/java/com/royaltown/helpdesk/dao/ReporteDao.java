package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.informe.FilaEstado;
import com.royaltown.helpdesk.model.informe.FilaSla;
import com.royaltown.helpdesk.model.informe.FilaTecnico;
import com.royaltown.helpdesk.model.informe.TicketAbierto;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * Lectura de las vistas del dashboard. Las vistas viven en base de datos a
 * proposito: el KPI se calcula ahi y el codigo solo las recoge, de modo que
 * cambiar la definicion del informe no toca Java.
 */
public class ReporteDao {

    private final ConexionDB db;

    public ReporteDao(ConexionDB db) {
        this.db = db;
    }

    public List<TicketAbierto> cola() {
        String sql = "SELECT id_ticket, titulo, categoria, prioridad, estado, "
                + "COALESCE(tecnico, 'Sin asignar') AS tecnico, fecha_creacion, fecha_limite "
                + "FROM vw_tickets_abiertos ORDER BY fecha_limite";
        List<TicketAbierto> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new TicketAbierto(
                        rs.getInt("id_ticket"),
                        rs.getString("titulo"),
                        rs.getString("categoria"),
                        rs.getString("prioridad"),
                        rs.getString("estado"),
                        rs.getString("tecnico"),
                        rs.getTimestamp("fecha_creacion").toLocalDateTime(),
                        rs.getTimestamp("fecha_limite").toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer la cola de trabajo", e);
        }
        return resultado;
    }

    public List<FilaSla> slaIncumplidos() {
        String sql = "SELECT id_ticket, titulo, prioridad, sla_horas, fecha_creacion, "
                + "fecha_limite, COALESCE(tecnico, 'Sin asignar') AS tecnico "
                + "FROM vw_sla_incumplido ORDER BY fecha_limite";
        List<FilaSla> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new FilaSla(
                        rs.getInt("id_ticket"),
                        rs.getString("titulo"),
                        rs.getString("prioridad"),
                        rs.getInt("sla_horas"),
                        rs.getTimestamp("fecha_creacion").toLocalDateTime(),
                        rs.getTimestamp("fecha_limite").toLocalDateTime(),
                        rs.getString("tecnico")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el informe de SLA", e);
        }
        return resultado;
    }

    public List<FilaTecnico> resolucionPorTecnico() {
        String sql = "SELECT nombre_completo, tickets_resueltos, minutos_medios "
                + "FROM vw_resolucion_por_tecnico ORDER BY minutos_medios";
        List<FilaTecnico> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long minutos = rs.getLong("minutos_medios");
                resultado.add(new FilaTecnico(
                        rs.getString("nombre_completo"),
                        rs.getLong("tickets_resueltos"),
                        rs.wasNull() ? null : minutos));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el informe por tecnico", e);
        }
        return resultado;
    }

    public List<FilaEstado> ticketsPorEstado() {
        String sql = "SELECT nombre, orden, total FROM vw_tickets_por_estado ORDER BY orden";
        List<FilaEstado> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new FilaEstado(
                        rs.getString("nombre"),
                        rs.getInt("orden"),
                        rs.getLong("total")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el reparto por estado", e);
        }
        return resultado;
    }
}
