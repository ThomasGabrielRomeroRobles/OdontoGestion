package ar.edu.odontogestion.application.listaespera;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.listaespera.AvisoNoDisponibleException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * "El primero gana" con dos transacciones reales en paralelo contra PostgreSQL (C-10 D5): dos pacientes aceptan a la
 * vez el mismo hueco; exactamente uno se queda con el turno y el otro recibe 409.
 */
class AvisoAceptacionConcurrenteIT extends AbstractListaEsperaIT {

    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private AvisoComandos comandos;
    @Autowired
    private TokenAccesoService tokens;

    private Object resultado(Future<AvisoAceptadoResponse> f) throws Exception {
        try {
            return f.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        }
    }

    @RepeatedTest(5)
    void dosAceptacionesSimultaneasDejanUnGanadorYUnConflicto() throws Exception {
        Long prestacion = crearPrestacion(30);
        Long turnoOrigen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        List<Long> entradas = new ArrayList<>();
        List<Long> avisos = new ArrayList<>();
        List<TokenEmitido> emitidos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Long entrada = insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
            Long aviso = insertarAviso(entrada, turnoOrigen, prestacion, lunes("10:00"), lunes("10:30"), "ENVIADO",
                    reloj.instant().plusSeconds(3600));
            entradas.add(entrada);
            avisos.add(aviso);
            emitidos.add(tokens.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(aviso),
                    reloj.instant().plusSeconds(3600)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<AvisoAceptadoResponse>> futuros = new ArrayList<>();
        for (TokenEmitido t : emitidos) {
            futuros.add(pool.submit(() -> {
                listos.countDown();
                largada.await();
                return comandos.aceptar(t.valor());
            }));
        }
        listos.await();
        largada.countDown();
        Object r1 = resultado(futuros.get(0));
        Object r2 = resultado(futuros.get(1));
        pool.shutdownNow();

        long ganadores = List.of(r1, r2).stream().filter(r -> r instanceof AvisoAceptadoResponse).count();
        long conflictos = List.of(r1, r2).stream().filter(r -> r instanceof AvisoNoDisponibleException).count();
        assertThat(ganadores).as("resultados: %s / %s", r1, r2).isEqualTo(1);
        assertThat(conflictos).as("resultados: %s / %s", r1, r2).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE inicio = ?::timestamptz AND estado NOT IN "
                + "('CANCELADO','EXPIRADO')", Integer.class, lunes("10:00").toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrada_lista_espera WHERE id IN (?, ?) AND estado = "
                + "'CUBIERTA'", Integer.class, entradas.get(0), entradas.get(1))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_hueco WHERE turno_origen_id = ? AND estado = "
                + "'ACEPTADO'", Integer.class, turnoOrigen)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_hueco WHERE turno_origen_id = ? AND estado = "
                + "'CERRADO'", Integer.class, turnoOrigen)).isEqualTo(1);
    }
}
