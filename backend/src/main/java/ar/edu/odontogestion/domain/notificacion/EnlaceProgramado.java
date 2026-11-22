package ar.edu.odontogestion.domain.notificacion;

import ar.edu.odontogestion.domain.token.PropositoToken;
import java.time.Instant;
import java.util.Objects;

/**
 * Enlace que llevará la notificación: solo propósito y vencimiento. El token se emite recién al enviar
 * (design D8, RN-TK-02), así que el outbox nunca guarda su valor.
 */
public record EnlaceProgramado(PropositoToken proposito, Instant venceEn) {

    public EnlaceProgramado {
        Objects.requireNonNull(proposito, "proposito");
        Objects.requireNonNull(venceEn, "venceEn");
    }
}
