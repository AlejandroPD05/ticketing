package com.royaltown.helpdesk.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.royaltown.helpdesk.dao.CategoriaDao;
import com.royaltown.helpdesk.dao.ComentarioDao;
import com.royaltown.helpdesk.dao.HistorialDao;
import com.royaltown.helpdesk.dao.TicketDao;
import com.royaltown.helpdesk.dao.UsuarioDao;
import com.royaltown.helpdesk.dao.ValoracionDao;
import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Comentario;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.HistorialCambio;
import com.royaltown.helpdesk.model.Prioridad;
import com.royaltown.helpdesk.model.Sesion;
import com.royaltown.helpdesk.model.Ticket;
import com.royaltown.helpdesk.model.Valoracion;
import com.royaltown.helpdesk.util.ConexionDB;
import com.royaltown.helpdesk.util.Transaccion;

/**
 * Capa de servicios del ticket: reglas de negocio, permisos y transacciones.
 *
 * Reglas codificadas aqui:
 *   * El ciclo de vida solo avanza por transiciones permitidas en
 *     `Transiciones_Estado` (validadas contra el catalogo, no con la BD).
 *   * Solo soporte (tecnico/admin) asigna, cambia estado o prioridad; el
 *     empleado creador unica excepcion: confirmar el cierre (Resuelto ->
 *     Cerrado) de su propio ticket.
 *   * Cada mutacion escribe su linea en `Historial_Ticket` dentro de la MISMA
 *     transaccion: o se aplican ambas o no se aplica ninguna.
 *   * fecha_cierre se fija al llegar a Resuelto y se borra al reabrir,
 *     de modo que el KPI de resolucion mide tiempo hasta solucion real.
 *   * fecha_limite se (re)calcula siempre como creacion + sla_horas.
 */
public class TicketService {

    private final ConexionDB db;
    private final CatalogoService catalogo;
    private final TicketDao tickets;
    private final UsuarioDao usuarios;
    private final ComentarioDao comentarios;
    private final HistorialDao historial;
    private final ValoracionDao valoraciones;
    private final CategoriaDao categorias;

    public TicketService(ConexionDB db, CatalogoService catalogo, TicketDao tickets,
                         UsuarioDao usuarios, ComentarioDao comentarios,
                         HistorialDao historial, ValoracionDao valoraciones,
                         CategoriaDao categorias) {
        this.db = db;
        this.catalogo = catalogo;
        this.tickets = tickets;
        this.usuarios = usuarios;
        this.comentarios = comentarios;
        this.historial = historial;
        this.valoraciones = valoraciones;
        this.categorias = categorias;
    }

    /* ------------------------------------------------------------------ */
    /* Lectura                                                             */
    /* ------------------------------------------------------------------ */

    public Optional<Ticket> buscar(int idTicket) {
        return tickets.porId(idTicket);
    }

    public Ticket obligatorio(int idTicket) {
        return tickets.porId(idTicket)
                .orElseThrow(() -> new HelpdeskException("No existe el ticket #" + idTicket));
    }

    /** Tickets visibles para la sesion: soporte ve todo, el empleado lo suyo. */
    public List<Ticket> visiblesPara(Sesion sesion) {
        if (sesion.esSoporte()) {
            return tickets.listar(TicketDao.Filtro.todo());
        }
        return tickets.listar(TicketDao.Filtro.todo().deCreador(sesion.idUsuario()));
    }

    public List<Ticket> creadosPor(int idUsuario) {
        return tickets.listar(TicketDao.Filtro.todo().deCreador(idUsuario));
    }

    public List<Ticket> asignadosA(int idTecnico) {
        return tickets.listar(TicketDao.Filtro.todo().deTecnico(idTecnico));
    }

    public List<Ticket> sinResolver() {
        return tickets.listar(TicketDao.Filtro.todo().sinResolver());
    }

    public void exigirVisible(Sesion sesion, Ticket ticket) {
        boolean propietario = ticket.usuarioCreador() == sesion.idUsuario();
        if (!sesion.esSoporte() && !propietario) {
            throw new HelpdeskException("No tienes acceso a este ticket.");
        }
    }

    public List<Comentario> hiloDe(Sesion sesion, int idTicket) {
        Ticket ticket = obligatorio(idTicket);
        exigirVisible(sesion, ticket);
        List<Comentario> hilo = comentarios.porTicket(idTicket);
        // Las notas internas solo las ve soporte.
        if (sesion.esSoporte()) {
            return hilo;
        }
        return hilo.stream().filter(c -> !c.esInterno()).toList();
    }

