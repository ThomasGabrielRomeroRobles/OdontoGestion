package ar.edu.odontogestion.application.turno;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.turno.AusenteAntesDeToleranciaException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.ReversionFueraDePlazoException;
import ar.edu.odontogestion.domain.turno.TransicionInvalidaException;
import ar.edu.odontogestion.domain.turno.evento.AusenciaRevertida;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.TurnoAusente;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Marcar ausente y revertir (C-08 design D2–D4). "Ahora" es el reloj fijo (2026-11-20T12:00Z); la tolerancia del
 * consultorio es la del seed (15 min) salvo donde se cambia y se restaura.
 */
@RecordApplicationEvents
class AusenciaTurnoServiceIT extends AbstractTurnoIT {

    @Autowired
    private TurnoComandos comandos;

    @Autowired
    private ApplicationEvents eventos;

    private Long paciente;
    private Long prestacion;
    private int toleranciaOriginal;

    @BeforeEach
    void datos() {
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
        toleranciaOriginal = jdbc.queryForObject("SELECT tolerancia_ausente_min FROM consultorio WHERE id = ?",
                Integer.class, consultorioId());
    }

    @AfterEach
    void restaurarTolerancia() {
        jdbc.update("UPDATE consultorio SET tolerancia_ausente_min = ? WHERE id = ?", toleranciaOriginal,
                consultorioId());
    }

    /** Turno de 30 min que empezó {@code minutosAtras} antes del reloj fijo. */
    private Long turnoQueEmpezoHace(int minutosAtras, EstadoTurno estado) {
        Instant inicio = AHORA.minus(Duration.ofMinutes(minutosAtras));
        return insertarTurno(paciente, prestacion, estado, inicio, inicio.plus(Duration.ofMinutes(30)));
    }

    private int ausenciasDelPaciente() {
        return jdbc.queryForObject("SELECT cantidad_ausencias FROM paciente WHERE id = ?", Integer.class, paciente);
    }

    private Map<String, Object> fila(Long turno) {
        return jdbc.queryForMap("SELECT estado, ausente_marcado_en, estado_previo_ausencia FROM turno WHERE id = ?",
                turno);
    }

    // ---- 4.1 marcar ausente ----

    @Test
    void marcarAusentePasadaLaToleranciaDevuelveElTurnoAusenteConLaMarcaYElContador() {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);

        TurnoDto dto = comandos.marcarAusente(turno);

        assertThat(dto.estado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(dto.ausenteMarcadoEn()).isEqualTo(AHORA);
        assertThat(dto.paciente().cantidadAusencias()).isEqualTo(1);
        assertThat(ausenciasDelPaciente()).isEqualTo(1);
        assertThat(fila(turno)).containsEntry("estado", "AUSENTE").containsEntry("estado_previo_ausencia", "RESERVADO");
        assertThat(eventos.stream(TurnoAusente.class)).singleElement()
                .satisfies(e -> assertThat(e.turnoId()).isEqualTo(turno));
        assertThat(eventos.stream(HuecoLiberado.class)).isEmpty();
    }

