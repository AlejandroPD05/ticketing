package com.royaltown.helpdesk.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.royaltown.helpdesk.dao.ComentarioDao;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.Prioridad;
import com.royaltown.helpdesk.model.Ticket;
import com.royaltown.helpdesk.service.CatalogoService;
import com.royaltown.helpdesk.service.ReporteService;

/**
 * Backend HTTP de solo lectura del helpdesk, construido sobre
 * `com.sun.net.httpserver.HttpServer` (del propio JDK): expone los datos que
 * ya viven en la base de datos a traves de la MISMA capa de servicios y DAOs
 * que la CLI, sin ninguna dependencia nueva ni SQL crudo.
 *
 * Decisiones de diseno:
 *
 *   * **Solo lectura.** No hay endpoints de escritura (POST/PUT/DELETE). La
 *     autoridad sobre mutaciones sigue siendo la CLI, que pasa por sesion,
 *     permisos y `Transaccion.ejecutar(...)`. Exponer escritura sin autori-
 *     zacion romperia el modelo de permisos; cuando se necesite, se anadira
 *     autenticacion y validacion de idempotencia antes que endpoints.
 *   * **CORS abierto (`Access-Control-Allow-Origin: *`).** La web del
 *     proyecto (GitHub Pages) es estatica y en otro origen: abrir CORS
 *     permite probar la API desde el navegador en local. En produccion se
 *     restringiria al origen de la aplicacion.
 *   * **Errores uniformes.** Todo fallo responde `{"error": "mensaje"}` con
 *     el codigo HTTP adecuado (400 parametros, 404 inexistente, 405 metodo,
 *     500 interno). Los fallos inesperados se registran en stderr y el
 *     cliente recibe un mensaje generico: nunca un stacktrace.
 */
public final class ServidorApi {

    /** Version expuesta en /api/salud (misma que el proyecto Maven). */
    public static final String VERSION = "1.0.0";

    private static final int TAMANO_POR_DEFECTO = 20;
    private static final int TAMANO_MAXIMO = 100;

    private final HttpServer servidor;
    private final CatalogoService catalogo;
    private final TicketDao tickets;
    private final ComentarioDao comentarios;
    private final HistorialDao historial;
    private final ReporteService informes;

