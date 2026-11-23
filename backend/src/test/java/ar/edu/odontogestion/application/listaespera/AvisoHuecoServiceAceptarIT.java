package ar.edu.odontogestion.application.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.listaespera.AvisoNoDisponibleException;
import ar.edu.odontogestion.domain.listaespera.AvisoVencidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Aceptación de un aviso (C-10 D5), secuencial. "Ahora" = viernes 2026-11-20 09:00 BA; el hueco es el lunes 23 de
 * 10:00 a 10:30. Los avisos de un mismo hueco comparten {@code turno_origen_id} e {@code inicio}.
 */
class AvisoHuecoServiceAceptarIT extends AbstractListaEsperaIT {

    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private AvisoHuecoService servicio;
    @Autowired
    private AvisoComandos comandos;
    @Autowired
    private TokenAccesoService tokens;

    /** Un hueco liberado con {@code n} avisos {@code ENVIADO} (entrada ACTIVA + token) de pacientes distintos. */
    private record Escenario(Long prestacion, Long turnoOrigen, List<Long> pacientes, List<Long> entradas,
            List<Long> avisos, List<TokenEmitido> tokens) {
    }

    private Escenario escenario(int n) {
        Long prestacion = crearPrestacion(30);
        Long turnoOrigen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        Escenario e = new Escenario(prestacion, turnoOrigen, new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        for (int i = 0; i < n; i++) {
            Long paciente = crearPaciente();
            Long entrada = insertarEntrada(paciente, prestacion, "ACTIVA", LUN, LUN);
            Long aviso = insertarAviso(entrada, turnoOrigen, prestacion, lunes("10:00"), lunes("10:30"), "ENVIADO",
                    reloj.instant().plusSeconds(3600));
            e.pacientes().add(paciente);
            e.entradas().add(entrada);
            e.avisos().add(aviso);
            e.tokens().add(tokens.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(aviso),
                    reloj.instant().plusSeconds(3600)));
        }
        return e;
    }

    private int turnosEnElRango() {
        return jdbc.queryForObject("SELECT count(*) FROM turno WHERE inicio = ?::timestamptz AND estado NOT IN "
                + "('CANCELADO','EXPIRADO')", Integer.class, lunes("10:00").toString());
    }

