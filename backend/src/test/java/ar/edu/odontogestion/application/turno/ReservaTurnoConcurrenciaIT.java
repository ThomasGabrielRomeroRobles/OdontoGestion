package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Carreras reales contra PostgreSQL (regla dura #8): sin {@code @Transactional} en el test, dos hilos que arrancan
 * a la vez con un {@link CountDownLatch}. El ganador lo decide la restricción de exclusión, no Java.
 */
class ReservaTurnoConcurrenciaIT extends AbstractTurnoIT {

    private static final int RONDAS = 10;

    @Autowired
    private ReservaTurnoService reservas;

    private Callable<Object> reserva(CountDownLatch salida, Long paciente, Long prestacion, Instant inicio) {
        return () -> {
            salida.await();
            try {
                return reservas.reservar(SolicitudReserva.delOdontologo(paciente, prestacion, inicio));
            } catch (HuecoNoDisponibleException e) {
                return e;
            }
        };
    }

    /** Ejecuta las dos reservas en paralelo y devuelve sus resultados (el turno o la excepción de conflicto). */
    private List<Object> enCarrera(Long paciente, Long prestacion, Instant inicioA, Instant inicioB)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            Future<Object> a = pool.submit(reserva(salida, paciente, prestacion, inicioA));
            Future<Object> b = pool.submit(reserva(salida, paciente, prestacion, inicioB));
            salida.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private void verificarUnGanador(List<Object> resultados) {
        List<Object> exitos = new ArrayList<>(resultados.stream().filter(r -> r instanceof TurnoDto).toList());
        List<Object> conflictos = resultados.stream().filter(r -> r instanceof HuecoNoDisponibleException).toList();
        assertThat(exitos).as("exactamente un éxito").hasSize(1);
        assertThat(conflictos).as("y un conflicto").hasSize(1);
    }

    @Test
    void dosReservasDelMismoHuecoTienenExactamenteUnGanador() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            Instant inicio = dia(ronda % 5, ronda < 5 ? "09:00" : "11:00");
            int antes = contarTurnos();

            verificarUnGanador(enCarrera(paciente, prestacion, inicio, inicio));

            assertThat(contarTurnos()).as("ronda " + ronda).isEqualTo(antes + 1);
        }
    }

    @Test
    void dosReservasDeHuecosSolapadosDistintosTienenExactamenteUnGanador() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            Instant a = dia(ronda % 5, ronda < 5 ? "15:00" : "16:30");
            Instant b = a.plusSeconds(15 * 60);
            int antes = contarTurnos();

            verificarUnGanador(enCarrera(paciente, prestacion, a, b));

            assertThat(contarTurnos()).as("ronda " + ronda).isEqualTo(antes + 1);
        }
    }

    @Test
    void dosReservasContiguasEnParaleloSeAceptanAmbas() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        List<Object> resultados = enCarrera(paciente, prestacion, lunes("17:00"), lunes("17:30"));

        assertThat(resultados).allMatch(r -> r instanceof TurnoDto);
        assertThat(contarTurnos()).isEqualTo(2);
    }
}
