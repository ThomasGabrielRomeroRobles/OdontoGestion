package ar.edu.odontogestion.domain.token;

import java.time.Instant;

/**
 * Resultado de emitir un token: el valor en claro vive solo aquí (para armar el enlace del email).
 *
 * @param valor      valor en claro (43 caracteres base64url); nunca se persiste ni se registra
 * @param proposito  propósito único del token
 * @param referencia recurso al que apunta
 * @param venceEn    instante de vencimiento (UTC)
 */
public record TokenEmitido(String valor, PropositoToken proposito, ReferenciaToken referencia, Instant venceEn) {

    @Override
    public String toString() {
        return "TokenEmitido[valor=***, proposito=" + proposito + ", referencia=" + referencia
                + ", venceEn=" + venceEn + "]";
    }
}
