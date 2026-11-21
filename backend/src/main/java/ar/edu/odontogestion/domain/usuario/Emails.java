package ar.edu.odontogestion.domain.usuario;

import java.util.Locale;

/**
 * Normalización de emails (design D2): {@code trim} + minúsculas. Se aplica al guardar y al buscar porque
 * el driver envía los parámetros como {@code varchar}; {@code citext UNIQUE} queda como garantía de integridad.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalizar(String email) {
        if (email == null) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
