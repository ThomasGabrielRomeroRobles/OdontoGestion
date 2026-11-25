package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractRecordatorioIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Consulta de candidatos a recordatorio (C-11 D1/D2/D7) contra PostgreSQL. Reloj fijo; H = 24. */
class TurnoRepositoryRecordatorioIT extends AbstractRecordatorioIT {

    private static final int H = 24;

    @Autowired
    private TurnoRepository turnos;

    private List<Long> candidatos() {
        Instant ahora = reloj.instant();
        return turnos.idsParaRecordatorio(consultorioId(), ahora, ahora.plus(Duration.ofHours(H)), H);
    }

    @Test
    void incluyeReservadosEnLaVentanaIncluidoElBordeSuperiorYExcluyeElInicioExactoYLoQueSeaPasaDeH() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long enElBorde = reservado(p, pr, ahora.plus(Duration.ofHours(H)));
        Long muyProximo = turno(p, pr, EstadoTurno.RESERVADO, ahora.plus(Duration.ofHours(1)),
                ahora.minus(Duration.ofHours(25)));
        Long yaEmpieza = reservado(p, pr, ahora);
        Long pasadoElBorde = reservado(p, pr, ahora.plus(Duration.ofHours(H)).plusSeconds(60));

        assertThat(candidatos()).containsExactlyInAnyOrder(enElBorde, muyProximo)
                .doesNotContain(yaEmpieza, pasadoElBorde);
    }

    @Test
    void excluyeElTurnoCreadoConMenosDeHorasDeAnticipacionRNNO09() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Instant inicio = ahora.plus(Duration.ofHours(5));
        Long reciente = turno(p, pr, EstadoTurno.RESERVADO, inicio, ahora.minus(Duration.ofHours(10)));
        Long justo = turno(p, pr, EstadoTurno.RESERVADO, inicio.plusSeconds(60), inicio.plusSeconds(60).minus(Duration.ofHours(H)));

        assertThat(candidatos()).containsExactly(justo).doesNotContain(reciente);
    }

    @Test
    void soloReservadoYSinRecordatorioPrevio() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long ok = reservado(p, pr, ahora.plus(Duration.ofHours(2)));
        Long confirmado = turno(p, pr, EstadoTurno.CONFIRMADO, ahora.plus(Duration.ofHours(3)), ahora.minus(Duration.ofHours(48)));
        Long pendiente = turno(p, pr, EstadoTurno.PENDIENTE_CONFIRMACION, ahora.plus(Duration.ofHours(4)),
                ahora.minus(Duration.ofHours(48)));
        Long cancelado = turno(p, pr, EstadoTurno.CANCELADO, ahora.plus(Duration.ofHours(5)), ahora.minus(Duration.ofHours(48)));
        Long conRecordatorio = reservado(p, pr, ahora.plus(Duration.ofHours(6)));
        jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, canal, destinatario, turno_id, datos, "
                + "enlaces, programada_para) VALUES (?, 'RECORDATORIO', 'EMAIL', 'x@ejemplo.test', ?, '{}'::jsonb, "
                + "'[]'::jsonb, now())", consultorioId(), conRecordatorio);

        assertThat(candidatos()).containsExactly(ok)
                .doesNotContain(confirmado, pendiente, cancelado, conRecordatorio);
    }

    @Test
    void filtraPorConsultorio() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        reservado(p, pr, reloj.instant().plus(Duration.ofHours(2)));

        assertThat(turnos.idsParaRecordatorio(crearOtroConsultorio(), reloj.instant(),
                reloj.instant().plus(Duration.ofHours(H)), H)).isEmpty();
    }
}
