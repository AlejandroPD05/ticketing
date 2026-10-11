package com.royaltown.helpdesk.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.royaltown.helpdesk.dao.UsuarioDao;
import com.royaltown.helpdesk.exception.HelpdeskException;
import com.royaltown.helpdesk.model.Sesion;
import com.royaltown.helpdesk.model.Usuario;
import com.royaltown.helpdesk.util.PasswordHash;

/**
 * Autenticacion.
 *
 * El mensaje de error es el mismo para "no existe" y "contrasena incorrecta"
 * a proposito: no se revela si un email esta dado de alta en el sistema.
 */
public class AuthService {

    private final UsuarioDao usuarios;

    public AuthService(UsuarioDao usuarios) {
        this.usuarios = usuarios;
    }

    public Sesion iniciarSesion(String email, String contrasena) {
        if (email == null || email.isBlank() || contrasena == null || contrasena.isBlank()) {
            throw new HelpdeskException("Introduce email y contrasena.");
        }

        Optional<Usuario> buscado = usuarios.porEmail(email.trim());
        if (buscado.isEmpty() || !PasswordHash.verifica(contrasena, buscado.get().passwordHash())) {
            throw new HelpdeskException("Credenciales no validas.");
        }

        Usuario usuario = buscado.get();
        if (!usuario.activo()) {
            throw new HelpdeskException(
                    "La cuenta esta desactivada; contacta con el administrador.");
        }

        return new Sesion(usuario, LocalDateTime.now());
    }
}
