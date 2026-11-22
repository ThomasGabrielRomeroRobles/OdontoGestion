package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancelar y mover el mismo turno a la vez (design D9), contra PostgreSQL real y sin {@code @Transactional} en el
 * test. Para que la superposición sea determinística, una transacción auxiliar toma el candado de la fila
 * ({@code SELECT ... FOR UPDATE}): las dos operaciones leen la versión 0 (las lecturas no se bloquean), quedan
 * esperando en su {@code UPDATE} y, al soltar el candado, la primera aplica y la segunda ve {@code version = 1}.
 */
class GestionTurnoConcurrenciaIT extends AbstractTurnoIT {

    private static final int RONDAS = 10;

    @Autowired
    private GestionTurnoService gestion;
    @Autowired
    private TransactionTemplate tx;

    private Throwable ejecutar(Runnable operacion) {
        try {
            operacion.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Test
    void cancelarYMoverElMismoTurnoALaVezAplicaExactamenteUnoYElOtroEsConflictoDeVersion() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            Instant inicio = dia(ronda % 5, ronda < 5 ? "09:00" : "11:00");
            Instant destino = dia(ronda % 5, ronda < 5 ? "15:00" : "16:30");
            Long id = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, inicio,
                    inicio.plus(Duration.ofMinutes(30)));

            List<Throwable> resultados = enCarrera(id, destino);

            long exitos = resultados.stream().filter(r -> r == null).count();
            List<Throwable> fallos = resultados.stream().filter(r -> r != null).toList();
            assertThat(exitos).as("ronda " + ronda + ": exactamente una aplicada").isEqualTo(1);
            assertThat(fallos).as("ronda " + ronda).singleElement()
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(jdbc.queryForObject("SELECT version FROM turno WHERE id = ?", Long.class, id))
                    .as("una sola modificación persistida").isEqualTo(1L);
            String estado = estadoDe(id);
            Instant inicioFinal = jdbc.queryForObject("SELECT inicio FROM turno WHERE id = ?",
                    java.sql.Timestamp.class, id).toInstant();
            assertThat(estado.equals("CANCELADO") ? inicioFinal.equals(inicio) : inicioFinal.equals(destino))
                    .as("estado " + estado + " con inicio " + inicioFinal).isTrue();
        }
    }

    /** Devuelve, para [cancelar, mover], la excepción lanzada o {@code null} si se aplicó. */
    private List<Throwable> enCarrera(Long id, Instant destino) throws Exception {
        CountDownLatch candadoTomado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        CompletableFuture<Void> candado = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForList("SELECT id FROM turno WHERE id = ? FOR UPDATE", id);
            candadoTomado.countDown();
            try {
                soltar.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(candadoTomado.await(30, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Throwable> cancelar = CompletableFuture.supplyAsync(
                () -> ejecutar(() -> gestion.cancelar(id, "Concurrente")));
        CompletableFuture<Throwable> mover = CompletableFuture.supplyAsync(
                () -> ejecutar(() -> gestion.mover(id, destino)));
        esperarDosSesionesBloqueadas();
        soltar.countDown();
        candado.get(30, TimeUnit.SECONDS);
        return java.util.Arrays.asList(nullable(cancelar), nullable(mover));
    }

    private static Throwable nullable(CompletableFuture<Throwable> futuro) throws Exception {
        return futuro.get(30, TimeUnit.SECONDS);
    }

    private void esperarDosSesionesBloqueadas() throws InterruptedException {
        Instant limite = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(limite)) {
            Integer esperando = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (esperando != null && esperando >= 2) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Las dos operaciones no quedaron esperando el candado de la fila");
    }
}
