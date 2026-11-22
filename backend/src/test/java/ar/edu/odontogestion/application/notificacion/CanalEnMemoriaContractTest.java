package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El canal falso de los tests cumple el mismo contrato que el de email. */
class CanalEnMemoriaContractTest extends NotificationChannelContractTest {

    private CanalEnMemoria canal;

    @BeforeEach
    void crear() {
        canal = new CanalEnMemoria();
    }

    @Override
    protected NotificationChannel canal() {
        return canal;
    }

    @Override
    protected void provocarRechazo(String destinatario) {
        canal.rechazar(destinatario);
    }

    @Test
    void registraLosEnviosAceptadosYNoLosRechazados() {
        canal.enviar(saliente("a@ejemplo.test"));
        canal.rechazar("b@ejemplo.test");
        canal.enviar(saliente("b@ejemplo.test"));

        assertThat(canal.enviados()).extracting(NotificacionSaliente::destinatario).containsExactly("a@ejemplo.test");
    }

    @Test
    void puedeFallarSiempreYLuegoReiniciarse() {
        canal.fallarSiempre();
        assertThat(canal.enviar(saliente("a@ejemplo.test"))).isInstanceOf(ResultadoEnvio.Fallido.class);

        canal.reiniciar();
        assertThat(canal.enviar(saliente("a@ejemplo.test"))).isInstanceOf(ResultadoEnvio.Enviado.class);
        assertThat(canal.enviados()).hasSize(1);
    }
}
