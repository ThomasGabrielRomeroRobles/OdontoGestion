package ar.edu.odontogestion.application.token;

import ar.edu.odontogestion.support.TurnosFicticios;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(FixedClockTestConfig.class)
@TurnosFicticios
class TokenConsumoConcurrenteIT extends AbstractIntegrationTest {

    private static final int RONDAS = 20;

    @Autowired
    private TokenAccesoService service;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    @Test
    void dosConsumosConcurrentesDelMismoTokenTienenExactamenteUnGanador() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < RONDAS; ronda++) {
                ReferenciaToken.Turno turno = new ReferenciaToken.Turno(900200 + ronda);
                TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, turno,
                        AHORA.plus(Duration.ofHours(1)));
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<Object>> resultados = new ArrayList<>();
                for (int h = 0; h < 2; h++) {
                    resultados.add(pool.submit(() -> {
                        largada.await();
                        try {
                            return tx.execute(status -> {
                                ReferenciaToken ref = service.consumir(emitido.valor(), PropositoToken.CONFIRMAR_TURNO);
                                pausaCorta();
                                return ref;
                            });
                        } catch (TokenInvalidoException e) {
                            return e;
                        }
                    }));
                }
                largada.countDown();

                List<Object> obtenidos = new ArrayList<>();
                for (Future<Object> f : resultados) {
                    obtenidos.add(f.get());
                }
                assertThat(obtenidos).as("ronda %d", ronda).hasSize(2);
                assertThat(obtenidos.stream().filter(o -> o instanceof ReferenciaToken).toList())
                        .as("ganadores en ronda %d", ronda).containsExactly(turno);
                assertThat(obtenidos.stream().filter(o -> o instanceof TokenInvalidoException).count())
                        .as("perdedores en ronda %d", ronda).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Mantiene la transaccion ganadora abierta un instante para forzar que el perdedor espere el lock. */
    private static void pausaCorta() {
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
