package ar.edu.odontogestion.domain.token;

import java.time.Instant;

/**
 * Resultado de verificar (sin consumir) un token vigente.
 *
 * @param referencia recurso al que apunta
 * @param venceEn    instante de vencimiento (UTC)
 */
public record TokenVigente(ReferenciaToken referencia, Instant venceEn) {
}
