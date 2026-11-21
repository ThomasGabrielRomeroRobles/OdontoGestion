package ar.edu.odontogestion.application.perfil;

/** Vista de aplicación del perfil del odontólogo (sin hash ni entidad JPA). */
public record PerfilVista(String email, String nombre, String apellido, String matricula) {
}
