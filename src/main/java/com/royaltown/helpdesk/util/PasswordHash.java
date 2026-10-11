package com.royaltown.helpdesk.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Hashing de contrasenas con bcrypt.
 *
 * La semilla de schema.sql contiene hashes bcrypt $2a$10$ generados con la
 * libreria bcrypt de Python, compatibles con esta implementacion: el formato
 * es interoperable, no depende del lenguaje.
 */
public final class PasswordHash {

    /** Coste por defecto: 2^10 iteraciones, equilibrio usuial entre seguridad y latencia. */
    public static final int COSTE = 10;

    private PasswordHash() {
    }

    public static String hash(String contrasena) {
        return BCrypt.withDefaults().hashToString(COSTE, contrasena.toCharArray());
    }

    public static boolean verifica(String contrasena, String hashGuardado) {
        if (contrasena == null || hashGuardado == null) {
            return false;
        }
        return BCrypt.verifyer().verify(contrasena.toCharArray(), hashGuardado).verified;
    }
}
