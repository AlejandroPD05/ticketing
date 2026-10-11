package com.royaltown.helpdesk.service;

import java.util.List;

import com.royaltown.helpdesk.dao.ReporteDao;
import com.royaltown.helpdesk.model.informe.FilaEstado;
import com.royaltown.helpdesk.model.informe.FilaSla;
import com.royaltown.helpdesk.model.informe.FilaTecnico;
import com.royaltown.helpdesk.model.informe.TicketAbierto;

/**
 * Informes de gestion. Son una fachada fina sobre las vistas de la base de
 * datos: si mañana el KPI cambia, se reescribe la vista y esta clase sigue
 * igual.
 */
public class ReporteService {

    private final ReporteDao reportes;

    public ReporteService(ReporteDao reportes) {
        this.reportes = reportes;
    }

    /** Cola activa: tickets todavia sin resolver, mas urgente primero. */
    public List<TicketAbierto> cola() {
        return reportes.cola();
    }

    /** Incumplimientos de SLA: sin resolver y fuera de plazo. */
    public List<FilaSla> slaIncumplidos() {
        return reportes.slaIncumplidos();
    }

    /** Tiempo medio de resolucion por tecnico. */
    public List<FilaTecnico> resolucionPorTecnico() {
        return reportes.resolucionPorTecnico();
    }

    /** Reparto de tickets por estado del ciclo de vida. */
    public List<FilaEstado> ticketsPorEstado() {
        return reportes.ticketsPorEstado();
    }
}
