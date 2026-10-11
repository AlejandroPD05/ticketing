package com.royaltown.helpdesk.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.royaltown.helpdesk.dao.CatalogoDao;
import com.royaltown.helpdesk.dao.CategoriaDao;
import com.royaltown.helpdesk.dao.ComentarioDao;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.ReporteDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.dao.UsuarioDao;
import com.royaltown.helpdesk.dao.ValoracionDao;
import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Comentario;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.HistorialCambio;
import com.royaltown.helpdesk.model.Prioridad;
import com.royaltown.helpdesk.model.Sesion;
import com.royaltown.helpdesk.model.Ticket;
import com.royaltown.helpdesk.model.Valoracion;
import com.royaltown.helpdesk.service.AuthService;
import com.royaltown.helpdesk.service.CatalogoService;
import com.royaltown.helpdesk.service.ReporteService;
import com.royaltown.helpdesk.service.TicketService;
import com.royaltown.helpdesk.util.ConexionDB;

/**
 * CLI de demostracion: el punto de entrada que ejercita todo el sistema
 * (login por rol, ciclo de vida, trazabilidad e informes).
 *
 * Arquitectura de la llamada: CLI -> servicios -> DAOs -> JDBC. La CLI no
 * contiene reglas de negocio ni SQL: solo pide datos y pinta resultados.
 */
public final class Main {

    /** Entrada de menu: codigo, etiqueta y accion. */
    private record Opcion(int codigo, String etiqueta, Runnable accion) {
    }

    private final ConexionDB db;
    private final CatalogoService catalogo;
    private final AuthService auth;
    private final TicketService tickets;
    private final ReporteService informes;
    private final UsuarioDao usuarios;
    private final CategoriaDao categorias;

    private Sesion sesion;

    public static void main(String[] args) {
        try {
            new Main().arrancar();
        } catch (HelpdeskException e) {
            Consola.error(e.getMessage());
        }
    }

    private Main() {
        db = ConexionDB.porDefecto();
        catalogo = new CatalogoService(new CatalogoDao(db));
        usuarios = new UsuarioDao(db);
        categorias = new CategoriaDao(catalogo::get);

        TicketDao ticketDao = new TicketDao(db, catalogo);
        ComentarioDao comentarioDao = new ComentarioDao(db);
        HistorialDao historialDao = new HistorialDao(db);
        ValoracionDao valoracionDao = new ValoracionDao(db);

        auth = new AuthService(usuarios);
        tickets = new TicketService(db, catalogo, ticketDao, usuarios,
                comentarioDao, historialDao, valoracionDao, categorias);
        informes = new ReporteService(new ReporteDao(db));
    }

    private void arrancar() {
        Consola.cabecera("HELPDESK ROYALTOWN · Java + JDBC");
        System.out.println("  Réplica de sistema de helpdesk con ciclo de vida trazable.");

        boolean seguir = true;
        while (seguir) {
            Optional<Sesion> entrada = login();
            if (entrada.isEmpty()) {
                seguir = false;
                break;
            }
            sesion = entrada.get();
            bucleDeSesion();
        }
        Consola.cabecera("Hasta pronto");
    }

    /* ------------------------------------------------------------------ */
    /* Autenticacion                                                        */
    /* ------------------------------------------------------------------ */

