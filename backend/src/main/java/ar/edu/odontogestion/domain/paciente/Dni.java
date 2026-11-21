package ar.edu.odontogestion.domain.paciente;

import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.util.regex.Pattern;

/**
 * Normalización del DNI (design D3): quita puntos, espacios y guiones y exige 7 u 8 dígitos. Se aplica al guardar
 * y al buscar; la restricción {@code CHECK} de V5 queda como garantía. El error nunca incluye el valor recibido.
 */
public final class Dni {

    private static final Pattern SEPARADORES = Pattern.compile("[.\\s-]");
    private static final Pattern VALIDO = Pattern.compile("^[0-9]{7,8}$");
    static final String MENSAJE = "Debe tener 7 u 8 dígitos.";

    private Dni() {
    }

    public static String normalizar(String dni) {
        String limpio = dni == null ? "" : SEPARADORES.matcher(dni).replaceAll("");
        if (!VALIDO.matcher(limpio).matches()) {
            throw new ValidacionDominioException("dni", MENSAJE);
        }
        return limpio;
    }
}