    /**
     * @param puerto Puerto TCP; 0 deja que el sistema asigne uno efimero
     *               (util en pruebas, el real se consulta con `puerto()`).
     */
    public ServidorApi(int puerto, CatalogoService catalogo, TicketDao tickets,
                       ComentarioDao comentarios, HistorialDao historial,
                       ReporteService informes) throws IOException {
        this.catalogo = catalogo;
        this.tickets = tickets;
        this.comentarios = comentarios;
        this.historial = historial;
        this.informes = informes;
        this.servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        this.servidor.createContext("/", this::enrutar);
        this.servidor.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void arrancar() {
        servidor.start();
    }

    public void apagar() {
        servidor.stop(0);
    }

    /** Puerto real en escucha (necesario con puerto efimero 0). */
    public int puerto() {
        return servidor.getAddress().getPort();
    }

    /* ------------------------------------------------------------------ */
    /* Enrutado                                                             */
    /* ------------------------------------------------------------------ */

    /** Ruta cada petición al endpoint que corresponda. */
    private void enrutar(HttpExchange canje) throws IOException {
        try {
            String metodo = canje.getRequestMethod();
            String ruta = canje.getRequestURI().getPath();

            if ("OPTIONS".equals(metodo)) {           // preflight de CORS
                responder(canje, 204, null);
            } else if (!"GET".equals(metodo)) {
                error(canje, 405, "Metodo no soportado: " + metodo
                        + ". Esta API es de solo lectura (GET).");
            } else if ("/api/salud".equals(ruta)) {
                salud(canje);
            } else if ("/api/tickets".equals(ruta)) {
                listarTickets(canje);
            } else if (ruta.startsWith("/api/tickets/")) {
                detalleTicket(canje, ruta.substring("/api/tickets/".length()));
            } else if ("/api/catalogo".equals(ruta)) {
                verCatalogo(canje);
            } else if ("/api/informes/kpis".equals(ruta)) {
                kpis(canje);
            } else {
                error(canje, 404, "Ruta desconocida: " + ruta);
            }
        } catch (PeticionInvalida e) {
            error(canje, e.codigo, e.getMessage());
        } catch (Exception e) {
            // El detalle va al log del servidor; el cliente solo ve un
            // mensaje generico (nunca stacktrace ni internals de la BD).
            System.err.println("[api] Error interno atendiendo "
                    + canje.getRequestMethod() + " " + canje.getRequestURI());
            e.printStackTrace(System.err);
            error(canje, 500, "Error interno del servidor");
        } finally {
            canje.close();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Endpoints                                                            */
    /* ------------------------------------------------------------------ */

    /** GET /api/salud: ping liviano que no toca la base de datos. */
    private void salud(HttpExchange canje) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", "ok");
        cuerpo.put("servicio", "helpdesk-api");
        cuerpo.put("version", VERSION);
        cuerpo.put("fechaHora", java.time.LocalDateTime.now().toString());
        responder(canje, 200, cuerpo);
    }

    /**
     * GET /api/tickets?estado=&prioridad=&pagina=&tamano=
     *
     * Reutiliza el filtro del TicketDao (PreparedStatement parametrizado);
     * la paginacion se aplica sobre la lista filtrada, que en un helpdesk
     * interno nunca es lo bastante grande como para justificar LIMIT/OFFSET
     * y asi el DAO sigue siendo identico para CLI y API.
     */
    private void listarTickets(HttpExchange canje) throws IOException {
        Map<String, String> parametros = parametros(canje);

        TicketDao.Filtro filtro = TicketDao.Filtro.todo();
        String estado = parametros.get("estado");
        if (estado != null && !estado.isBlank()) {
            filtro = filtro.deEstado(idEstadoDesde(estado));
        }
        String prioridad = parametros.get("prioridad");
        if (prioridad != null && !prioridad.isBlank()) {
            filtro = filtro.dePrioridad(idPrioridadDesde(prioridad));
        }

        int pagina = entero(parametros, "pagina", 1, 1, Integer.MAX_VALUE);
        int tamano = entero(parametros, "tamano", TAMANO_POR_DEFECTO, 1, TAMANO_MAXIMO);

        List<Ticket> filtrados = tickets.listar(filtro);
        int total = filtrados.size();
        int totalPaginas = Math.max(1, (total + tamano - 1) / tamano);
        if (pagina > totalPaginas) {
            throw new PeticionInvalida("La pagina " + pagina
                    + " no existe (hay " + totalPaginas + " paginas).");
        }
        List<Ticket> paginaTickets = filtrados
                .subList((pagina - 1) * tamano, Math.min(pagina * tamano, total));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("pagina", pagina);
        cuerpo.put("tamano", tamano);
        cuerpo.put("total", total);
        cuerpo.put("totalPaginas", totalPaginas);
        cuerpo.put("tickets", paginaTickets);
        responder(canje, 200, cuerpo);
    }

    /** GET /api/tickets/{id}: detalle + comentarios + historial completo. */
    private void detalleTicket(HttpExchange canje, String idTexto) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idTexto);
        } catch (NumberFormatException e) {
            throw new PeticionInvalida("El identificador de ticket debe ser numerico: "
                    + idTexto);
        }
        Ticket ticket = tickets.porId(id)
                .orElseThrow(() -> new PeticionInvalida(404,
                        "No existe el ticket #" + id));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ticket", ticket);
        cuerpo.put("comentarios", comentarios.porTicket(id));
        cuerpo.put("historial", historial.porTicket(id));
        responder(canje, 200, cuerpo);
    }

    /** GET /api/catalogo: estados, prioridades, categorias y transiciones. */
    private void verCatalogo(HttpExchange canje) throws IOException {
        Catalogo cat = catalogo.catalogo();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estados", cat.estados());
        cuerpo.put("prioridades", cat.prioridades());
        cuerpo.put("categorias", cat.categorias());
        cuerpo.put("transiciones", cat.transiciones());
        responder(canje, 200, cuerpo);
    }

    /**
     * GET /api/informes/kpis: los KPIs de las vistas de la base de datos
     * (cola abierta, SLA incumplido, resolucion por tecnico y reparto por
     * estado), tal cual los sirve la capa de informes de la CLI.
     */
    private void kpis(HttpExchange canje) throws IOException {
        var cola = informes.cola();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ticketsAbiertos", cola.size());
        cuerpo.put("colaTrabajo", cola);
        cuerpo.put("slaIncumplidos", informes.slaIncumplidos());
        cuerpo.put("resolucionPorTecnico", informes.resolucionPorTecnico());
        cuerpo.put("ticketsPorEstado", informes.ticketsPorEstado());
        responder(canje, 200, cuerpo);
    }

    /* ------------------------------------------------------------------ */
    /* Parametros de consulta                                               */
    /* ------------------------------------------------------------------ */

    /** Parsea la query string decodificando URL-encoding (UTF-8). */
    private Map<String, String> parametros(HttpExchange canje) {
        Map<String, String> resultado = new LinkedHashMap<>();
        String consulta = canje.getRequestURI().getRawQuery();
        if (consulta == null || consulta.isBlank()) {
            return resultado;
        }
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            String clave = igual < 0 ? par : par.substring(0, igual);
            String valor = igual < 0 ? "" : par.substring(igual + 1);
            resultado.put(decodificar(clave), decodificar(valor));
        }
        return resultado;
    }

    private static String decodificar(String texto) {
        return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    }

    private int entero(Map<String, String> parametros, String nombre,
                       int defecto, int minimo, int maximo) {
        String valor = parametros.get(nombre);
        if (valor == null || valor.isBlank()) {
            return defecto;
        }
        int numero;
        try {
            numero = Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new PeticionInvalida("El parametro '" + nombre
                    + "' debe ser un numero entero: " + valor);
        }
        if (numero < minimo || numero > maximo) {
            throw new PeticionInvalida("El parametro '" + nombre + "' debe estar entre "
                    + minimo + " y " + maximo + ": " + numero);
        }
        return numero;
    }

    /**
     * Acepta el id numerico o el nombre (p. ej. `estado=Abierto`); lo resuelve
     * contra el catalogo en memoria, sin SQL adicional.
     */
    private int idEstadoDesde(String valor) {
        Catalogo cat = catalogo.catalogo();
        try {
            int id = Integer.parseInt(valor);
            if (cat.estado(id).isEmpty()) {
                throw new PeticionInvalida("Estado desconocido: " + valor);
            }
            return id;
        } catch (NumberFormatException e) {
            Estado estado = cat.estadoPorNombre(valor)
                    .orElseThrow(() -> new PeticionInvalida("Estado desconocido: " + valor));
            return estado.id();
        }
    }

    private int idPrioridadDesde(String valor) {
        Catalogo cat = catalogo.catalogo();
        try {
            int id = Integer.parseInt(valor);
            if (cat.prioridad(id).isEmpty()) {
                throw new PeticionInvalida("Prioridad desconocida: " + valor);
            }
            return id;
        } catch (NumberFormatException e) {
            Prioridad prioridad = cat.prioridades().stream()
                    .filter(p -> p.nombre().equalsIgnoreCase(valor))
                    .findFirst()
                    .orElseThrow(() -> new PeticionInvalida("Prioridad desconocida: " + valor));
            return prioridad.id();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Respuestas                                                           */
    /* ------------------------------------------------------------------ */

    /** TODA respuesta pasa por aqui: JSON UTF-8 + cabecera CORS. */
    private void responder(HttpExchange canje, int codigo, Object cuerpo) throws IOException {
        canje.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        canje.getResponseHeaders().set("Content-Type",
                "application/json; charset=utf-8");

        if (cuerpo == null) {                       // 204 sin contenido
            canje.sendResponseHeaders(codigo, -1);
            return;
        }
        byte[] bytes = Json.de(cuerpo).getBytes(StandardCharsets.UTF_8);
        canje.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = canje.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private void error(HttpExchange canje, int codigo, String mensaje) throws IOException {
        responder(canje, codigo, Map.of("error", mensaje));
    }

    /** Parametro invalido (400) o recurso inexistente (404). */
    private static final class PeticionInvalida extends RuntimeException {

        private final int codigo;

        private PeticionInvalida(String mensaje) {
            this(400, mensaje);
        }

        private PeticionInvalida(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }
    }
}