    @Test
    void justoAlCumplirseLaToleranciaSePuedeMarcar() {
        Long turno = turnoQueEmpezoHace(15, EstadoTurno.CONFIRMADO);

        assertThat(comandos.marcarAusente(turno).estado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(fila(turno)).containsEntry("estado_previo_ausencia", "CONFIRMADO");
    }

    @Test
    void antesDeLaToleranciaEsRechazadoSinTocarElContadorNiPublicarEventos() {
        Long turno = turnoQueEmpezoHace(14, EstadoTurno.RESERVADO);

        assertThatThrownBy(() -> comandos.marcarAusente(turno)).isInstanceOf(AusenteAntesDeToleranciaException.class);

        assertThat(fila(turno)).containsEntry("estado", "RESERVADO");
        assertThat(ausenciasDelPaciente()).isZero();
        assertThat(eventos.stream(TurnoAusente.class)).isEmpty();
    }

    @Test
    void transicionInvalidaNoTocaElContador() {
        Long turno = turnoQueEmpezoHace(60, EstadoTurno.ATENDIDO);

        assertThatThrownBy(() -> comandos.marcarAusente(turno)).isInstanceOf(TransicionInvalidaException.class);

        assertThat(ausenciasDelPaciente()).isZero();
        assertThat(eventos.stream(TurnoAusente.class)).isEmpty();
    }

    @Test
    void turnoDeOtroConsultorioOInexistenteEsNoEncontrado() {
        Long otro = crearOtroConsultorio();
        Long pacienteAjeno = crearPaciente(otro, true);
        Long prestacionAjena = crearPrestacion(otro, 30, true);
        Instant inicio = AHORA.minus(Duration.ofHours(1));
        Long ajeno = jdbc.queryForObject("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, "
                + "estado, origen) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'RESERVADO', 'ODONTOLOGO') "
                + "RETURNING id", Long.class, otro, pacienteAjeno, prestacionAjena, inicio.toString(),
                inicio.plus(Duration.ofMinutes(30)).toString());

        assertThatThrownBy(() -> comandos.marcarAusente(ajeno)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> comandos.marcarAusente(999_999_999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> comandos.revertirAusencia(999_999_999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- 4.3 revertir, tolerancia configurada e intervalo ocupado ----

    @Test
    void revertirDentroDeLas24HorasVuelveAlEstadoPrevioYRestaElContador() {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.CONFIRMADO);
        comandos.marcarAusente(turno);
        reloj.advance(Duration.ofHours(23));

        TurnoDto dto = comandos.revertirAusencia(turno);

        assertThat(dto.estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(dto.ausenteMarcadoEn()).isNull();
        assertThat(dto.paciente().cantidadAusencias()).isZero();
        assertThat(fila(turno)).containsEntry("estado", "CONFIRMADO").containsEntry("ausente_marcado_en", null)
                .containsEntry("estado_previo_ausencia", null);
        assertThat(eventos.stream(AusenciaRevertida.class)).singleElement()
                .satisfies(e -> assertThat(e.turnoId()).isEqualTo(turno));
    }

    @Test
    void revertirFueraDePlazoNoCambiaNadaNiPublicaEventos() {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);
        comandos.marcarAusente(turno);
        reloj.advance(Duration.ofHours(24));

        assertThatThrownBy(() -> comandos.revertirAusencia(turno))
                .isInstanceOf(ReversionFueraDePlazoException.class);

        assertThat(fila(turno)).containsEntry("estado", "AUSENTE");
        assertThat(ausenciasDelPaciente()).isEqualTo(1);
        assertThat(eventos.stream(AusenciaRevertida.class)).isEmpty();
    }

    @Test
    void marcarDeNuevoDespuesDeRevertirVuelveASumar() {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);
        comandos.marcarAusente(turno);
        comandos.revertirAusencia(turno);

        comandos.marcarAusente(turno);

        assertThat(fila(turno)).containsEntry("estado", "AUSENTE");
        assertThat(ausenciasDelPaciente()).isEqualTo(1);
    }

    @Test
    void laToleranciaConfiguradaPorElConsultorioSeRespeta() {
        jdbc.update("UPDATE consultorio SET tolerancia_ausente_min = 30 WHERE id = ?", consultorioId());
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);

        assertThatThrownBy(() -> comandos.marcarAusente(turno)).isInstanceOf(AusenteAntesDeToleranciaException.class);
    }

    @Test
    void elIntervaloDeUnTurnoAusenteSigueOcupado() {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);
        comandos.marcarAusente(turno);
        Map<String, Object> rango = jdbc.queryForMap("SELECT inicio, fin FROM turno WHERE id = ?", turno);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, "
                + "fin, estado, origen) VALUES (?, ?, ?, ?, ?, 'RESERVADO', 'ODONTOLOGO')", consultorioId(),
                paciente, prestacion, rango.get("inicio"), rango.get("fin")))
                .hasMessageContaining("ex_turno_sin_superposicion");
    }
}
