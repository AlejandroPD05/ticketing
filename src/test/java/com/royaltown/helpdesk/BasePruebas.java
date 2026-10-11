package com.royaltown.helpdesk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import com.royaltown.helpdesk.dao.CatalogoDao;
import com.royaltown.helpdesk.dao.CategoriaDao;
import com.royaltown.helpdesk.dao.ComentarioDao;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.ReporteDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.dao.UsuarioDao;
import com.royaltown.helpdesk.dao.ValoracionDao;
import com.royaltown.helpdesk.model.Rol;
import com.royaltown.helpdesk.model.Sesion;
import com.royaltown.helpdesk.model.Usuario;
import com.royaltown.helpdesk.service.AuthService;
import com.royaltown.helpdesk.service.CatalogoService;
import com.royaltown.helpdesk.service.ReporteService;
import com.royaltown.helpdesk.service.TicketService;
import com.royaltown.helpdesk.util.ConexionDB;

import org.h2.tools.RunScript;

/**
 * Base de las pruebas: monta un helpdesk completo en H2 (modo MySQL) en
 * memoria cargando el MISMO schema.sql de produccion, con su semilla.
 *
 * Asi los tests ejercitan el esquema real -vistas, constraints, CHECKs incluidos-
 * sin necesitar ningun servidor, y un cambio que rompa el esquema se ve en
 * `mvn test` antes que en MySQL.
 */
public abstract class BasePruebas {

    protected ConexionDB db;
    protected CatalogoService catalogo;
    protected AuthService auth;
    protected TicketService tickets;
    protected ReporteService informes;
    protected UsuarioDao usuarios;
    protected ComentarioDao comentarios;

    @BeforeEach
    void montarBase() throws Exception {
        String nombre = "helpdesk_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + nombre
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        db = new ConexionDB(url, "", "");

        String esquema = Files.readString(Path.of("schema.sql"), StandardCharsets.UTF_8);
        try (Connection conn = db.abrir()) {
            RunScript.execute(conn, new java.io.StringReader(esquema));
        }

        CatalogoDao catalogoDao = new CatalogoDao(db);
        catalogo = new CatalogoService(catalogoDao);
        usuarios = new UsuarioDao(db);
        CategoriaDao categorias = new CategoriaDao(catalogo::get);
        TicketDao ticketDao = new TicketDao(db, catalogo);
        comentarios = new ComentarioDao(db);
        HistorialDao historial = new HistorialDao(db);
        ValoracionDao valoraciones = new ValoracionDao(db);

        auth = new AuthService(usuarios);
        tickets = new TicketService(db, catalogo, ticketDao, usuarios,
                comentarios, historial, valoraciones, categorias);
        informes = new ReporteService(new ReporteDao(db));
    }

    /** Sesion de la semilla: 1 Marta y 2 Javier (empleados), 3 y 4 tecnicos, 5 admin. */
    protected Sesion sesion(int idUsuario) {
        Usuario usuario = usuarios.porId(idUsuario)
                .orElseThrow(() -> new AssertionError("usuario " + idUsuario + " en la semilla"));
        return new Sesion(usuario, java.time.LocalDateTime.now());
    }

    protected Sesion marta() {
        return sesion(1);
    }

    protected Sesion laura() {
        return sesion(3);
    }

    protected Sesion admin() {
        return sesion(5);
    }

    /** Crea un ticket de referencia (categoria 1 Hardware, prioridad 2 Media). */
    protected com.royaltown.helpdesk.model.Ticket ticketNuevo(Sesion creador) {
        return tickets.crear(creador, "Titulo de prueba", "Descripcion de prueba", 1, 2);
    }

    protected static Rol rolDe(Sesion sesion) {
        return sesion.usuario().rol();
    }
}
