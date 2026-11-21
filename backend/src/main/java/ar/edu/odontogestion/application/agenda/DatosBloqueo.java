package ar.edu.odontogestion.application.agenda;

/**
 * Bloqueo tal como llega de la API: instantes como texto ISO-8601 con zona, para responder 422 en el campo si vienen
 * mal formados (design D11). {@code motivo} puede ser {@code null}: se guarda {@code ""} (D12).
 */
public record DatosBloqueo(String desde, String hasta, String motivo) {
}
