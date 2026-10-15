package com.royaltown.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.royaltown.helpdesk.api.ServidorApi;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.ReporteDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.model.Ticket;
import com.royaltown.helpdesk.service.ReporteService;

/**
 * Pruebas de la API REST: el servidor arranca en puerto efimero (0) sobre el
 * MISMO H2 que el resto de las pruebas y se le pide con el HttpClient del
 * propio JDK. Cada prueba levanta y para su servidor, asi que no queda
 * ningun hilo abierto entre tests.
 */
class ApiPruebas extends BasePruebas {

    private ServidorApi servidor;
    private HttpClient cliente;
    private String base;

    @BeforeEach
    void arrancarServidor() throws Exception {
        servidor = new ServidorApi(0, catalogo, new TicketDao(db, catalogo),
                comentarios, new HistorialDao(db), new ReporteService(new ReporteDao(db)));
        servidor.arrancar();
        cliente = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + servidor.puerto();
    }

    @AfterEach
    void pararServidor() {
        servidor.apagar();
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    /* --- Salud y cabeceras -------------------------------------------- */

    @Test
    @DisplayName("La salud responde ok en JSON UTF-8 con CORS abierto")
    void saludRespondeOkConCors() throws Exception {
        HttpResponse<String> r = get("/api/salud");

        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("content-type").orElse("")
                        .contains("application/json"),
                "content-type: " + r.headers().firstValue("content-type"));
        assertTrue(r.headers().firstValue("content-type").orElse("")
                .contains("charset=utf-8"));
        assertEquals("*", r.headers().firstValue("access-control-allow-origin")
                .orElse(""), "CORS abierto en todas las respuestas");
        assertTrue(r.body().contains("\"estado\":\"ok\""));
        assertTrue(r.body().contains("\"version\":\"" + ServidorApi.VERSION + "\""));
    }