    private Optional<Sesion> login() {
        Consola.cabecera("Inicio de sesion");
        String email = Consola.linea("  Email ('salir' para terminar): ");
        if (email.equalsIgnoreCase("salir")) {
            return Optional.empty();
        }
        String contrasena = Consola.linea("  Contrasena: ");
        try {
            Sesion nueva = auth.iniciarSesion(email, contrasena);
            Consola.ok("Bienvenido/a " + nueva.usuario().nombreCompleto()
                    + " · " + nueva.usuario().rol().nombre());
            return Optional.of(nueva);
        } catch (HelpdeskException e) {
            Consola.error(e.getMessage());
            return login();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Bucle de sesion                                                      */
    /* ------------------------------------------------------------------ */

    private void bucleDeSesion() {
        while (sesion != null) {
            List<Opcion> menu = construirMenu();
            Consola.cabecera("Sesion: " + sesion.usuario().nombreCompleto()
                    + " · " + sesion.usuario().rol().nombre());

            for (Opcion opcion : menu) {
                System.out.println("  " + opcion.codigo() + ") " + opcion.etiqueta());
            }

            int elegido = Consola.entero("  Opcion: ", 0,
                    menu.stream().mapToInt(Opcion::codigo).max().orElse(0));
            if (elegido == 0) {
                sesion = null;
                continue;
            }
            menu.stream()
                    .filter(o -> o.codigo() == elegido)
                    .findFirst()
                    .ifPresent(o -> ejecutar(o.accion()));
        }
    }

    /** Aisla cada accion: una regla de negocio violada no tira la sesion. */
    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (HelpdeskException e) {
            Consola.error(e.getMessage());
        } catch (RuntimeException e) {
            Consola.error("Fallo inesperado: " + e.getMessage());
        }
    }

    private List<Opcion> construirMenu() {
        List<Opcion> menu = new ArrayList<>();
        boolean soporte = sesion.esSoporte();

        if (soporte) {
            menu.add(new Opcion(1, "Cola de trabajo (sin resolver)", this::verCola));
            menu.add(new Opcion(2, "Mis tickets asignados", this::verAsignados));
            menu.add(new Opcion(3, "Tickets que yo cree", this::verCreados));
            menu.add(new Opcion(4, "Crear ticket", this::crearTicket));
            menu.add(new Opcion(5, "Abrir ticket (detalle, hilo e historial)", this::abrirTicket));
            menu.add(new Opcion(6, "Asignarme un ticket", this::asignarse));
            menu.add(new Opcion(7, "Cambiar estado de un ticket", this::cambiarEstado));
            menu.add(new Opcion(8, "Cambiar prioridad de un ticket", this::cambiarPrioridad));
            menu.add(new Opcion(9, "Comentar en un ticket", this::comentar));
            menu.add(new Opcion(10, "Informes de gestion", this::verInformes));
            if (sesion.esAdministrador()) {
                menu.add(new Opcion(11, "Asignar ticket a un tecnico", this::asignarATecnico));
                menu.add(new Opcion(12, "Crear categoria", this::crearCategoria));
            }
        } else {
            menu.add(new Opcion(1, "Mis tickets", this::verCreados));
            menu.add(new Opcion(2, "Crear ticket", this::crearTicket));
            menu.add(new Opcion(3, "Abrir ticket (detalle, hilo e historial)", this::abrirTicket));
            menu.add(new Opcion(4, "Comentar en un ticket", this::comentar));
            menu.add(new Opcion(5, "Confirmar cierre de mi ticket", this::cambiarEstado));
            menu.add(new Opcion(6, "Valorar un ticket cerrado", this::valorar));
        }

        menu.add(new Opcion(0, "Cerrar sesion", () -> sesion = null));
        return menu;
    }

    /* ------------------------------------------------------------------ */
    /* Listados                                                             */
    /* ------------------------------------------------------------------ */

    private void verCola() {
        Consola.cabecera("Cola de trabajo");
        var cola = informes.cola();
        List<String[]> filas = cola.stream()
                .map(t -> new String[]{
                        String.valueOf(t.idTicket()),
                        corto(t.titulo(), 46),
                        t.prioridad(),
                        t.estado(),
                        t.tecnico(),
                        Consola.fecha(t.fechaLimite())})
                .toList();
        Consola.imprimirTabla(
                new String[]{"#", "Titulo", "Prioridad", "Estado", "Tecnico", "Limite SLA"}, filas);
        Consola.pausa();
    }

    private void verAsignados() {
        Consola.cabecera("Tickets asignados a mi");
        pintarTickets(tickets.asignadosA(sesion.idUsuario()));
        Consola.pausa();
    }

    private void verCreados() {
        Consola.cabecera("Tickets creados por mi");
        pintarTickets(tickets.creadosPor(sesion.idUsuario()));
        Consola.pausa();
    }

    private void pintarTickets(List<Ticket> lista) {
        if (lista.isEmpty()) {
            Consola.vacio("todavia no hay tickets");
            return;
        }
        List<String[]> filas = lista.stream()
                .map(t -> new String[]{
                        String.valueOf(t.id()),
                        corto(t.titulo(), 46),
                        t.categoria().nombre(),
                        t.prioridad().nombre(),
                        t.estado().nombre(),
                        Consola.fecha(t.fechaCreacion())})
                .toList();
        Consola.imprimirTabla(
                new String[]{"#", "Titulo", "Categoria", "Prioridad", "Estado", "Creado"}, filas);
    }

    /* ------------------------------------------------------------------ */
    /* Operaciones sobre tickets                                            */
    /* ------------------------------------------------------------------ */

    private void crearTicket() {
        Consola.cabecera("Nuevo ticket");

        List<Categoria> cats = catalogo.catalogo().categorias();
        for (int i = 0; i < cats.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + cats.get(i).nombre());
        }
        int categoria = cats.get(Consola.entero("  Categoria: ", 1, cats.size()) - 1).id();

        List<Prioridad> prios = catalogo.catalogo().prioridades();
        for (int i = 0; i < prios.size(); i++) {
            Prioridad p = prios.get(i);
            System.out.println("  " + (i + 1) + ") " + p.nombre()
                    + " (SLA " + p.slaHoras() + "h)");
        }
        int prioridad = prios.get(Consola.entero("  Prioridad: ", 1, prios.size()) - 1).id();

        String titulo = Consola.linea("  Titulo: ");
        String descripcion = Consola.linea("  Descripcion: ");

        Ticket creado = tickets.crear(sesion, titulo, descripcion, categoria, prioridad);
        Consola.ok("Ticket #" + creado.id() + " creado · limite "
                + Consola.fecha(creado.fechaLimite()));
        Consola.pausa();
    }

