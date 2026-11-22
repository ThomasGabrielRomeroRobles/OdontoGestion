package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import org.junit.jupiter.api.Test;

/**
 * Contrato común de todo {@link NotificationChannel} (KB 12, design D10): un destinatario válido devuelve
 * {@code Enviado}; un destino rechazado devuelve {@code Fallido} con motivo, sin lanzar excepciones.
 * Cada canal lo hereda y provee cómo fabricarse y cómo provocar el rechazo.
 */
public abstract class NotificationChannelContractTest {

    protected static final String DESTINATARIO = "paciente@ejemplo.test";

    /** Canal bajo prueba, listo para aceptar envíos. */
    protected abstract NotificationChannel canal();

    /** Deja al canal en un estado en el que el siguiente envío a {@code destinatario} es rechazado. */
    protected abstract void provocarRechazo(String destinatario);

    protected static NotificacionSaliente saliente(String destinatario) {
        return new NotificacionSaliente(TipoNotificacion.CONFIRMACION_TURNO, destinatario,
                "Tu turno en Consultorio Ficticio está confirmado", "Cuerpo ficticio del mensaje.\n");
    }

    @Test
    void destinatarioValidoDevuelveEnviado() {
        assertThat(canal().enviar(saliente(DESTINATARIO))).isInstanceOf(ResultadoEnvio.Enviado.class);
    }

    @Test
    void destinoRechazadoDevuelveFallidoConMotivoSinExcepcion() {
        NotificationChannel canal = canal();
        provocarRechazo(DESTINATARIO);

        assertThatCode(() -> {
            ResultadoEnvio resultado = canal.enviar(saliente(DESTINATARIO));
            assertThat(resultado).isInstanceOf(ResultadoEnvio.Fallido.class);
            assertThat(((ResultadoEnvio.Fallido) resultado).motivo()).isNotBlank();
        }).doesNotThrowAnyException();
    }

    @Test
    void declaraSuCanal() {
        assertThat(canal().canal()).isNotNull();
    }
}
