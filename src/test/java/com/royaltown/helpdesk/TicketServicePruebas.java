package com.royaltown.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Categoria;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.HistorialCambio;
import com.royaltown.helpdesk.model.Sesion;
import com.royaltown.helpdesk.model.Ticket;

/** Reglas de negocio del ciclo de vida del ticket. */
class TicketServicePruebas extends BasePruebas {

    private Estado estado(String nombre) {
        return catalogo.catalogo().estadoPorNombreObligatorio(nombre);
    }

    private int idEstado(String nombre) {
        return estado(nombre).id();
    }

    /* --- Creacion ------------------------------------------------------ */

    @Test
    @DisplayName("Al crear un ticket se fija SLA y se registra la trazabilidad inicial")
    void crearTicketFijaLimiteYHistorial() {
        Ticket creado = ticketNuevo(marta());

        assertTrue(creado.id() > 0);
        assertEquals("Abierto", creado.estado().nombre());
        assertEquals(creado.fechaCreacion().plusHours(24), creado.fechaLimite(),
                "Media = SLA de 24h desde la creacion");

        List<HistorialCambio> historial = tickets.historialDe(marta(), creado.id());
        assertEquals(1, historial.size());
        assertEquals("estado", historial.get(0).campo());
        assertNull(historial.get(0).valorAnterior());
        assertEquals("Abierto", historial.get(0).valorNuevo());
        assertEquals(marta().idUsuario(), historial.get(0).idUsuario());
    }

    @Test
    @DisplayName("Un ticket sin titulo o descripcion no llega a la base de datos")
    void crearTicketValidaEntrada() {
        assertThrows(HelpdeskException.class,
                () -> tickets.crear(marta(), "  ", "desc", 1, 2));
        assertThrows(HelpdeskException.class,
                () -> tickets.crear(marta(), "titulo", null, 1, 2));
        assertThrows(HelpdeskException.class,
                () -> tickets.crear(marta(), "titulo", "desc", 999, 2));
    }

    /* --- Transiciones -------------------------------------------------- */

    @Test
    @DisplayName("La transicion Abierto -> Cerrado esta prohibida por la matriz")
    void transicionDirectaIlegalRechazada() {
        Ticket creado = ticketNuevo(marta());

        HelpdeskException e = assertThrows(HelpdeskException.class,
                () -> tickets.cambiarEstado(laura(), creado.id(),
                        catalogo.catalogo().estadoCerrado().id()));
        assertTrue(e.getMessage().contains("Transicion no permitida"));
    }

    @Test
    @DisplayName("El tecnico avanza el ciclo y cada salto queda en el historial")
    void tecnicoAvanzaCicloConHistorial() {
        Ticket t = ticketNuevo(marta());

        t = tickets.cambiarEstado(laura(), t.id(), idEstado("En_Progreso"));
        t = tickets.cambiarEstado(laura(), t.id(), idEstado("Resuelto"));

        assertEquals("Resuelto", t.estado().nombre());
        assertNotNull(t.fechaCierre(), "resolver fija la fecha de cierre");

        List<HistorialCambio> historial = tickets.historialDe(marta(), t.id());
        assertEquals(3, historial.size());
        assertEquals("Abierto -> En_Progreso",
                historial.get(1).valorAnterior() + " -> " + historial.get(1).valorNuevo());
        assertEquals("En_Progreso -> Resuelto",
                historial.get(2).valorAnterior() + " -> " + historial.get(2).valorNuevo());
    }

    @Test
    @DisplayName("El empleado no puede transicionar salvo para confirmar el cierre")
    void empleadoSoloConfirmaCierre() {
        Ticket t = ticketNuevo(marta());

        HelpdeskException e = assertThrows(HelpdeskException.class,
                () -> tickets.cambiarEstado(marta(), t.id(), idEstado("En_Progreso")));
        assertTrue(e.getMessage().contains("soporte"));

        // el ciclo avanza hasta Resuelto y Marta (creadora) puede cerrarlo
        tickets.cambiarEstado(laura(), t.id(), idEstado("En_Progreso"));
        tickets.cambiarEstado(laura(), t.id(), idEstado("Resuelto"));
        Ticket cerrado = tickets.cambiarEstado(marta(), t.id(),
                catalogo.catalogo().estadoCerrado().id());

        assertEquals("Cerrado", cerrado.estado().nombre());
        assertTrue(cerrado.estado().esFinal());
    }

    @Test
    @DisplayName("Reabrir un ticket borra su fecha de cierre (vuelve a medirse)")
    void reaperturaBorraFechaCierre() {
        Ticket t = ticketNuevo(marta());

        t = tickets.cambiarEstado(laura(), t.id(), idEstado("En_Progreso"));
        t = tickets.cambiarEstado(laura(), t.id(), idEstado("Resuelto"));
        t = tickets.cambiarEstado(laura(), t.id(), catalogo.catalogo().estadoCerrado().id());
        assertNotNull(t.fechaCierre());

        t = tickets.cambiarEstado(laura(), t.id(), idEstado("En_Progreso"));
        assertNull(t.fechaCierre(), "una reapertura limpia el cierre anterior");
        assertFalse(t.estado().esFinal());
    }

    /* --- Asignacion ---------------------------------------------------- */

    @Test
    @DisplayName("Un tecnico se asigna a si mismo; repetir es un error")
    void tecnicoSeAsignaYNoRepite() {
        Ticket t = ticketNuevo(marta());

        Ticket asignado = tickets.asignar(laura(), t.id(), null);
        assertEquals(laura().idUsuario(), asignado.tecnicoAsignado());

        assertThrows(HelpdeskException.class, () -> tickets.asignar(laura(), t.id(), null));

        List<HistorialCambio> historial = tickets.historialDe(marta(), t.id());
        assertTrue(historial.stream().anyMatch(
                h -> "tecnico_asignado".equals(h.campo())
                        && "Laura Gomez Martin".equals(h.valorNuevo())));
    }