    private void abrirTicket() {
        Consola.cabecera("Detalle de ticket");
        int id = Consola.enteroObligatorio("  Ticket #: ");
        Ticket t = tickets.obligatorio(id);
        tickets.exigirVisible(sesion, t);

        System.out.println("\n  #" + t.id() + "  " + t.titulo());
        System.out.println("  Estado:    " + t.estado().nombre());
        System.out.println("  Prioridad: " + t.prioridad().nombre()
                + " (SLA " + t.prioridad().slaHoras() + "h)");
        System.out.println("  Categoria: " + t.categoria().nombre());
        System.out.println("  Creado:    " + Consola.fecha(t.fechaCreacion())
                + "   Limite: " + Consola.fecha(t.fechaLimite())
                + "   Cierre: " + Consola.fecha(t.fechaCierre()));
        System.out.println("  Tecnico:   " + (t.tecnicoAsignado() == null
                ? "Sin asignar"
                : usuarios.porId(t.tecnicoAsignado()).map(u -> u.nombreCompleto()).orElse("?")));
        System.out.println("\n  " + t.descripcion());

        Optional<Valoracion> valoracion = tickets.valoracionDe(id);
        valoracion.ifPresent(v -> System.out.println(
                "\n  Valoracion: " + v.puntuacion() + "/5 - "
                        + (v.comentario() == null ? "sin comentario" : v.comentario())));

        Consola.subtitulo("Hilo de comentarios");
        List<Comentario> hilo = tickets.hiloDe(sesion, id);
        if (hilo.isEmpty()) {
            Consola.vacio("sin comentarios");
        }
        for (Comentario c : hilo) {
            System.out.println("  [" + Consola.fecha(c.fecha()) + "] "
                    + c.autorNombre() + (c.esInterno() ? "  (nota interna)" : ""));
            System.out.println("      " + c.texto());
        }

        Consola.subtitulo("Historial de cambios");
        List<HistorialCambio> historial = tickets.historialDe(sesion, id);
        if (historial.isEmpty()) {
            Consola.vacio("sin historial");
        }
        for (HistorialCambio h : historial) {
            System.out.println("  [" + Consola.fecha(h.fecha()) + "] " + h.autorNombre()
                    + " · " + h.campo() + ": "
                    + (h.valorAnterior() == null ? "(nuevo)" : h.valorAnterior())
                    + " -> " + h.valorNuevo());
        }

        Consola.pausa();
    }

