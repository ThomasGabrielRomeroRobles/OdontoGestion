package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.application.notificacion.NotificationServiceEncolarIT.DATOS_TURNO;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Encolado idempotente (RN-NO-03, design D7). Ids ficticios >= 900000. */
@Import(FixedClockTestConfig.class)
class NotificationServiceIdempotenciaIT extends AbstractIntegrationTest {

    private static final List<EnlaceProgramado> ENLACES_RECORDATORIO = List.of(
            new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
            new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))));

    @Autowired
    private NotificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private int filas(String where) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE " + where, Integer.class);
    }

    private boolean recordatorio(long turno) {
        return service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(turno), DATOS_TURNO, ENLACES_RECORDATORIO);
    }

    @Test
    void recordatorioDuplicadoSeIgnoraYLaOtraTransaccionConfirmaSusDatos() {
        tx.executeWithoutResult(s -> recordatorio(900101));

        Boolean segunda = tx.execute(s -> {
            boolean insertada = recordatorio(900101);
            // La misma transacción sigue sana y escribe otros datos (otra notificación) que deben confirmarse.
            service.encolar(TipoNotificacion.CONFIRMACION_TURNO, "paciente@ejemplo.test",
                    new ReferenciaToken.Turno(900101), DATOS_TURNO, List.of());
            return insertada;
        });

        assertThat(segunda).isFalse();
        assertThat(filas("turno_id = 900101 AND tipo = 'RECORDATORIO'")).isEqualTo(1);
        assertThat(filas("turno_id = 900101 AND tipo = 'CONFIRMACION_TURNO'")).isEqualTo(1);
    }

    @Test
    void avisoHuecoDuplicadoGeneraUnaSolaFila() {
        Map<String, String> datos = DATOS_TURNO;
        List<EnlaceProgramado> enlaces =
                List.of(new EnlaceProgramado(PropositoToken.ACEPTAR_AVISO, AHORA.plus(Duration.ofHours(2))));

        Boolean primera = tx.execute(s -> service.encolar(TipoNotificacion.AVISO_HUECO, "paciente@ejemplo.test",
                new ReferenciaToken.Aviso(900007), datos, enlaces));
        Boolean segunda = tx.execute(s -> service.encolar(TipoNotificacion.AVISO_HUECO, "paciente@ejemplo.test",
                new ReferenciaToken.Aviso(900007), datos, enlaces));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
        assertThat(filas("aviso_id = 900007")).isEqualTo(1);
    }

    @Test
    void confirmacionDeTurnoSeRepitePorCadaMovimiento() {
        for (int i = 0; i < 2; i++) {
            Boolean insertada = tx.execute(s -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO,
                    "paciente@ejemplo.test", new ReferenciaToken.Turno(900102), DATOS_TURNO, List.of()));
            assertThat(insertada).isTrue();
        }

        assertThat(filas("turno_id = 900102 AND tipo = 'CONFIRMACION_TURNO'")).isEqualTo(2);
    }

    @Test
    void confirmarListaEsperaDuplicadaDeLaMismaEntradaGeneraUnaSolaFila() {
        Map<String, String> datos = Map.of("consultorio", "Consultorio Ficticio Sonrisa",
                "prestacion", "Limpieza ficticia");
        List<EnlaceProgramado> enlaces = List.of(
                new EnlaceProgramado(PropositoToken.CONFIRMAR_LISTA_ESPERA, AHORA.plus(Duration.ofMinutes(30))));

        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.CONFIRMAR_LISTA_ESPERA,
                "paciente@ejemplo.test", new ReferenciaToken.EntradaListaEspera(900030), datos, enlaces));
        Boolean segunda = tx.execute(s -> service.encolar(TipoNotificacion.CONFIRMAR_LISTA_ESPERA,
                "paciente@ejemplo.test", new ReferenciaToken.EntradaListaEspera(900030), datos, enlaces));

        assertThat(segunda).isFalse();
        assertThat(filas("entrada_lista_espera_id = 900030")).isEqualTo(1);
    }
}
