package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.token.PropositoToken;
import java.time.Instant;
import java.util.Objects;

/**
 * Enlace ya armado para un envío. La URL contiene el token en claro: vive solo en memoria durante el envío y
 * nunca se persiste ni se loguea ({@link #toString()} la oculta).
 */
public record EnlaceRenderizado(PropositoToken proposito, String url, Instant venceEn) {

    public EnlaceRenderizado {
        Objects.requireNonNull(proposito, "proposito");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(venceEn, "venceEn");
    }

    @Override
    public String toString() {
        return "EnlaceRenderizado[proposito=" + proposito + ", url=<oculta>, venceEn=" + venceEn + "]";
    }
}
