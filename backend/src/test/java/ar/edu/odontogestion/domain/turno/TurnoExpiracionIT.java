package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Expiración masiva de pendientes vencidas (design D8) con el reloj avanzable. */
class TurnoExpiracionIT extends AbstractTurnoIT {

    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private TransactionTemplate tx;

    private int expirar() {
        Integer filas = tx.execute(s -> turnos.expirarPendientesVencidas(consultorioId(), reloj.instant()));
        return filas == null ? -1 : filas;
    }

    private long version(Long turnoId) {
        return jdbc.queryForObject("SELECT version FROM turno WHERE id = ?", Long.class, turnoId);
    }

    private Long pendiente(Long paciente, Long prestacion, String inicio, Instant hasta) {
        return insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes(inicio),
                lunes(inicio).plus(Duration.ofMinutes(30)), hasta);
    }

    @Test
    void laVencidaPasaAExpiradoConVersionIncrementadaYLaVigenteQuedaIntacta() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long vencida = pendiente(paciente, prestacion, "10:00", reloj.instant().minusSeconds(1));
        Long vigente = pendiente(paciente, prestacion, "11:00", reloj.instant().plusSeconds(600));

        assertThat(expirar()).isEqualTo(1);

        assertThat(estadoDe(vencida)).isEqualTo("EXPIRADO");
        assertThat(version(vencida)).isEqualTo(1L);
        assertThat(estadoDe(vigente)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(version(vigente)).isZero();
    }

    @Test
    void laSegundaEjecucionNoModificaNada() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long vencida = pendiente(paciente, prestacion, "10:00", reloj.instant().minusSeconds(60));
        expirar();
        Instant actualizado = jdbc.queryForObject("SELECT actualizado_en FROM turno WHERE id = ?",
                java.sql.Timestamp.class, vencida).toInstant();

        assertThat(expirar()).isZero();

        assertThat(version(vencida)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM turno WHERE id = ?", java.sql.Timestamp.class,
                vencida).toInstant()).isEqualTo(actualizado);
    }

    @Test
    void elLimiteExactoCuentaComoVencidoYElRelojAvanzadoVenceALaVigente() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long exacta = pendiente(paciente, prestacion, "10:00", reloj.instant());
        Long vigente = pendiente(paciente, prestacion, "11:00", reloj.instant().plusSeconds(600));

        assertThat(expirar()).isEqualTo(1);
        assertThat(estadoDe(exacta)).isEqualTo("EXPIRADO");

        reloj.advance(Duration.ofMinutes(11));

        assertThat(expirar()).isEqualTo(1);
        assertThat(estadoDe(vigente)).isEqualTo("EXPIRADO");
    }

    @Test
    void soloAfectaPendientesDelConsultorioYNingunOtroEstado() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long reservado = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("09:00"), lunes("09:30"));
        Long otro = crearOtroConsultorio();
        Long pacienteAjeno = crearPaciente(otro, true);
        Long prestacionAjena = crearPrestacion(otro, 30, true);
        Long ajena = jdbc.queryForObject("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, "
                + "estado, origen, pendiente_hasta) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, "
                + "'PENDIENTE_CONFIRMACION', 'PUBLICA', ?::timestamptz) RETURNING id", Long.class, otro,
                pacienteAjeno, prestacionAjena, lunes("10:00").toString(), lunes("10:30").toString(),
                reloj.instant().minusSeconds(60).toString());

        assertThat(expirar()).isZero();

        assertThat(estadoDe(reservado)).isEqualTo("RESERVADO");
        assertThat(estadoDe(ajena)).isEqualTo("PENDIENTE_CONFIRMACION");
    }
}
