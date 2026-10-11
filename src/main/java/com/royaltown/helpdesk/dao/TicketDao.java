package com.royaltown.helpdesk.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Ticket;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * DAO de Tickets.
 *
 * Patron unit-of-work: los metodos de escritura reciben la `Connection` del
 * servicio para que toda la operacion (ticket + historial) viva dentro de la
 * misma transaccion; los metodos de lectura abren y cierran la suya.
 *
 * El hidratado de estados y prioridades sale del `Catalogo` en memoria
 * (suministrado como `Supplier`, de modo que una recarga del catalogo no
 * obliga a construir otro DAO).
 */
public class TicketDao {

    /** Filtro de listado combinable. */
    public record Filtro(Integer idCreador, Integer idTecnico, Integer idEstado,
                         Boolean soloSinResolver) {
        public static Filtro todo() {
            return new Filtro(null, null, null, null);
        }

        public Filtro deCreador(int id) {
            return new Filtro(id, idTecnico, idEstado, soloSinResolver);
        }

        public Filtro deTecnico(int id) {
            return new Filtro(idCreador, id, idEstado, soloSinResolver);
        }

        public Filtro sinResolver() {
            return new Filtro(idCreador, idTecnico, idEstado, Boolean.TRUE);
        }
    }

    private static final String COLUMNAS =
            "SELECT t.id_ticket, t.titulo, t.descripcion, t.id_estado, t.id_prioridad, "
                    + "t.fecha_creacion, t.fecha_limite, t.fecha_cierre, "
                    + "t.id_categoria, t.usuario_creador, t.tecnico_asignado "
                    + "FROM Tickets t JOIN Estados e ON e.id_estado = t.id_estado ";

    private final ConexionDB db;
    private final Supplier<Catalogo> catalogo;

    public TicketDao(ConexionDB db, Supplier<Catalogo> catalogo) {
        this.db = db;
        this.catalogo = catalogo;
    }

    public int insertar(Connection conn, Ticket datos) throws SQLException {
        String sql = "INSERT INTO Tickets (titulo, descripcion, id_estado, id_prioridad, "
                + "fecha_creacion, fecha_limite, id_categoria, usuario_creador, tecnico_asignado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, datos.titulo());
            ps.setString(2, datos.descripcion());
            ps.setInt(3, datos.estado().id());
            ps.setInt(4, datos.prioridad().id());
            ps.setTimestamp(5, Timestamp.valueOf(datos.fechaCreacion()));
            ps.setTimestamp(6, Timestamp.valueOf(datos.fechaLimite()));
            ps.setInt(7, datos.categoria().id());
            ps.setInt(8, datos.usuarioCreador());
            if (datos.tecnicoAsignado() == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, datos.tecnicoAsignado());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No se devolvio el id del ticket creado");
                }
                return rs.getInt(1);
            }
        }
    }

    public Optional<Ticket> porId(int id) {
        String sql = COLUMNAS + "WHERE t.id_ticket = ?";
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo leer el ticket #" + id, e);
        }
    }

    public List<Ticket> listar(Filtro filtro) {
        StringBuilder sql = new StringBuilder(COLUMNAS);
        List<Object> parametros = new ArrayList<>();

        sql.append("WHERE 1 = 1 ");
        if (filtro.idCreador() != null) {
            sql.append("AND t.usuario_creador = ? ");
            parametros.add(filtro.idCreador());
        }
        if (filtro.idTecnico() != null) {
            sql.append("AND t.tecnico_asignado = ? ");
            parametros.add(filtro.idTecnico());
        }
        if (filtro.idEstado() != null) {
            sql.append("AND t.id_estado = ? ");
            parametros.add(filtro.idEstado());
        }
        if (Boolean.TRUE.equals(filtro.soloSinResolver())) {
            sql.append("AND e.nombre NOT IN ('Resuelto', 'Cerrado') ");
        }
        sql.append("ORDER BY t.fecha_creacion DESC");

        List<Ticket> resultado = new ArrayList<>();
        try (Connection conn = db.abrir();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo listar los tickets", e);
        }
        return resultado;
    }

    public void cambiarEstado(Connection conn, int idTicket, int idEstado,
                              LocalDateTime fechaCierre) throws SQLException {
        String sql = "UPDATE Tickets SET id_estado = ?, fecha_cierre = ? WHERE id_ticket = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idEstado);
            if (fechaCierre == null) {
                ps.setNull(2, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(2, Timestamp.valueOf(fechaCierre));
            }
            ps.setInt(3, idTicket);
            ps.executeUpdate();
        }
    }

    public void asignarTecnico(Connection conn, int idTicket, Integer idTecnico) throws SQLException {
        String sql = "UPDATE Tickets SET tecnico_asignado = ? WHERE id_ticket = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idTecnico == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, idTecnico);
            }
            ps.setInt(2, idTicket);
            ps.executeUpdate();
        }
    }

    public void cambiarPrioridad(Connection conn, int idTicket, int idPrioridad,
                                 LocalDateTime fechaLimite) throws SQLException {
        String sql = "UPDATE Tickets SET id_prioridad = ?, fecha_limite = ? WHERE id_ticket = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPrioridad);
            ps.setTimestamp(2, Timestamp.valueOf(fechaLimite));
            ps.setInt(3, idTicket);
            ps.executeUpdate();
        }
    }

    private Ticket mapear(ResultSet rs) throws SQLException {
        Catalogo cat = catalogo.get();
        int idCategoria = rs.getInt("id_categoria");
        Categoria categoria = cat.categoria(idCategoria)
                .orElseGet(() -> new Categoria(idCategoria, "categoria #" + idCategoria, null));
        int tecnico = rs.getInt("tecnico_asignado");
        boolean sinTecnico = rs.wasNull();

        return new Ticket(
                rs.getInt("id_ticket"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                cat.estadoObligatorio(rs.getInt("id_estado")),
                cat.prioridadObligatoria(rs.getInt("id_prioridad")),
                toLocalDateTime(rs.getTimestamp("fecha_creacion")),
                toLocalDateTime(rs.getTimestamp("fecha_limite")),
                toLocalDateTime(rs.getTimestamp("fecha_cierre")),
                categoria,
                rs.getInt("usuario_creador"),
                sinTecnico ? null : tecnico);
    }

    private static LocalDateTime toLocalDateTime(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