    @Test
    @DisplayName("El administrador reparte tickets entre tecnicos; un empleado no")
    void administradorReparteYEmpleadoNo() {
        Ticket t = ticketNuevo(marta());

        HelpdeskException e = assertThrows(HelpdeskException.class,
                () -> tickets.asignar(marta(), t.id(), null));
        assertTrue(e.getMessage().contains("soporte"));

        Ticket asignado = tickets.asignar(admin(), t.id(), 4);
        assertEquals(4, asignado.tecnicoAsignado());
    }

    /* --- Prioridad y SLA ------------------------------------------------ */

    @Test
    @DisplayName("Cambiar la prioridad recalcula fecha_limite y lo registra")
    void cambiarPrioridadRecalculaSla() {
        Ticket t = ticketNuevo(marta());  // Media, 24h
        int critica = catalogo.catalogo().prioridadPorNombreObligatoria("Critica").id();

        Ticket actualizado = tickets.cambiarPrioridad(laura(), t.id(), critica);

        assertEquals("Critica", actualizado.prioridad().nombre());
        // H2 (como MySQL) guarda TIMESTAMP con precision de microsegundos y
        // redondea, mientras que fechaCreacion() en memoria lleva nanosegundos:
        // se compara con tolerancia de 1 microsegundo para que el test no sea
        // flaky (fallaba ~50 % de las corridas por el redondeo).
        long dif = java.time.Duration.between(
                t.fechaCreacion().plusHours(4).truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                actualizado.fechaLimite()).abs().toNanos();
        assertTrue(dif <= 1_000,
                "el SLA se recalcula contra la fecha de creacion (desviacion " + dif + " ns)");

        List<HistorialCambio> historial = tickets.historialDe(marta(), t.id());
        assertTrue(historial.stream().anyMatch(
                h -> "prioridad".equals(h.campo())
                        && "Media".equals(h.valorAnterior())
                        && "Critica".equals(h.valorNuevo())));
    }

    @Test
    @DisplayName("Un empleado no puede repriorizar")
    void empleadoNoReprioriza() {
        Ticket t = ticketNuevo(marta());
        assertThrows(HelpdeskException.class,
                () -> tickets.cambiarPrioridad(marta(), t.id(), 3));
    }

    /* --- Comentarios y visibilidad --------------------------------------- */

    @Test
    @DisplayName("La nota interna no llega al empleado que creo el ticket")
    void notaInternaOcultaAlEmpleado() {
        Ticket t = ticketNuevo(marta());
        tickets.comentar(laura(), t.id(), "Nota interna: causa conocida", true);

        assertTrue(tickets.hiloDe(laura(), t.id()).stream().anyMatch(c -> c.esInterno()),
                "soporte la ve");
        assertTrue(tickets.hiloDe(marta(), t.id()).isEmpty(), "el empleado no");
    }

    @Test
    @DisplayName("Solo soporte puede marcar un comentario como interno")
    void internoSoloSoporte() {
        Ticket t = ticketNuevo(marta());
        assertThrows(HelpdeskException.class,
                () -> tickets.comentar(marta(), t.id(), "gol", true));
    }

    @Test
    @DisplayName("Un empleado no ajeno al ticket no puede verlo ni comentarlo")
    void visibilidadEntreEmpleados() {
        Ticket t = ticketNuevo(marta());
        Sesion javier = sesion(2);

        assertThrows(HelpdeskException.class, () -> tickets.hiloDe(javier, t.id()));
        assertThrows(HelpdeskException.class,
                () -> tickets.comentar(javier, t.id(), "hola", false));
    }

    /* --- Valoracion ------------------------------------------------------ */

    @Test
    @DisplayName("Solo el creador valora, solo una vez, y solo con el ticket cerrado")
    void valoracionConReglas() {
        Ticket t = ticketNuevo(marta());

        assertThrows(HelpdeskException.class,
                () -> tickets.valorar(marta(), t.id(), 5, "sin cerrar"));

        tickets.cambiarEstado(laura(), t.id(), idEstado("En_Progreso"));
        tickets.cambiarEstado(laura(), t.id(), idEstado("Resuelto"));

        assertThrows(HelpdeskException.class,
                () -> tickets.valorar(sesion(2), t.id(), 5, "no soy creador"));

        tickets.cambiarEstado(marta(), t.id(), catalogo.catalogo().estadoCerrado().id());

        assertThrows(HelpdeskException.class,
                () -> tickets.valorar(marta(), t.id(), 9, "fuera de rango"));

        tickets.valorar(marta(), t.id(), 4, "Rapido");
        assertThrows(HelpdeskException.class,
                () -> tickets.valorar(marta(), t.id(), 5, "otra vez"));

        assertEquals(4, tickets.valoracionDe(t.id()).orElseThrow().puntuacion());
    }

    /* --- Categorias ------------------------------------------------------ */

    @Test
    @DisplayName("El administrador crea categorias y quedan disponibles al instante")
    void administradorCreaCategoria() {
        Categoria creada = tickets.crearCategoria(admin(), "Telefonia", "Fijos y moviles");

        assertTrue(catalogo.catalogo().categorias().stream()
                .anyMatch(c -> c.id() == creada.id()));

        assertThrows(HelpdeskException.class,
                () -> tickets.crearCategoria(marta(), "Otra", "x"));
    }
}