    private void comentar() {
        Consola.cabecera("Comentar en ticket");
        int id = Consola.enteroObligatorio("  Ticket #: ");
        String texto = Consola.linea("  Comentario: ");

        boolean interno = false;
        if (sesion.esSoporte()) {
            String respuesta = Consola.linea("  ¿Nota interna (solo soporte)? (s/N): ");
            interno = respuesta.equalsIgnoreCase("s") || respuesta.equalsIgnoreCase("si");
        }
        tickets.comentar(sesion, id, texto, interno);
        Consola.ok("Comentario publicado.");
        Consola.pausa();
    }

    private void asignarse() {
        Consola.cabecera("Asignarme un ticket");
        int id = elegirDeLaCola("  Ticket a asignarme (#, 0 cancelar): ");
        if (id == 0) {
            return;
        }
        Ticket asignado = tickets.asignar(sesion, id, null);
        Consola.ok("Ticket #" + asignado.id() + " asignado a ti.");
        Consola.pausa();
    }

    private void asignarATecnico() {
        Consola.cabecera("Asignar ticket a un tecnico");
        int id = elegirDeLaCola("  Ticket (#, 0 cancelar): ");
        if (id == 0) {
            return;
        }
        List<com.royaltown.helpdesk.model.Usuario> tecnicos = usuarios.tecnicos();
        for (int i = 0; i < tecnicos.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + tecnicos.get(i).nombreCompleto()
                    + " · " + tecnicos.get(i).departamento());
        }
        int opcion = Consola.entero("  Tecnico: ", 1, tecnicos.size());
        Ticket asignado = tickets.asignar(sesion, id, tecnicos.get(opcion - 1).id());
        Consola.ok("Ticket #" + asignado.id() + " asignado a "
                + tecnicos.get(opcion - 1).nombreCompleto() + ".");
        Consola.pausa();
    }

    private void cambiarEstado() {
        Consola.cabecera("Cambiar estado");
        int id = Consola.enteroObligatorio("  Ticket #: ");
        Ticket t = tickets.obligatorio(id);
        tickets.exigirVisible(sesion, t);

        System.out.println("\n  #" + t.id() + " " + t.titulo()
                + "\n  Estado actual: " + t.estado().nombre());

        List<Estado> destinos = catalogo.catalogo().destinosPosibles(t.estado().id());
        if (destinos.isEmpty()) {
            Consola.aviso("No hay transiciones salientes desde este estado.");
            Consola.pausa();
            return;
        }
        for (int i = 0; i < destinos.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + destinos.get(i).nombre());
        }
        Estado elegido = destinos.get(
                Consola.entero("  Nuevo estado: ", 1, destinos.size()) - 1);

        Ticket actualizado = tickets.cambiarEstado(sesion, id, elegido.id());
        Consola.ok("Estado actualizado: " + actualizado.estado().nombre()
                + (actualizado.fechaCierre() != null
                ? " · cierre registrado " + Consola.fecha(actualizado.fechaCierre())
                : ""));
        Consola.pausa();
    }

    private void cambiarPrioridad() {
        Consola.cabecera("Cambiar prioridad");
        int id = Consola.enteroObligatorio("  Ticket #: ");
        Ticket t = tickets.obligatorio(id);

        System.out.println("\n  #" + t.id() + " " + t.titulo()
                + "\n  Prioridad actual: " + t.prioridad().nombre());

        List<Prioridad> prioridades = catalogo.catalogo().prioridades();
        for (int i = 0; i < prioridades.size(); i++) {
            Prioridad p = prioridades.get(i);
            System.out.println("  " + (i + 1) + ") " + p.nombre()
                    + " (SLA " + p.slaHoras() + "h)");
        }
        Prioridad opcion = prioridades.get(
                Consola.entero("  Nueva prioridad: ", 1, prioridades.size()) - 1);

        Ticket actualizado = tickets.cambiarPrioridad(sesion, id, opcion.id());
        Consola.ok("Prioridad " + actualizado.prioridad().nombre()
                + " · nuevo limite " + Consola.fecha(actualizado.fechaLimite()));
        Consola.pausa();
    }

    private void valorar() {
        Consola.cabecera("Valorar ticket cerrado");
        int id = Consola.enteroObligatorio("  Ticket #: ");
        int puntuacion = Consola.entero("  Puntuacion (1-5): ", 1, 5);
        String comentario = Consola.linea("  Comentario (opcional): ");
        tickets.valorar(sesion, id, puntuacion, comentario);
        Consola.ok("Gracias por tu valoracion.");
        Consola.pausa();
    }

    private void crearCategoria() {
        Consola.cabecera("Nueva categoria");
        String nombre = Consola.linea("  Nombre: ");
        String descripcion = Consola.linea("  Descripcion: ");
        Categoria creada = tickets.crearCategoria(sesion, nombre, descripcion);
        Consola.ok("Categoria #" + creada.id() + " '" + creada.nombre() + "' creada.");
        Consola.pausa();
    }

    /* ------------------------------------------------------------------ */
    /* Informes                                                             */
    /* ------------------------------------------------------------------ */

    private void verInformes() {
        Consola.cabecera("Informes de gestion");

        Consola.subtitulo("Tickets por estado");
        List<String[]> estados = informes.ticketsPorEstado().stream()
                .map(f -> new String[]{f.estado(), String.valueOf(f.total())})
                .toList();
        Consola.imprimirTabla(new String[]{"Estado", "Tickets"}, estados);

        Consola.subtitulo("SLA incumplidos (sin resolver y fuera de plazo)");
        List<String[]> sla = informes.slaIncumplidos().stream()
                .map(f -> new String[]{
                        String.valueOf(f.idTicket()),
                        corto(f.titulo(), 40),
                        f.prioridad(),
                        Consola.fecha(f.fechaLimite()),
                        f.tecnico()})
                .toList();
        Consola.imprimirTabla(
                new String[]{"#", "Titulo", "Prioridad", "Limite", "Tecnico"}, sla);

        Consola.subtitulo("Resolucion media por tecnico");
        List<String[]> tecnicos = informes.resolucionPorTecnico().stream()
                .map(f -> new String[]{
                        f.tecnico(),
                        String.valueOf(f.ticketsResueltos()),
                        f.minutosMedios() == null ? "-" : f.minutosMedios() + " min"})
                .toList();
        Consola.imprimirTabla(
                new String[]{"Tecnico", "Resueltos", "Tiempo medio"}, tecnicos);

        Consola.pausa();
    }

    /* ------------------------------------------------------------------ */
    /* Auxiliares                                                           */
    /* ------------------------------------------------------------------ */

    /** Muestra la cola y devuelve el id elegido (0 = cancelar). */
    private int elegirDeLaCola(String prompt) {
        List<Ticket> cola = tickets.sinResolver();
        if (cola.isEmpty()) {
            Consola.vacio("no hay tickets sin resolver");
            return 0;
        }
        List<String[]> filas = cola.stream()
                .map(t -> new String[]{
                        String.valueOf(t.id()),
                        corto(t.titulo(), 46),
                        t.prioridad().nombre(),
                        t.estado().nombre()})
                .toList();
        Consola.imprimirTabla(new String[]{"#", "Titulo", "Prioridad", "Estado"}, filas);

        int id = Consola.enteroLibre(prompt);
        if (id == 0) {
            return 0;
        }
        boolean existe = cola.stream().anyMatch(t -> t.id() == id);
        if (!existe) {
            throw new HelpdeskException("El ticket #" + id + " no esta en la cola.");
        }
        return id;
    }

    private static String corto(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
