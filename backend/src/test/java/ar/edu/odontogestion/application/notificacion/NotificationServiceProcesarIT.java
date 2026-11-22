package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.support.TurnosFicticios;
import static ar.edu.odontogestion.application.notificacion.NotificationServiceEncolarIT.DATOS_TURNO;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Procesamiento del outbox con reintentos (design D10). Ids ficticios >= 900000 y datos ficticios. */
@Import(FixedClockTestConfig.class)
@TurnosFicticios
class NotificationServiceProcesarIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private CanalEnMemoria canal;

    @Autowired
    private MutableClock reloj;

    @AfterEach
    void limpiar() {
        reloj.set(AHORA);
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private void encolarConfirmacion(long turno, String destinatario) {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO, destinatario,
                new ReferenciaToken.Turno(turno), DATOS_TURNO, List.of()));
    }

    private Map<String, Object> fila(long turno) {
        return jdbc.queryForMap("SELECT estado, intentos, error, programada_para, enviada_en "
                + "FROM notificacion WHERE turno_id = ?", turno);
    }

    private static Instant instante(Object timestamptz) {
        return ((Timestamp) timestamptz).toInstant();
    }

    @Test
    void envioExitosoDejaLaNotificacionEnviada() {
        encolarConfirmacion(900201, "paciente@ejemplo.test");

        service.procesarPendientes();

        Map<String, Object> fila = fila(900201);
        assertThat(fila).containsEntry("estado", "ENVIADA").containsEntry("intentos", 1);
        assertThat(fila.get("error")).isNull();
        assertThat(instante(fila.get("enviada_en"))).isEqualTo(AHORA);
        assertThat(canal.enviados()).singleElement().satisfies(n -> {
            assertThat(n.destinatario()).isEqualTo("paciente@ejemplo.test");
            assertThat(n.tipo()).isEqualTo(TipoNotificacion.CONFIRMACION_TURNO);
            assertThat(n.asunto()).contains("Consultorio Ficticio Sonrisa");
            assertThat(n.cuerpo()).contains("10:00");
        });
    }

    @Test
    void falloYReintentoConEsperaCrecienteHastaQuedarFallida() {
        encolarConfirmacion(900202, "paciente@ejemplo.test");
        canal.fallarSiempre();

        service.procesarPendientes();
        Map<String, Object> primera = fila(900202);
        assertThat(primera).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
        Duration primeraEspera = Duration.between(AHORA, instante(primera.get("programada_para")));
        assertThat(primeraEspera).isEqualTo(Duration.ofMinutes(1));
        assertThat((String) primera.get("error")).isNotBlank();

        reloj.advance(primeraEspera);
        Instant segundoIntento = reloj.instant();
        service.procesarPendientes();
        Map<String, Object> segunda = fila(900202);
        assertThat(segunda).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 2);
        Duration segundaEspera = Duration.between(segundoIntento, instante(segunda.get("programada_para")));
        assertThat(segundaEspera).isGreaterThan(primeraEspera);

        reloj.advance(segundaEspera);
        service.procesarPendientes();
        Map<String, Object> tercera = fila(900202);
        assertThat(tercera).containsEntry("estado", "FALLIDA").containsEntry("intentos", 3)
                .containsEntry("error", "Canal en memoria configurado para fallar");
        assertThat(tercera.get("enviada_en")).isNull();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    void noSeReprocesaAntesDeTiempo() {
        encolarConfirmacion(900203, "paciente@ejemplo.test");
        canal.fallarSiempre();
        service.procesarPendientes();
        canal.reiniciar();

        reloj.advance(Duration.ofSeconds(59));
        service.procesarPendientes();

        assertThat(canal.enviados()).isEmpty();
        assertThat(fila(900203)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
    }

    @Test
    void unFalloNoFrenaAlResto() {
        encolarConfirmacion(900204, "rechazado@ejemplo.test");
        encolarConfirmacion(900205, "paciente@ejemplo.test");
        canal.rechazar("rechazado@ejemplo.test");

        service.procesarPendientes();

        assertThat(fila(900204)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1)
                .containsEntry("error", "Destinatario rechazado por el canal en memoria");
        assertThat(fila(900205)).containsEntry("estado", "ENVIADA").containsEntry("intentos", 1);
    }

    @Test
    void lasEnviadasYFallidasNoSeVuelvenAProcesar() {
        encolarConfirmacion(900206, "paciente@ejemplo.test");
        service.procesarPendientes();

        reloj.advance(Duration.ofHours(1));
        service.procesarPendientes();

        assertThat(canal.enviados()).hasSize(1);
        assertThat(fila(900206)).containsEntry("intentos", 1);
    }
}
