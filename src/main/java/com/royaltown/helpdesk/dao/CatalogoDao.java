package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.Prioridad;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * Lectura de las tablas lookup y de la matriz de transiciones.
 * Solo metodos de lectura: la semilla es la fuente de verdad y la CLI las
 * carga una sola vez al arrancar.
 */
public class CatalogoDao {

    private final ConexionDB db;

    public CatalogoDao(ConexionDB db) {
        this.db = db;
    }

    public List<Estado> estados() {
        String sql = "SELECT id_estado, nombre, orden, es_final FROM Estados ORDER BY orden";
        List<Estado> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Estado(
                        rs.getInt("id_estado"),
                        rs.getString("nombre"),
                        rs.getInt("orden"),
                        rs.getBoolean("es_final")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudieron leer los estados", e);
        }
        return resultado;
    }

    public List<Prioridad> prioridades() {
        String sql = "SELECT id_prioridad, nombre, orden, sla_horas FROM Prioridades ORDER BY orden";
        List<Prioridad> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Prioridad(
                        rs.getInt("id_prioridad"),
                        rs.getString("nombre"),
                        rs.getInt("orden"),
                        rs.getInt("sla_horas")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudieron leer las prioridades", e);
        }
        return resultado;
    }

    public List<Categoria> categorias() {
        String sql = "SELECT id_categoria, nombre, descripcion FROM Categorias ORDER BY nombre";
        List<Categoria> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Categoria(
                        rs.getInt("id_categoria"),
                        rs.getString("nombre"),
                        rs.getString("descripcion")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudieron leer las categorias", e);
        }
        return resultado;
    }

    public List<Catalogo.Transicion> transiciones() {
        String sql = "SELECT id_estado_origen, id_estado_destino FROM Transiciones_Estado";
        List<Catalogo.Transicion> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Catalogo.Transicion(
                        rs.getInt("id_estado_origen"),
                        rs.getInt("id_estado_destino")));
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer la matriz de transiciones", e);
        }
        return resultado;
    }

    /** Carga el catalogo completo en memoria. */
    public Catalogo cargar() {
        return new Catalogo(estados(), prioridades(), categorias(), transiciones());
    }
}
