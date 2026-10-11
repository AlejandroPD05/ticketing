package com.royaltown.helpdesk.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fotografia en memoria de las tablas lookup (estados, prioridades,
 * categorias) y de la matriz de transiciones permitidas.
 *
 * Se carga una sola vez al arrancar la CLI: las lookup cambian muy poco y
 * tenerlas en memoria evita un JOIN por cada fila de ticket y permite validar
 * transiciones sin salir a base de datos.
 */
public final class Catalogo {

    private final List<Estado> estados;
    private final List<Prioridad> prioridades;
    private final List<Categoria> categorias;
    private final Set<Transicion> transiciones;

    private final Map<Integer, Estado> estadosPorId = new HashMap<>();
    private final Map<Integer, Prioridad> prioridadesPorId = new HashMap<>();
    private final Map<Integer, Categoria> categoriasPorId = new HashMap<>();
    private final Map<String, Estado> estadosPorNombre = new HashMap<>();
    private final Map<String, Prioridad> prioridadesPorNombre = new HashMap<>();

    public Catalogo(List<Estado> estados, List<Prioridad> prioridades,
                    List<Categoria> categorias, Collection<Transicion> transiciones) {
        this.estados = List.copyOf(estados);
        this.prioridades = List.copyOf(prioridades);
        this.categorias = List.copyOf(categorias);
        this.transiciones = Set.copyOf(transiciones);

        for (Estado estado : this.estados) {
            estadosPorId.put(estado.id(), estado);
            estadosPorNombre.put(estado.nombre().toLowerCase(), estado);
        }
        for (Prioridad prioridad : this.prioridades) {
            prioridadesPorId.put(prioridad.id(), prioridad);
            prioridadesPorNombre.put(prioridad.nombre().toLowerCase(), prioridad);
        }
        for (Categoria categoria : this.categorias) {
            categoriasPorId.put(categoria.id(), categoria);
        }
    }

    /** Par de estados que codifica una fila de Transiciones_Estado. */
    public record Transicion(int origen, int destino) {
    }

    public List<Estado> estados() {
        return estados;
    }

    public List<Prioridad> prioridades() {
        return prioridades;
    }

    public List<Categoria> categorias() {
        return categorias;
    }

    public Optional<Estado> estado(int id) {
        return Optional.ofNullable(estadosPorId.get(id));
    }

    public Estado estadoObligatorio(int id) {
        return Optional.ofNullable(estadosPorId.get(id))
                .orElseThrow(() -> new IllegalArgumentException("Estado desconocido: " + id));
    }

    public Optional<Estado> estadoPorNombre(String nombre) {
        return Optional.ofNullable(estadosPorNombre.get(nombre.toLowerCase()));
    }

    public Estado estadoPorNombreObligatorio(String nombre) {
        return estadoPorNombre(nombre)
                .orElseThrow(() -> new IllegalArgumentException("Estado desconocido: " + nombre));
    }

    public Optional<Prioridad> prioridad(int id) {
        return Optional.ofNullable(prioridadesPorId.get(id));
    }

    public Prioridad prioridadObligatoria(int id) {
        return Optional.ofNullable(prioridadesPorId.get(id))
                .orElseThrow(() -> new IllegalArgumentException("Prioridad desconocida: " + id));
    }

    public Prioridad prioridadPorNombreObligatoria(String nombre) {
        Prioridad prioridad = prioridadesPorNombre.get(nombre.toLowerCase());
        if (prioridad == null) {
            throw new IllegalArgumentException("Prioridad desconocida: " + nombre);
        }
        return prioridad;
    }

    public Optional<Categoria> categoria(int id) {
        return Optional.ofNullable(categoriasPorId.get(id));
    }

    public boolean transicionPermitida(int origen, int destino) {
        return transiciones.contains(new Transicion(origen, destino));
    }

    /** Destinos legales desde un estado dado (para pintar el menu de la CLI). */
    public List<Estado> destinosPosibles(int origen) {
        return transiciones.stream()
                .filter(t -> t.origen() == origen)
                .map(t -> estadosPorId.get(t.destino()))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Estado::orden))
                .toList();
    }

    /** El estado inicial del ciclo de vida (Abierto). */
    public Estado estadoInicial() {
        return estados.stream()
                .min(java.util.Comparator.comparingInt(Estado::orden))
                .orElseThrow(() -> new IllegalStateException("No hay estados cargados"));
    }

    /** El estado final del ciclo de vida (Cerrado). */
    public Estado estadoCerrado() {
        return estados.stream()
                .filter(Estado::esFinal)
                .max(java.util.Comparator.comparingInt(Estado::orden))
                .orElseThrow(() -> new IllegalStateException("No hay estado final"));
    }

    public Optional<Estado> estadoResuelto() {
        return estados.stream()
                .filter(e -> e.nombre().equalsIgnoreCase("Resuelto"))
                .findFirst();
    }

    /** Añade fuera del constructor las categorias nuevas creadas en caliente. */
    public Catalogo conCategoria(Categoria categoria) {
        List<Categoria> ampliadas = new java.util.ArrayList<>(categorias);
        ampliadas.add(categoria);
        return new Catalogo(estados, prioridades, ampliadas, transiciones);
    }

    public Set<Transicion> transiciones() {
        return new HashSet<>(transiciones);
    }
}
