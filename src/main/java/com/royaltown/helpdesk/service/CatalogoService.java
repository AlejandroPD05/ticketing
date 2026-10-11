package com.royaltown.helpdesk.service;

import java.util.function.Supplier;

import com.royaltown.helpdesk.dao.CatalogoDao;
import com.royaltown.helpdesk.model.Catalogo;

/**
 * Mantiene el catalogo (estados, prioridades, categorias y transiciones)
 * cargado en memoria y lo recarga cuando cambia algo (p. ej. alta de una
 * categoria). El resto de servicios y los DAOs lo consumen como `Supplier`,
 * de modo que siempre ven la version mas reciente.
 */
public class CatalogoService implements Supplier<Catalogo> {

    private final CatalogoDao dao;
    private Catalogo actual;

    public CatalogoService(CatalogoDao dao) {
        this.dao = dao;
        recargar();
    }

    public final void recargar() {
        this.actual = dao.cargar();
    }

    public Catalogo catalogo() {
        return actual;
    }

    @Override
    public Catalogo get() {
        return actual;
    }
}
