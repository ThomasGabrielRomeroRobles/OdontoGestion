package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.application.notificacion.NotificationServiceEncolarIT.DATOS_TURNO;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Dos procesamientos superpuestos no envían dos veces la misma notificación (FOR UPDATE SKIP LOCKED). */
@Import(FixedClockTestConfig.class)
class NotificationServiceConcurrenciaIT extends AbstractIntegrationTest {

    private static final int RONDAS = 5;

    @Autowired
    private NotificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private CanalEnMemoria canal;

    @AfterEach
    void limpiar() {
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    @Test
    void ejecucionesSuperpuestasEnvianExactamenteUnaVez() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < RONDAS; ronda++) {
                long turno = 900400 + ronda;
                tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO,
                        "paciente@ejemplo.test", new ReferenciaToken.Turno(turno), DATOS_TURNO, List.of()));
                canal.reiniciar();
                canal.demorar(Duration.ofMillis(300));

                CountDownLatch largada = new CountDownLatch(1);
                Future<Integer> a = hilos.submit(() -> {
                    largada.await();
                    return service.procesarPendientes();
                });
                Future<Integer> b = hilos.submit(() -> {
                    largada.await();
                    return service.procesarPendientes();
                });
                largada.countDown();
                int tomadas = a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS);

                assertThat(canal.enviados()).as("ronda %d", ronda).hasSize(1);
                assertThat(tomadas).as("ronda %d", ronda).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT intentos FROM notificacion WHERE turno_id = ?",
                        Integer.class, turno)).isEqualTo(1);
            }
        } finally {
            hilos.shutdownNow();
        }
    }
}
