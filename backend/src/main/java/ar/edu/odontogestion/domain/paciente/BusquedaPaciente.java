package ar.edu.odontogestion.domain.paciente;

import java.util.Locale;

/**
 * Parámetros {@code LIKE} de la búsqueda por texto (design D7). Siempre se envían como parámetros (nunca
 * concatenados) y con los comodines del usuario escapados con {@code \}.
 *
 * @param patron     "contiene" en minúsculas, para apellido, nombre y email
 * @param prefijoDni dígitos de la búsqueda + {@code %}; vacío si no hay dígitos (no coincide con ningún DNI)
 */
public record BusquedaPaciente(String patron, String prefijoDni) {

    public static BusquedaPaciente de(String q) {
        String texto = q == null ? "" : q.trim();
        String patron = "%" + escapar(texto.toLowerCase(Locale.ROOT)) + "%";
        String digitos = texto.replaceAll("[^0-9]", "");
        String prefijo = digitos.isEmpty() ? "" : digitos + "%";
        return new BusquedaPaciente(patron, prefijo);
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
