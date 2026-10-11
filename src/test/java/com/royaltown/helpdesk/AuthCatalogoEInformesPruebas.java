package com.royaltown.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Catalogo;
import com.royaltown.helpdesk.model.Estado;
import com.royaltown.helpdesk.model.informe.FilaEstado;
import com.royaltown.helpdesk.model.informe.TicketAbierto;

/** Autenticacion, catalogo de transiciones e informes (vistas). */
class AuthCatalogoEInformesPruebas extends BasePruebas {

    /* --- Autenticacion --------------------------------------------------- */

    @Test
    @DisplayName("Login correcto con las credenciales de la semilla")
    void loginCorrecto() {
        var sesion = auth.iniciarSesion("marta.sanchez@royaltown.local", "royaltown2026");
        assertEquals("Marta Sanchez Lopez", sesion.usuario().nombreCompleto());
        assertFalse(sesion.esSoporte());

        var soporte = auth.iniciarSesion("laura.gomez@royaltown.local", "royaltown2026");
        assertTrue(soporte.esSoporte());
    }

    @Test
    @DisplayName("Contrasena o email erroneos: mismo mensaje, sin filtrar quien existe")
    void loginErroneoUnicoMensaje() {
        HelpdeskException malaPass = assertThrows(HelpdeskException.class,
                () -> auth.iniciarSesion("marta.sanchez@royaltown.local", "otra"));
        HelpdeskException sinUsuario = assertThrows(HelpdeskException.class,
                () -> auth.iniciarSesion("nadie@royaltown.local", "royaltown2026"));

        assertEquals(malaPass.getMessage(), sinUsuario.getMessage());
        assertTrue(malaPass.getMessage().contains("Credenciales"));
    }

    /* --- Catalogo de transiciones ---------------------------------------- */

    @Test
    @DisplayName("La matriz de transiciones coincide con la tabla Transiciones_Estado")
    void matrizDeTransiciones() {
        Catalogo cat = catalogo.catalogo();

        assertTrue(cat.transicionPermitida(
                cat.estadoPorNombreObligatorio("Abierto").id(),
                cat.estadoPorNombreObligatorio("En_Progreso").id()));
        assertFalse(cat.transicionPermitida(
                cat.estadoPorNombreObligatorio("Abierto").id(),
                cat.estadoCerrado().id()));

        List<Estado> destinos =
                cat.destinosPosibles(cat.estadoPorNombreObligatorio("Abierto").id());
        assertEquals(2, destinos.size(), "Abierto solo puede ir a En_Progreso o Pendiente_Usuario");
    }

    /* --- Informes --------------------------------------------------------- */

    @Test
    @DisplayName("El informe por estado recoge todos los tickets de la semilla")
    void informePorEstado() {
        List<FilaEstado> filas = informes.ticketsPorEstado();
        assertEquals(5, filas.size());
        long total = filas.stream().mapToLong(FilaEstado::total).sum();
        assertTrue(total >= 48, "la semilla carga 48 tickets");
    }

    @Test
    @DisplayName("La cola de trabajo solo contiene tickets sin resolver")
    void colaSinResolver() {
        List<TicketAbierto> cola = informes.cola();

        assertFalse(cola.isEmpty());
        assertTrue(cola.stream().allMatch(
                t -> !"Resuelto".equals(t.estado()) && !"Cerrado".equals(t.estado())));
        // la mas urgente primero
        for (int i = 1; i < cola.size(); i++) {
            assertFalse(cola.get(i - 1).fechaLimite().isAfter(cola.get(i).fechaLimite()),
                    "la cola debe ir ordenada por fecha_limite");
        }
    }

    @Test
    @DisplayName("El informe por tecnico solo cuenta tickets con tecnico y cierre")
    void informePorTecnico() {
        var filas = informes.resolucionPorTecnico();
        assertFalse(filas.isEmpty());
        assertTrue(filas.stream().allMatch(f -> f.ticketsResueltos() > 0));
    }
}
