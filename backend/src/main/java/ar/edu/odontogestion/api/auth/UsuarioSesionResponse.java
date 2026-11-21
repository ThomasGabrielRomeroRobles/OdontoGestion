package ar.edu.odontogestion.api.auth;

/** Datos del usuario de la sesión (login y {@code /auth/me}). */
public record UsuarioSesionResponse(String email, String nombre, String apellido) {
}
