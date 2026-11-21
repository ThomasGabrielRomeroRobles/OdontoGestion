package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carreras reales contra PostgreSQL (design D3): el chequeo previo en Java no ve la fila sin commitear de otra
 * transacción, el {@code saveAndFlush} queda bloqueado por la restricción y, al commitear la otra, la violación se
 * traduce a 422 con campo. Prueba la garantía de la base, no el chequeo en Java.
 */
class RestriccionesConcurrentesIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private PrestacionService prestaciones;

    @Autowired
    private HorarioService horarios;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Carrera'");
        jdbc.update("DELETE FROM horario_trabajo WHERE dia_semana = 7");
    }

    @Test
    void franjaSolapadaEnCarreraLaFrenaElExcludeY422EnHoraDesde() throws Exception {
        Throwable lanzado = enCarrera(
                db -> db.update("INSERT INTO horario_trabajo (consultorio_id, dia_semana, hora_desde, hora_hasta) "
                        + "VALUES (?, 7, '09:00', '13:00')", consultorioId()),
                () -> horarios.crear(new DatosHorario(7, "12:00", "15:00")));

        assertThat(lanzado).isNotNull();
        esErrorDeCampo(lanzado, "horaDesde");
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM horario_trabajo WHERE dia_semana = 7",
                Integer.class);
        assertThat(filas).isEqualTo(1);
    }

    private Long consultorioId() {
        return jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
    }

    /**
     * Abre otra transacción que ejecuta {@code insercionRival} y espera; corre {@code accion}, que debe quedar
     * bloqueada por la restricción; recién entonces commitea la rival y devuelve lo que lanzó {@code accion}.
     */
    private Throwable enCarrera(Consumer<JdbcTemplate> insercionRival, Runnable accion) throws Exception {
        CountDownLatch insertada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CompletableFuture<Void> rival = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            insercionRival.accept(jdbc);
            insertada.countDown();
            try {
                liberar.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(insertada.await(30, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> nuestra = CompletableFuture.runAsync(accion);
        esperarBloqueoPorLock();
        liberar.countDown();
        rival.get(30, TimeUnit.SECONDS);

        try {
            nuestra.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    /** Espera a que alguna sesión quede esperando un lock: la nuestra, frenada por la fila de la rival. */
    private void esperarBloqueoPorLock() throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            Integer esperando = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (esperando != null && esperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("La operación no quedó bloqueada por la restricción");
    }

    private static void esErrorDeCampo(Throwable lanzado, String campo) {
        assertThatThrownBy(() -> {
            throw lanzado;
        }).isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).containsExactly(campo));
    }

    @Test
    void nombreDuplicadoEnCarreraLoFrenaLaRestriccionUnicaY422EnNombre() throws Exception {
        Throwable lanzado = enCarrera(
                db -> db.update("INSERT INTO prestacion (consultorio_id, nombre, duracion_min) VALUES (?, 'Carrera', 30)",
                        consultorioId()),
                () -> prestaciones.crear(new DatosPrestacion("carrera", 30)));

        assertThat(lanzado).isNotNull();
        esErrorDeCampo(lanzado, "nombre");
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM prestacion WHERE nombre = 'Carrera'", Integer.class);
        assertThat(filas).isEqualTo(1);
    }
}
