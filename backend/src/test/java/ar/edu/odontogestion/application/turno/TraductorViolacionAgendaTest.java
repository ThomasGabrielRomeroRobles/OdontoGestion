package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

class TraductorViolacionAgendaTest {

    private final TraductorViolacionAgenda traductor = new TraductorViolacionAgenda();

    private static DataIntegrityViolationException violacion(String sqlState, String restriccion) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: violates constraint \"" + restriccion + "\"", sqlState));
    }

    @Test
    void laExclusionDeAgendaSeTraduceAHuecoNoDisponible() {
        DataIntegrityViolationException exclusion = violacion("23P01", "ex_turno_sin_superposicion");

        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw exclusion;
        })).isInstanceOf(HuecoNoDisponibleException.class).hasNoCause();
    }

    @Test
    void unaFkNoSeTraduceYSeRelanzaTalCual() {
        DataIntegrityViolationException fk = violacion("23503", "fk_turno_paciente");

        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw fk;
        })).isSameAs(fk);
    }

    @Test
    void otraExclusionConOtroNombreOUnSqlStateDistintoNoSeTraduce() {
        DataIntegrityViolationException otraExclusion = violacion("23P01", "ex_horario_trabajo_sin_solapamiento");
        DataIntegrityViolationException mismoNombreOtroEstado = violacion("23505", "ex_turno_sin_superposicion");

        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw otraExclusion;
        })).isSameAs(otraExclusion);
        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw mismoNombreOtroEstado;
        })).isSameAs(mismoNombreOtroEstado);
    }

    @Test
    void sinViolacionDevuelveElResultadoDeLaOperacion() {
        assertThat(traductor.ejecutar(() -> "ok")).isEqualTo("ok");
    }

    private static CannotAcquireLockException deadlock(String contexto) {
        return new CannotAcquireLockException("could not execute statement",
                new SQLException("ERROR: deadlock detected  Where: " + contexto, "40P01"));
    }

    @Test
    void elDeadlockEntreDosInsercionesQueChequeanLaExclusionDeTurnoEsUnConflictoDeAgenda() {
        // PostgreSQL puede abortar a una de dos transacciones concurrentes con 40P01 en vez de 23P01.
        CannotAcquireLockException perdedora = deadlock(
                "while checking exclusion constraint on tuple (0,5) in relation \"turno\"");

        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw perdedora;
        })).isInstanceOf(HuecoNoDisponibleException.class);
    }

    @Test
    void otroDeadlockNoSeTraduceYSeRelanzaTalCual() {
        CannotAcquireLockException otro = deadlock("while updating tuple (0,1) in relation \"paciente\"");
        CannotAcquireLockException otraExclusion = deadlock(
                "while checking exclusion constraint on tuple (0,5) in relation \"horario_trabajo\"");

        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw otro;
        })).isSameAs(otro);
        assertThatThrownBy(() -> traductor.ejecutar(() -> {
            throw otraExclusion;
        })).isSameAs(otraExclusion);
    }
}
