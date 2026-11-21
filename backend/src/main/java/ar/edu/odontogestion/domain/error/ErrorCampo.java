package ar.edu.odontogestion.domain.error;

/** Error asociado a un campo de la solicitud; se serializa como {@code {field, message}} (igual que el 400). */
public record ErrorCampo(String field, String message) {
}