    @Test
    @DisplayName("El preflight OPTIONS responde 204 con las cabeceras CORS")
    void opcionesResponde204ConCors() throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(base + "/api/tickets"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> r = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, r.statusCode());
        assertEquals("*", r.headers().firstValue("access-control-allow-origin").orElse(""));
    }

    /* --- Listado y paginacion ------------------------------------------ */

    @Test
    @DisplayName("El listado pagina y expone totales coherentes con la semilla")
    void listadoPaginaYExponeTotales() throws Exception {
        HttpResponse<String> pagina1 = get("/api/tickets?pagina=1&tamano=5");
        assertEquals(200, pagina1.statusCode());
        assertTrue(pagina1.body().contains("\"pagina\":1"));
        assertTrue(pagina1.body().contains("\"tamano\":5"));
        assertEquals(5, ocurrencias(pagina1.body(), "\"titulo\":"),
                "tamano=5 debe devolver 5 tickets");
        int total = extraerEntero(pagina1.body(), "\"total\":");
        assertTrue(total >= 24, "la semilla crea 24+ tickets, total=" + total);
        assertTrue(extraerEntero(pagina1.body(), "\"totalPaginas\":") >= 5);

        HttpResponse<String> pagina2 = get("/api/tickets?pagina=2&tamano=5");
        assertEquals(200, pagina2.statusCode());
        assertNotEquals(primerId(pagina1.body()), primerId(pagina2.body()),
                "paginas distintas contienen tickets distintos");
    }

    @Test
    @DisplayName("El filtro por estado acepta nombre o id y la validacion responde 400")
    void filtroPorEstadoYParametrosInvalidos() throws Exception {
        HttpResponse<String> abiertos = get("/api/tickets?estado=Abierto&tamano=100");
        assertEquals(200, abiertos.statusCode());
        int tickets = ocurrencias(abiertos.body(), "\"estado\":{\"id\":");
        assertTrue(tickets >= 1, "hay tickets abiertos en la semilla");
        assertEquals(tickets, ocurrencias(abiertos.body(), "\"nombre\":\"Abierto\""),
                "todos los del listado estan en estado Abierto");

        HttpResponse<String> porId = get("/api/tickets?estado=1&tamano=100");
        assertEquals(200, porId.statusCode());
        assertEquals(ocurrencias(abiertos.body(), "\"titulo\":"),
                ocurrencias(porId.body(), "\"titulo\":"),
                "estado=1 y estado=Abierto devuelven lo mismo");

        assertEquals(400, get("/api/tickets?estado=Inventado").statusCode(),
                "estado inexistente -> 400");
        assertEquals(400, get("/api/tickets?pagina=0").statusCode(),
                "pagina fuera de rango -> 400");
        assertEquals(400, get("/api/tickets?pagina=abc").statusCode(),
                "pagina no numerica -> 400");
    }

    /* --- Detalle y errores --------------------------------------------- */

    @Test
    @DisplayName("El detalle trae comentarios e historial; el inexistente responde 404")
    void detalleIncluyeComentariosEHistorial() throws Exception {
        Ticket creado = ticketNuevo(marta());
        tickets.comentar(marta(), creado.id(), "Primer comentario", false);

        HttpResponse<String> r = get("/api/tickets/" + creado.id());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"titulo\":\"Titulo de prueba\""));
        assertTrue(r.body().contains("\"comentarios\":["));
        assertTrue(r.body().contains("\"historial\":["));
        assertTrue(r.body().contains("\"Primer comentario\""));
        assertTrue(r.body().contains("\"campo\":\"estado\""),
                "la trazabilidad inicial del alta esta en el historial");

        HttpResponse<String> noExiste = get("/api/tickets/999999");
        assertEquals(404, noExiste.statusCode());
        assertTrue(noExiste.body().contains("\"error\""));
    }

    @Test
    @DisplayName("Rutas desconocidas y metodos no GET devuelven error JSON")
    void rutasDesconocidasYMetodosNoSoportados() throws Exception {
        HttpResponse<String> ruta = get("/api/nada");
        assertEquals(404, ruta.statusCode());
        assertTrue(ruta.body().contains("\"error\""));

        HttpRequest post = HttpRequest.newBuilder(URI.create(base + "/api/tickets"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> metodo = cliente.send(post, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, metodo.statusCode());
        assertTrue(metodo.body().contains("\"error\""));
    }

    /* --- Catalogo e informes -------------------------------------------- */

    @Test
    @DisplayName("El catalogo expone estados, prioridades, categorias y transiciones")
    void catalogoExponeLaMatrizCompleta() throws Exception {
        HttpResponse<String> r = get("/api/catalogo");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"estados\":["));
        assertTrue(r.body().contains("\"prioridades\":["));
        assertTrue(r.body().contains("\"categorias\":["));
        assertTrue(r.body().contains("\"transiciones\":["));
        assertEquals(5, ocurrencias(r.body(), "\"esFinal\":"),
                "los 5 estados del ciclo de vida");
        assertEquals(4, ocurrencias(r.body(), "\"slaHoras\":"),
                "las 4 prioridades con su SLA");
        assertEquals(10, ocurrencias(r.body(), "\"origen\":"),
                "la matriz de transiciones legales tiene 10 filas");
    }

    @Test
    @DisplayName("Los KPIs devuelven abiertos, SLA, resolucion por tecnico y estados")
    void informesDevuelvenKpis() throws Exception {
        HttpResponse<String> r = get("/api/informes/kpis");
        assertEquals(200, r.statusCode());
        assertTrue(extraerEntero(r.body(), "\"ticketsAbiertos\":") > 0,
                "la semilla deja tickets sin resolver");
        assertTrue(r.body().contains("\"colaTrabajo\":["));
        assertTrue(r.body().contains("\"slaIncumplidos\":["));
        assertTrue(r.body().contains("\"resolucionPorTecnico\":["));
        assertTrue(r.body().contains("\"ticketsPorEstado\":["));
        assertTrue(ocurrencias(r.body(), "\"orden\":") >= 1,
                "vw_tickets_por_estado devuelve filas por estado");
    }

    /* --- Escapado de JSON ------------------------------------------------- */

    @Test
    @DisplayName("Comillas, barras invertidas y acentos se serializan escapados")
    void comillasYAcentosSeEscapanEnElJson() throws Exception {
        Ticket creado = ticketNuevo(marta());
        tickets.comentar(marta(), creado.id(),
                "Dijo \"no funciona\" tras reinstalar en C:\\temp · niño, ¿vale?", false);

        HttpResponse<String> r = get("/api/tickets/" + creado.id());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\\\"no funciona\\\""),
                "las comillas dobles del texto van escapadas");
        assertTrue(r.body().contains("C:\\\\temp"),
                "las barras invertidas van escapadas");
        assertTrue(r.body().contains("niño, ¿vale?"),
                "acentos y eñe viajan en UTF-8 sin romper nada");
        assertFalse(r.body().contains("\n"), "el JSON no contiene saltos crudos");
    }

    /* --- Auxiliares -------------------------------------------------------- */

    private static int ocurrencias(String texto, String busqueda) {
        int total = 0;
        int i = 0;
        while ((i = texto.indexOf(busqueda, i)) >= 0) {
            total++;
            i += busqueda.length();
        }
        return total;
    }

    private static int extraerEntero(String texto, String clave) {
        int inicio = texto.indexOf(clave);
        assertTrue(inicio >= 0, "falta la clave " + clave);
        int desde = inicio + clave.length();
        int hasta = desde;
        while (hasta < texto.length() && Character.isDigit(texto.charAt(hasta))) {
            hasta++;
        }
        return Integer.parseInt(texto.substring(desde, hasta));
    }

    private static String primerId(String cuerpo) {
        int inicio = cuerpo.indexOf("\"id\":");
        assertTrue(inicio >= 0, "el listado no contiene ningun ticket");
        int desde = inicio + "\"id\":".length();
        int hasta = desde;
        while (hasta < cuerpo.length() && Character.isDigit(cuerpo.charAt(hasta))) {
            hasta++;
        }
        return cuerpo.substring(desde, hasta);
    }
}