    private boolean tokenUsado(Long avisoId) {
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE aviso_id = ?",
                Boolean.class, avisoId);
    }

    @Test
    void aceptarCreaElTurnoReservadoYCierraLosDemasAvisos() {
        Escenario e = escenario(2);

        AvisoAceptadoResponse r = servicio.aceptar(e.tokens().get(0).valor());

        assertThat(r.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(r.inicio()).isEqualTo(lunes("10:00"));
        assertThat(r.fin()).isEqualTo(lunes("10:30"));
        assertThat(r.prestacion()).startsWith("IT-Turno-");
        Map<String, Object> turno = jdbc.queryForMap("SELECT * FROM turno WHERE paciente_id = ?",
                e.pacientes().get(0));
        assertThat(turno.get("estado")).isEqualTo("RESERVADO");
        assertThat(turno.get("origen")).isEqualTo("LISTA_ESPERA");
        assertThat(estadoAviso(e.avisos().get(0))).isEqualTo("ACEPTADO");
        assertThat(estadoAviso(e.avisos().get(1))).isEqualTo("CERRADO");
        assertThat(estadoEntrada(e.entradas().get(0))).isEqualTo("CUBIERTA");
        assertThat(estadoEntrada(e.entradas().get(1))).isEqualTo("ACTIVA");
        assertThat(jdbc.queryForObject("SELECT turno_resultante_id FROM entrada_lista_espera WHERE id = ?",
                Long.class, e.entradas().get(0))).isEqualTo(turno.get("id"));
        assertThat(tokenUsado(e.avisos().get(0))).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'CONFIRMACION_TURNO' AND "
                + "turno_id = ?", Integer.class, turno.get("id"))).isEqualTo(1);
    }

    @Test
    void elPerdedorRecibe409YNoSeCreaOtroTurno() {
        Escenario e = escenario(2);
        servicio.aceptar(e.tokens().get(0).valor());

        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(1).valor()))
                .isInstanceOf(AvisoNoDisponibleException.class);

        assertThat(turnosEnElRango()).isEqualTo(1);
        assertThat(tokenUsado(e.avisos().get(1))).isFalse();
        assertThat(estadoEntrada(e.entradas().get(1))).isEqualTo("ACTIVA");
    }

    @Test
    void unaEntradaQueYaNoEstaActivaDa409SinTurno() {
        Escenario e = escenario(1);
        jdbc.update("UPDATE entrada_lista_espera SET estado = 'CANCELADA' WHERE id = ?", e.entradas().get(0));

        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(AvisoNoDisponibleException.class);

        assertThat(turnosEnElRango()).isZero();
        assertThat(tokenUsado(e.avisos().get(0))).isFalse();
    }

    @Test
    void unAvisoCerradoDa409() {
        Escenario e = escenario(1);
        jdbc.update("UPDATE aviso_hueco SET estado = 'CERRADO' WHERE id = ?", e.avisos().get(0));

        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(AvisoNoDisponibleException.class);
        assertThat(turnosEnElRango()).isZero();
    }

    @Test
    void unTokenUsadoOdeOtroPropositoEsInvalido() {
        Escenario e = escenario(1);
        servicio.aceptar(e.tokens().get(0).valor());
        Long turno = insertarTurno(crearPaciente(), e.prestacion(), EstadoTurno.RESERVADO, lunes("15:00"),
                lunes("15:30"));
        TokenEmitido otro = tokens.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(turno),
                lunes("15:00"));

        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> servicio.aceptar(otro.valor())).isInstanceOf(TokenInvalidoException.class);
        assertThat(jdbc.queryForObject("SELECT usado_en IS NULL FROM token_acceso WHERE turno_id = ?",
                Boolean.class, turno)).isTrue();
    }

    // --- 4.4 hueco tomado por una reserva normal (AvisoComandos) ---

    @Test
    void siUnaReservaNormalTomoElHuecoElComandoDa409CierraLosAvisosYDejaElTokenSinConsumir() {
        Escenario e = escenario(2);
        insertarTurno(crearPaciente(), e.prestacion(), EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        assertThatThrownBy(() -> comandos.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(HuecoNoDisponibleException.class);

        assertThat(tokenUsado(e.avisos().get(0))).isFalse();
        assertThat(estadoEntrada(e.entradas().get(0))).isEqualTo("ACTIVA");
        assertThat(estadoAviso(e.avisos().get(0))).isEqualTo("CERRADO");
        assertThat(estadoAviso(e.avisos().get(1))).isEqualTo("CERRADO");
        assertThat(turnosEnElRango()).isEqualTo(1); // solo la reserva normal
    }

    // --- 5.1 vencimiento perezoso ---

    @Test
    void unAvisoVencidoConTokenVigenteDa410YQuedaExpiradoAunqueLaOperacionFalle() {
        Escenario e = escenario(1);
        jdbc.update("UPDATE aviso_hueco SET vence_en = ?::timestamptz WHERE id = ?",
                reloj.instant().minusSeconds(5).toString(), e.avisos().get(0));

        assertThatThrownBy(() -> comandos.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(estadoAviso(e.avisos().get(0))).isEqualTo("EXPIRADO");
        assertThat(tokenUsado(e.avisos().get(0))).isFalse();
        assertThat(turnosEnElRango()).isZero();
        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(0).valor()))
                .isInstanceOf(AvisoNoDisponibleException.class);
    }

    @Test
    void elServicioLanzaAvisoVencidoParaQueElComandoLoMarque() {
        Escenario e = escenario(1);
        jdbc.update("UPDATE aviso_hueco SET vence_en = ?::timestamptz WHERE id = ?",
                Instant.parse("2026-11-20T11:59:59Z").toString(), e.avisos().get(0));

        assertThatThrownBy(() -> servicio.aceptar(e.tokens().get(0).valor()))
                .isInstanceOfSatisfying(AvisoVencidoException.class,
                        ex -> assertThat(ex.avisoId()).isEqualTo(e.avisos().get(0)));
    }
}
