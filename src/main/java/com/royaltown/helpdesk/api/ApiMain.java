package com.royaltown.helpdesk.api;

import java.util.concurrent.CountDownLatch;

import com.royaltown.helpdesk.dao.CatalogoDao;
import com.royaltown.helpdesk.dao.ComentarioDao;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.ReporteDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.service.CatalogoService;
import com.royaltown.helpdesk.service.ReporteService;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * Punto de entrada de la API REST (alternativa a la CLI, que sigue siendo
 * `com.royaltown.helpdesk.cli.Main`).
 *
 * Monta exactamente el mismo armazón que la CLI —ConexionDB, catálogo en
 * memoria y DAOs— pero sirve los datos por HTTP en lugar de por consola:
 * CLI y API comparten servicios, DAOs y esquema, solo cambia la fachada.
 *
 * Uso:
 *   java ... com.royaltown.helpdesk.api.ApiMain [puerto]
 *   (puerto por defecto: 8080; la conexión a base de datos se resuelve con
 *    las mismas variables/propiedades que la CLI: HELPDESK_DB_URL, etc.)
 */
public final class ApiMain {

    private ApiMain() {
    }

    public static void main(String[] args) {
        int puerto = 8080;
        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto no valido: '" + args[0]
                        + "'. Uso: ApiMain [puerto]");
                System.exit(1);
            }
        }

        try {
            ConexionDB db = ConexionDB.porDefecto();
            CatalogoService catalogo = new CatalogoService(new CatalogoDao(db));
            TicketDao tickets = new TicketDao(db, catalogo);
            ComentarioDao comentarios = new ComentarioDao(db);
            HistorialDao historial = new HistorialDao(db);
            ReporteService informes = new ReporteService(new ReporteDao(db));

            ServidorApi servidor = new ServidorApi(puerto, catalogo, tickets,
                    comentarios, historial, informes);
            servidor.arrancar();

            System.out.println("API del helpdesk escuchando en http://localhost:"
                    + servidor.puerto() + "/api/salud");
            System.out.println("  GET /api/salud            estado y version");
            System.out.println("  GET /api/tickets          listado paginado (estado, prioridad)");
            System.out.println("  GET /api/tickets/{id}     detalle + comentarios + historial");
            System.out.println("  GET /api/catalogo         estados, prioridades, categorias");
            System.out.println("  GET /api/informes/kpis    KPIs de las vistas");
            System.out.println("Ctrl+C para detener.");

            Runtime.getRuntime().addShutdownHook(new Thread(servidor::apagar,
                    "apagar-api-helpdesk"));
            // El servidor corre en sus propios hilos: el main espera a la señal
            // de parada (Ctrl+C o kill) sin consumir CPU.
            new CountDownLatch(1).await();
        } catch (Exception e) {
            System.err.println("No se pudo arrancar la API: " + e.getMessage());
            System.err.println("¿Está MySQL en marcha? Revisa README.md -> Montaje.");
            System.exit(1);
        }
    }
}
