package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import java.util.Objects;

/**
 * Mensaje neutro al canal (KB 12): tipo, destinatario, asunto y cuerpo ya renderizados. El cuerpo puede contener
 * enlaces con token, por eso {@link #toString()} lo oculta.
 */
public record NotificacionSaliente(TipoNotificacion tipo, String destinatario, String asunto, String cuerpo) {

    public NotificacionSaliente {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(asunto, "asunto");
        Objects.requireNonNull(cuerpo, "cuerpo");
    }

    @Override
    public String toString() {
        return "NotificacionSaliente[tipo=" + tipo + ", asunto=" + asunto + ", cuerpo=<oculto>]";
    }
}
