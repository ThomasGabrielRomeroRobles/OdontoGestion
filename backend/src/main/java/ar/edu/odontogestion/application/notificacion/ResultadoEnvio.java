package ar.edu.odontogestion.application.notificacion;

import java.util.Objects;

/** Resultado de un envío por un canal: el canal nunca lanza excepciones al procesamiento (design D10). */
public sealed interface ResultadoEnvio {

    ResultadoEnvio ENVIADO = new Enviado();

    static ResultadoEnvio enviado() {
        return ENVIADO;
    }

    static ResultadoEnvio fallido(String motivo) {
        return new Fallido(motivo);
    }

    record Enviado() implements ResultadoEnvio {
    }

    /** @param motivo descripción técnica del fallo; sin el cuerpo del mensaje ni tokens */
    record Fallido(String motivo) implements ResultadoEnvio {

        public Fallido {
            Objects.requireNonNull(motivo, "motivo");
            if (motivo.isBlank()) {
                motivo = "Fallo de envío sin detalle";
            }
        }
    }
}
