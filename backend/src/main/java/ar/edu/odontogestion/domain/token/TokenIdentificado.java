package ar.edu.odontogestion.domain.token;

import java.time.Instant;

/**
 * Resultado de identificar (sin consumir) un token vigente entre varios propósitos admitidos.
 *
 * @param proposito  para qué sirve el token
 * @param referencia recurso al que apunta
 * @param venceEn    instante de vencimiento (UTC)
 */
public record TokenIdentificado(PropositoToken proposito, ReferenciaToken referencia, Instant venceEn) {
}
