package ar.edu.odontogestion.domain.usuario;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Política única de contraseñas (design D8): al menos 8 caracteres y como máximo 72 bytes UTF-8 (límite de
 * BCrypt: lo que exceda se ignoraría en silencio). Sin reglas de composición. Los mensajes nunca incluyen el valor.
 */
public final class PoliticaPassword {

    public static final int MIN_CARACTERES = 8;
    public static final int MAX_BYTES = 72;

    private PoliticaPassword() {
    }

    /** Devuelve el motivo del rechazo, o vacío si la contraseña cumple la política. */
    public static Optional<String> violacion(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Es obligatoria.");
        }
        if (password.length() < MIN_CARACTERES) {
            return Optional.of("Debe tener al menos " + MIN_CARACTERES + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Es demasiado larga (máximo " + MAX_BYTES + " bytes).");
        }
        return Optional.empty();
    }

    public static void exigir(String password) {
        violacion(password).ifPresent(motivo -> {
            throw new ReglaNegocioException("Contraseña inválida: " + motivo);
        });
    }
}
