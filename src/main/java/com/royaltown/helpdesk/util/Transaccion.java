package com.royaltown.helpdesk.util;

import java.sql.Connection;
import java.sql.SQLException;

import com.royaltown.helpdesk.exception.HelpdeskException;

/**
 * Ayudante de transacciones: una operacion = una transaccion.
 *
 * todo-o-nada: si la operacion lanza SQLException se revierte y se traduce en
 * HelpdeskException; si lanza una excepcion de dominio (regla de negocio) se
 * revierte igualmente y se propaga tal cual. Asi el ticket y su linea de
 * historial nunca pueden quedar a medias.
 */
public final class Transaccion {

    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar(Connection conn) throws SQLException;
    }

    private Transaccion() {
    }

    public static <T> T ejecutar(ConexionDB db, Operacion<T> operacion) {
        try (Connection conn = db.abrir()) {
            try {
                conn.setAutoCommit(false);
                T resultado = operacion.ejecutar(conn);
                conn.commit();
                return resultado;
            } catch (SQLException e) {
                conn.rollback();
                throw new HelpdeskException(
                        "Error de base de datos; se ha revertido la operacion: "
                                + e.getMessage(), e);
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new HelpdeskException("No se pudo abrir la transaccion", e);
        }
    }
}
