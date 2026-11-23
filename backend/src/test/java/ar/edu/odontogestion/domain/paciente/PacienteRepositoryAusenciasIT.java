package ar.edu.odontogestion.domain.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Contador de ausencias con {@code UPDATE} atómico (C-08 design D3). Pacientes ficticios de {@link AbstractTurnoIT}. */
class PacienteRepositoryAusenciasIT extends AbstractTurnoIT {

    private static final int RONDAS = 10;

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private TransactionTemplate tx;

    private int contador(Long pacienteId) {
        return jdbc.queryForObject("SELECT cantidad_ausencias FROM paciente WHERE id = ?", Integer.class, pacienteId);
    }

    @Test
    void incrementarSumaUno() {
        Long paciente = crearPaciente();

        tx.executeWithoutResult(s -> pacientes.incrementarAusencias(paciente));
        tx.executeWithoutResult(s -> pacientes.incrementarAusencias(paciente));

        assertThat(contador(paciente)).isEqualTo(2);
    }

    @Test
    void decrementarRestaUnoYDevuelveLaFilaAfectada() {
        Long paciente = crearPaciente();
        tx.executeWithoutResult(s -> pacientes.incrementarAusencias(paciente));

        Integer afectadas = tx.execute(s -> pacientes.decrementarAusencias(paciente));

        assertThat(afectadas).isEqualTo(1);
        assertThat(contador(paciente)).isZero();
    }

    @Test
    void decrementarConElContadorEnCeroNoLoVuelveNegativo() {
        Long paciente = crearPaciente();

        Integer afectadas = tx.execute(s -> pacientes.decrementarAusencias(paciente));

        assertThat(afectadas).isZero();
        assertThat(contador(paciente)).isZero();
    }

    @Test
    void dosIncrementosSimultaneosDelMismoPacienteSumanDos() throws Exception {
        Long paciente = crearPaciente();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 1; ronda <= RONDAS; ronda++) {
                CountDownLatch largada = new CountDownLatch(1);
                Future<?> a = hilos.submit(() -> {
                    largada.await();
                    return tx.execute(s -> pacientes.incrementarAusencias(paciente));
                });
                Future<?> b = hilos.submit(() -> {
                    largada.await();
                    return tx.execute(s -> pacientes.incrementarAusencias(paciente));
                });
                largada.countDown();
                a.get(10, TimeUnit.SECONDS);
                b.get(10, TimeUnit.SECONDS);

                assertThat(contador(paciente)).as("ronda %d", ronda).isEqualTo(2 * ronda);
            }
        } finally {
            hilos.shutdownNow();
        }
    }
}
