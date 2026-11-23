package ar.edu.odontogestion.application.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.turno.GestionTurnoService;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** De punta a punta: HuecoLiberado tras el commit del turno cancelado genera los avisos (C-10 D4). */
class ListaEsperaHuecoListenerIT extends AbstractListaEsperaIT {

    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private GestionTurnoService gestion;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private ApplicationEventPublisher eventos;

    private int avisosDe(Long turnoOrigen) {
        return jdbc.queryForObject("SELECT count(*) FROM aviso_hueco WHERE turno_origen_id = ?", Integer.class,
                turnoOrigen);
    }

    @Test
    void cancelarUnTurnoGeneraAvisosParaLasEntradasCompatiblesTrasElCommit() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        Long turno = insertarTurno(crearPaciente(), prestacion, EstadoTurno.RESERVADO, lunes("10:00"),
                lunes("10:30"));

        gestion.cancelar(turno, null);

        assertThat(avisosDe(turno)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'AVISO_HUECO' AND aviso_id "
                + "IN (SELECT id FROM aviso_hueco WHERE turno_origen_id = ?)", Integer.class, turno)).isEqualTo(2);
    }

    @Test
    void moverUnTurnoLiberaSuRangoAnteriorYAvisa() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        Long turno = insertarTurno(crearPaciente(), prestacion, EstadoTurno.RESERVADO, lunes("10:00"),
                lunes("10:30"));

        gestion.mover(turno, lunes("15:00"));

        assertThat(avisosDe(turno)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT inicio = ?::timestamptz FROM aviso_hueco WHERE turno_origen_id = ?",
                Boolean.class, lunes("10:00").toString(), turno)).isTrue();
    }

    @Test
    void siLaOperacionQueLiberaSeRevierteNoSeCreaNingunAviso() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        Long turno = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            eventos.publishEvent(new HuecoLiberado(consultorioId(), turno, prestacion, lunes("10:00"),
                    lunes("10:30")));
            throw new IllegalStateException("falla posterior");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(avisosDe(turno)).isZero();
    }
}
