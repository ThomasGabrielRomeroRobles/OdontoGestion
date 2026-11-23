package ar.edu.odontogestion.api.publico;

/** Prestación activa tal como la ve el público: sin datos internos. */
public record PrestacionPublicaResponse(Long id, String nombre, int duracionMin) {
}
