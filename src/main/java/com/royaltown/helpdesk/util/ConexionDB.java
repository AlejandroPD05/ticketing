package com.royaltown.helpdesk.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Punto unico de acceso a la base de datos.
 *
 * Resuelve la conexion en este orden de prioridad:
 *   1. Variables de entorno  HELPDESK_DB_URL / _USER / _PASSWORD
 *   2. Fichero src/main/resources/db.properties (o db.properties en la raiz)
 *   3. Valores por defecto: MySQL local en Docker (bd helpdesk)
 *
 * No es un pool: en una CLI una unica conexion por operacion es suficiente y
 * mantiene el codigo de transacciones explicito y legible. Para escalar se
 * sustituiria por un DataSource (HikariCP) sin tocar la capa de servicios.
 */
public final class ConexionDB {

    private final String url;
    private final String usuario;
    private final String password;

    public ConexionDB(String url, String usuario, String password) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
    }

    public static ConexionDB porDefecto() {
        Properties propiedades = cargarPropiedades();

        String url = System.getenv().getOrDefault("HELPDESK_DB_URL",
                propiedades.getProperty("db.url",
                        "jdbc:mysql://localhost:3306/helpdesk?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"));
        String usuario = System.getenv().getOrDefault("HELPDESK_DB_USER",
                propiedades.getProperty("db.user", "helpdesk"));
        String password = System.getenv().getOrDefault("HELPDESK_DB_PASSWORD",
                propiedades.getProperty("db.password", "helpdesk"));

        return new ConexionDB(url, usuario, password);
    }

    /** Abre una conexion nueva. Quien la recibe es responsable de cerrarla. */
    public Connection abrir() throws SQLException {
        try {
            return DriverManager.getConnection(url, usuario, password);
        } catch (SQLException e) {
            throw new SQLException(
                    "No se pudo conectar a la base de datos (" + url + "). "
                            + "¿Está MySQL en marcha? Revisa README.md -> Montaje.",
                    e);
        }
    }

    /** Solo para mensajes de error: no expone la contrasena. */
    public String descripcion() {
        return url.replaceAll("password=[^&;]*", "password=***");
    }

    private static Properties cargarPropiedades() {
        Properties propiedades = new Properties();
        String[] rutas = {"db.properties", "src/main/resources/db.properties"};

        try (InputStream entrada = ConexionDB.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (entrada != null) {
                propiedades.load(entrada);
                return propiedades;
            }
        } catch (IOException ignorado) {
            // se sigue con las rutas en disco y con los valores por defecto
        }

        for (String ruta : rutas) {
            Path fichero = Path.of(ruta);
            if (Files.exists(fichero)) {
                try (InputStream entrada = Files.newInputStream(fichero)) {
                    propiedades.load(entrada);
                    return propiedades;
                } catch (IOException ignorado) {
                    // se continua con los valores por defecto
                }
            }
        }
        return propiedades;
    }
}
