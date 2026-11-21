package ar.edu.odontogestion.application.auth;

import org.springframework.security.core.Authentication;

/**
 * Resultado de un login exitoso: datos del usuario tal como están guardados y la autenticación para la sesión
 * HTTP.
 */
public record UsuarioAutenticado(String email, String nombre, String apellido, Authentication authentication) {
}
