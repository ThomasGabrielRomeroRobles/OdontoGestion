package ar.edu.odontogestion.application.listaespera;

import java.time.Instant;

/**
 * Lo que muestra el enlace de un aviso (C-10 D5): el hueco ofrecido y si todavía puede aceptarse. Nunca datos del
 * paciente ni ids.
 */
public record ResumenAvisoResponse(String consultorio, String prestacion, Instant inicio, Instant fin,
        Instant venceEn, boolean disponible) {
}