    public List<HistorialCambio> historialDe(Sesion sesion, int idTicket) {
        Ticket ticket = obligatorio(idTicket);
        exigirVisible(sesion, ticket);
        return historial.porTicket(idTicket);
    }

    public Optional<Valoracion> valoracionDe(int idTicket) {
        return valoraciones.porTicket(idTicket);
    }

    /* ------------------------------------------------------------------ */
    /* Ciclo de vida                                                        */
    /* ------------------------------------------------------------------ */

    public Ticket crear(Sesion sesion, String titulo, String descripcion,
                        int idCategoria, int idPrioridad) {
        if (titulo == null || titulo.isBlank()) {
            throw new HelpdeskException("El titulo no puede estar vacio.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new HelpdeskException("Describe la incidencia antes de enviarla.");
        }

        Catalogo cat = catalogo.catalogo();
        Categoria categoria = cat.categoria(idCategoria)
                .orElseThrow(() -> new HelpdeskException("Categoria inexistente."));
        Prioridad prioridad = cat.prioridadObligatoria(idPrioridad);
        Estado inicial = cat.estadoInicial();

        LocalDateTime ahora = LocalDateTime.now();
        Ticket datos = new Ticket(0, titulo.trim(), descripcion.trim(), inicial, prioridad,
                ahora, ahora.plus(prioridad.sla()), null, categoria,
                sesion.idUsuario(), null);

        return Transaccion.ejecutar(db, conn -> {
            int id = tickets.insertar(conn, datos);
            historial.registrar(conn, id, sesion.idUsuario(), "estado",
                    null, inicial.nombre());
            // Se devuelve la copia construida: releer con otra conexion dentro
            // de la transaccion no veria todavia lo no confirmado.
            return new Ticket(id, datos.titulo(), datos.descripcion(), inicial, prioridad,
                    datos.fechaCreacion(), datos.fechaLimite(), null, categoria,
                    sesion.idUsuario(), null);
        });
    }

    public Ticket cambiarEstado(Sesion sesion, int idTicket, int idEstadoDestino) {
        Ticket ticket = obligatorio(idTicket);
        exigirVisible(sesion, ticket);
        Catalogo cat = catalogo.catalogo();

        Estado origen = ticket.estado();
        Estado destino = cat.estadoObligatorio(idEstadoDestino);

        if (origen.id() == destino.id()) {
            throw new HelpdeskException("El ticket ya esta en ese estado.");
        }
        if (!cat.transicionPermitida(origen.id(), destino.id())) {
            throw new HelpdeskException(
                    "Transicion no permitida: " + origen.nombre() + " -> " + destino.nombre()
                            + ". Destinos legales: "
                            + String.join(", ", cat.destinosPosibles(origen.id())
                                    .stream().map(Estado::nombre).toList()));
        }

        boolean confirmaCierre = !sesion.esSoporte()
                && ticket.usuarioCreador() == sesion.idUsuario()
                && cat.estadoResuelto().map(e -> e.id() == origen.id()).orElse(false)
                && destino.esFinal();
        if (!sesion.esSoporte() && !confirmaCierre) {
            throw new HelpdeskException(
                    "Solo el equipo de soporte puede cambiar el estado de un ticket.");
        }

        LocalDateTime cierre = calcularCierre(ticket, destino);

        return Transaccion.ejecutar(db, conn -> {
            tickets.cambiarEstado(conn, idTicket, destino.id(), cierre);
            historial.registrar(conn, idTicket, sesion.idUsuario(), "estado",
                    origen.nombre(), destino.nombre());
            return ticket.conEstadoYFechaCierre(destino, cierre);
        });
    }

    /** Resuelto fija el cierre; una reapertura lo borra para medir de nuevo. */
    private LocalDateTime calcularCierre(Ticket ticket, Estado destino) {
        Optional<Estado> resuelto = catalogo.catalogo().estadoResuelto();
        boolean llegaResuelto = resuelto.map(e -> e.id() == destino.id()).orElse(false);
        boolean reabre = destino.orden() < ticket.estado().orden();

        if (llegaResuelto) {
            return LocalDateTime.now();
        }
        if (reabre) {
            return null;
        }
        return ticket.fechaCierre();
    }

    public Ticket cambiarPrioridad(Sesion sesion, int idTicket, int idPrioridad) {
        if (!sesion.esSoporte()) {
            throw new HelpdeskException("Solo el equipo de soporte puede repriorizar.");
        }
        Ticket ticket = obligatorio(idTicket);
        Prioridad nueva = catalogo.catalogo().prioridadObligatoria(idPrioridad);
        Prioridad anterior = ticket.prioridad();

        if (nueva.id() == anterior.id()) {
            throw new HelpdeskException("El ticket ya tiene esa prioridad.");
        }

        // El SLA se recalcula contra la fecha de creacion, como en la semilla.
        LocalDateTime limite = ticket.fechaCreacion().plus(nueva.sla());

        return Transaccion.ejecutar(db, conn -> {
            tickets.cambiarPrioridad(conn, idTicket, nueva.id(), limite);
            historial.registrar(conn, idTicket, sesion.idUsuario(), "prioridad",
                    anterior.nombre(), nueva.nombre());
            return ticket.conPrioridad(nueva, limite);
        });
    }

    /* ------------------------------------------------------------------ */
    /* Asignacion                                                           */
    /* ------------------------------------------------------------------ */

    /** El tecnico se asigna a si mismo; el administrador reparte a otros. */
    public Ticket asignar(Sesion sesion, int idTicket, Integer idTecnicoDestino) {
        if (!sesion.esSoporte()) {
            throw new HelpdeskException("Solo el equipo de soporte puede asignar tickets.");
        }
        Ticket ticket = obligatorio(idTicket);
        if (ticket.estado().esFinal()) {
            throw new HelpdeskException("El ticket ya esta cerrado.");
        }

        int destino = (sesion.esAdministrador() && idTecnicoDestino != null)
                ? idTecnicoDestino
                : sesion.idUsuario();
        if (ticket.tecnicoAsignado() != null && ticket.tecnicoAsignado() == destino) {
            throw new HelpdeskException("Ese ticket ya esta asignado.");
        }
        String nombreAnterior = nombreTecnico(ticket.tecnicoAsignado());
        String nombreNuevo = usuarios.porId(destino)
                .filter(candidato -> candidato.esSoporte())
                .map(u -> u.nombreCompleto())
                .orElseThrow(() -> new HelpdeskException("Ese usuario no es tecnico."));

        return Transaccion.ejecutar(db, conn -> {
            tickets.asignarTecnico(conn, idTicket, destino);
            historial.registrar(conn, idTicket, sesion.idUsuario(),
                    "tecnico_asignado", nombreAnterior, nombreNuevo);
            return ticket.conTecnico(destino);
        });
    }

    private String nombreTecnico(Integer idTecnico) {
        if (idTecnico == null) {
            return null;
        }
        return usuarios.porId(idTecnico).map(u -> u.nombreCompleto()).orElse(null);
    }

    /* ------------------------------------------------------------------ */
    /* Conversacion y valoracion                                             */
    /* ------------------------------------------------------------------ */

    public void comentar(Sesion sesion, int idTicket, String texto, boolean interno) {
        if (texto == null || texto.isBlank()) {
            throw new HelpdeskException("El comentario no puede estar vacio.");
        }
        if (interno && !sesion.esSoporte()) {
            throw new HelpdeskException("Las notas internas son solo para soporte.");
        }
        Ticket ticket = obligatorio(idTicket);
        exigirVisible(sesion, ticket);

        Transaccion.ejecutar(db, conn -> {
            comentarios.insertar(conn, idTicket, sesion.idUsuario(), texto.trim(), interno);
            return null;
        });
    }

    public void valorar(Sesion sesion, int idTicket, int puntuacion, String comentario) {
        Ticket ticket = obligatorio(idTicket);

        if (ticket.usuarioCreador() != sesion.idUsuario()) {
            throw new HelpdeskException("Solo quien creo el ticket puede valorarlo.");
        }
        if (!ticket.estado().esFinal()) {
            throw new HelpdeskException("El ticket aun no esta cerrado.");
        }
        if (puntuacion < 1 || puntuacion > 5) {
            throw new HelpdeskException("La puntuacion va de 1 a 5.");
        }
        if (valoraciones.porTicket(idTicket).isPresent()) {
            throw new HelpdeskException("Este ticket ya esta valorado.");
        }

        Transaccion.ejecutar(db, conn -> {
            valoraciones.insertar(conn, idTicket, puntuacion,
                    comentario == null || comentario.isBlank() ? null : comentario.trim());
            return null;
        });
    }

    /* ------------------------------------------------------------------ */
    /* Categorias (administrador)                                           */
    /* ------------------------------------------------------------------ */

    public Categoria crearCategoria(Sesion sesion, String nombre, String descripcion) {
        if (!sesion.esAdministrador()) {
            throw new HelpdeskException("Solo el administrador puede crear categorias.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new HelpdeskException("La categoria necesita nombre.");
        }

        Categoria creada = Transaccion.ejecutar(db, conn ->
                categorias.crear(conn, nombre.trim(),
                        descripcion == null ? null : descripcion.trim()));
        catalogo.recargar();  // la nueva categoria queda visible para todos
        return creada;
    }
}
