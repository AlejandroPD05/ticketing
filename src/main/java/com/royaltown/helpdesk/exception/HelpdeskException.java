package com.royaltown.helpdesk.exception;

/**
 * Excepcion de dominio del helpdesk: violaciones de reglas de negocio
 * (transiciones ilegales, permisos, datos invalidos).
 *
 * Es unchecked a proposito: la capa de servicios la lanza y la CLI la
 * captura en su nivel de presentacion sin tener que firmar cada llamada.
 */
public class HelpdeskException extends RuntimeException {

    public HelpdeskException(String mensaje) {
        super(mensaje);
    }

    public HelpdeskException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
