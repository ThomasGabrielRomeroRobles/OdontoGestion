package ar.edu.odontogestion.application.acciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.acciones.ResumenAccionResponse.Motivo;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.LimiteCancelacionException;
import ar.edu.odontogestion.domain.turno.TransicionInvalidaException;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.SolicitudConfirmada;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Acciones por token (C-09 D6). "Ahora" = viernes 2026-11-20 09:00 BA; los turnos son el lunes 23 (más de 3 días
 * adelante) salvo donde se mueve el reloj. Límite de cancelación del seed: 2 horas.
 */
@RecordApplicationEvents
class AccionTokenServiceIT extends AbstractListaEsperaIT {

    @Autowired
    private AccionTokenService acciones;
    @Autowired
    private TokenAccesoService tokens;
    @Autowired
    private ApplicationEvents eventos;

    private Long turno(EstadoTurno estado, Instant inicio) {
        return insertarTurno(crearPaciente(), crearPrestacion(30), estado, inicio, inicio.plusSeconds(1800));
    }

    private TokenEmitido token(PropositoToken proposito, Long turnoId, Instant venceEn) {
        return tokens.emitir(proposito, new ReferenciaToken.Turno(turnoId), venceEn);
    }

    private boolean usado(TokenEmitido emitido) {
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE turno_id = ? "
                + "AND proposito = ?", Boolean.class, ((ReferenciaToken.Turno) emitido.referencia()).id(),
                emitido.proposito().name());
    }

    private List<Map<String, Object>> notificaciones(Long turnoId) {
        return jdbc.queryForList("SELECT tipo, estado FROM notificacion WHERE turno_id = ? ORDER BY id", turnoId);
    }

    // --- 8.1 resumen ---

    @Test
    void resumenDeUnTokenDeCancelacionVigenteMuestraElTurnoSinDatosDelPaciente() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));

        ResumenAccionResponse r = acciones.resumen(t.valor());

        assertThat(r.accion()).isEqualTo(PropositoToken.CANCELAR_TURNO);
        assertThat(r.venceEn()).isEqualTo(lunes("10:00"));
        assertThat(r.disponible()).isTrue();
        assertThat(r.motivo()).isNull();
        assertThat(r.limiteCancelacion()).isEqualTo(lunes("10:00").minus(Duration.ofHours(2)));
        assertThat(r.turno().prestacion()).startsWith("IT-Turno-");
        assertThat(r.turno().consultorio()).isNotBlank();
        assertThat(r.turno().inicio()).isEqualTo(lunes("10:00"));
        assertThat(r.turno().fin()).isEqualTo(lunes("10:30"));
        assertThat(r.turno().estado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void resumenDeConfirmarNoTraeLimiteDeCancelacionYTresLlamadasNoConsumen() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        for (int i = 0; i < 3; i++) {
            ResumenAccionResponse r = acciones.resumen(t.valor());
            assertThat(r.accion()).isEqualTo(PropositoToken.CONFIRMAR_TURNO);
            assertThat(r.disponible()).isTrue();
            assertThat(r.limiteCancelacion()).isNull();
        }

        assertThat(usado(t)).isFalse();
        assertThat(acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_TURNO).turno().estado())
                .isEqualTo(EstadoTurno.CONFIRMADO);
    }

    @Test
    void cancelacionFueraDelLimiteSeInformaConElInstanteLimite() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));
        reloj.set(lunes("10:00").minus(Duration.ofHours(1)));

        ResumenAccionResponse r = acciones.resumen(t.valor());

        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo(Motivo.FUERA_DE_LIMITE);
        assertThat(r.limiteCancelacion()).isEqualTo(lunes("10:00").minus(Duration.ofHours(2)));
    }

    @Test
    void turnoCanceladoPorElOdontologoSeMuestraComoEstadoQueNoPermiteLaAccion() {
        Long id = turno(EstadoTurno.CANCELADO, lunes("10:00"));
        TokenEmitido confirmar = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
        TokenEmitido cancelar = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));

        ResumenAccionResponse deConfirmar = acciones.resumen(confirmar.valor());
        ResumenAccionResponse deCancelar = acciones.resumen(cancelar.valor());

        assertThat(deConfirmar.turno().estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(deConfirmar.disponible()).isFalse();
        assertThat(deConfirmar.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE);
        assertThat(deCancelar.disponible()).isFalse();
        assertThat(deCancelar.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE);
    }

    @Test
    void unTokenDeAvisoOInvalidoEsGoneSinConsumirse() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long turno = insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, lunes("10:00"), lunes("10:30"));
        Long entrada = insertarEntrada(paciente, prestacion, "ACTIVA", java.time.LocalDate.of(2026, 11, 23),
                java.time.LocalDate.of(2026, 11, 23));
        Long avisoId = insertarAviso(entrada, turno, prestacion, lunes("10:00"), lunes("10:30"), "ENVIADO",
                lunes("09:00"));
        TokenEmitido aviso = tokens.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(avisoId),
                reloj.instant().plusSeconds(600));

        assertThatThrownBy(() -> acciones.resumen(aviso.valor())).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> acciones.resumen("no-existe-" + "x".repeat(30)))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(jdbc.queryForObject("SELECT usado_en IS NULL FROM token_acceso WHERE aviso_id = ?",
                Boolean.class, avisoId)).isTrue();
    }

    @Test
    void solicitudPendienteVigenteEsDisponibleYUnaVencidaSinExpirarEsGone() {
        Long vigente = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.PENDIENTE_CONFIRMACION,
                lunes("10:00"), lunes("10:30"), reloj.instant().plusSeconds(600));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_SOLICITUD, vigente, reloj.instant().plusSeconds(600));
        // Token con vida más larga que la retención: defensa en profundidad si la expiración no corrió.
        Long vencida = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.PENDIENTE_CONFIRMACION,
                lunes("11:00"), lunes("11:30"), reloj.instant().minusSeconds(5));
        TokenEmitido largo = token(PropositoToken.CONFIRMAR_SOLICITUD, vencida, reloj.instant().plusSeconds(3600));

        assertThat(acciones.resumen(t.valor()).disponible()).isTrue();
        assertThatThrownBy(() -> acciones.resumen(largo.valor())).isInstanceOf(TokenInvalidoException.class);
    }

    // --- 8.2 ejecutar ---

    @Test
    void confirmarTurnoConsumeElTokenYPasaAConfirmado() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        ResumenAccionResponse r = acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_TURNO);

        assertThat(estadoDe(id)).isEqualTo("CONFIRMADO");
        assertThat(r.turno().estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE);
        assertThat(r.venceEn()).isNull();
        assertThat(usado(t)).isTrue();
        assertThatThrownBy(() -> acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unaAccionDistintaDelPropositoEsGoneSinConsumirNiCambiarNada() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        assertThatThrownBy(() -> acciones.ejecutar(t.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> acciones.ejecutar(t.valor(), PropositoToken.ACEPTAR_AVISO))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
        assertThat(usado(t)).isFalse();
        assertThat(acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_TURNO).turno().estado())
                .isEqualTo(EstadoTurno.CONFIRMADO);
    }

    @Test
    void siLaAccionFallaElTokenSigueVigente() {
        Long cancelado = turno(EstadoTurno.CANCELADO, lunes("10:00"));
        TokenEmitido sobreCancelado = token(PropositoToken.CONFIRMAR_TURNO, cancelado, lunes("10:00"));
        Long atendido = turno(EstadoTurno.ATENDIDO, lunes("11:00"));
        TokenEmitido sobreAtendido = token(PropositoToken.CONFIRMAR_TURNO, atendido, lunes("11:00"));

        assertThatThrownBy(() -> acciones.ejecutar(sobreCancelado.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> acciones.ejecutar(sobreAtendido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TransicionInvalidaException.class);

        assertThat(estadoDe(cancelado)).isEqualTo("CANCELADO");
        assertThat(estadoDe(atendido)).isEqualTo("ATENDIDO");
        assertThat(usado(sobreCancelado)).isFalse();
        assertThat(usado(sobreAtendido)).isFalse();
        assertThat(acciones.resumen(sobreCancelado.valor()).disponible()).isFalse();
    }

    // --- 8.3 confirmar la solicitud ---

    @Test
    void confirmarLaSolicitudATiempoLaPasaAReservadaYPublicaElEvento() {
        Long id = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.PENDIENTE_CONFIRMACION,
                lunes("10:00"), lunes("10:30"), reloj.instant().plus(Duration.ofMinutes(10)));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_SOLICITUD, id, reloj.instant().plus(Duration.ofMinutes(10)));

        ResumenAccionResponse r = acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_SOLICITUD);

        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
        assertThat(r.turno().estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(eventos.stream(SolicitudConfirmada.class)).containsExactly(new SolicitudConfirmada(id));
    }

    @Test
    void unaSolicitudVencidaSinExpirarDa410YNoCambiaNiConsume() {
        Long id = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.PENDIENTE_CONFIRMACION,
                lunes("10:00"), lunes("10:30"), reloj.instant().minusSeconds(1));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_SOLICITUD, id, reloj.instant().plusSeconds(3600));

        assertThatThrownBy(() -> acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_SOLICITUD))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(estadoDe(id)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(usado(t)).isFalse();
        assertThat(eventos.stream(SolicitudConfirmada.class)).isEmpty();
    }

    // --- 8.4 notificación de confirmación ---

    @Test
    void alConfirmarLaSolicitudQuedaEncoladaLaConfirmacionDelTurno() {
        Long id = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.PENDIENTE_CONFIRMACION,
                lunes("10:00"), lunes("10:30"), reloj.instant().plus(Duration.ofMinutes(10)));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_SOLICITUD, id, reloj.instant().plus(Duration.ofMinutes(10)));
        assertThat(notificaciones(id)).isEmpty();

        acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_SOLICITUD);

        assertThat(notificaciones(id)).singleElement().satisfies(n -> assertThat(n)
                .containsEntry("tipo", "CONFIRMACION_TURNO").containsEntry("estado", "PENDIENTE"));
    }

    @Test
    void siLaConfirmacionFallaNoQuedaNotificacionFantasma() {
        Long id = turno(EstadoTurno.CANCELADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_SOLICITUD, id, lunes("10:00"));

        assertThatThrownBy(() -> acciones.ejecutar(t.valor(), PropositoToken.CONFIRMAR_SOLICITUD))
                .isInstanceOf(TransicionInvalidaException.class);

        assertThat(notificaciones(id)).isEmpty();
    }

    // --- 8.5 cancelar ---

    @Test
    void cancelarDejaElTurnoCanceladoPorElPacienteConEventosYNotificacion() {
        Long id = turno(EstadoTurno.CONFIRMADO, lunes("10:00"));
        TokenEmitido cancelar = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));
        TokenEmitido confirmar = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        ResumenAccionResponse r = acciones.ejecutar(cancelar.valor(), PropositoToken.CANCELAR_TURNO);

        assertThat(estadoDe(id)).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT cancelado_por FROM turno WHERE id = ?", String.class, id))
                .isEqualTo("PACIENTE");
        assertThat(jdbc.queryForObject("SELECT motivo_cancelacion FROM turno WHERE id = ?", String.class, id))
                .isNull();
        assertThat(r.turno().estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(eventos.stream(TurnoCancelado.class)).containsExactly(new TurnoCancelado(id,
                ar.edu.odontogestion.domain.turno.CanceladoPor.PACIENTE));
        assertThat(eventos.stream(HuecoLiberado.class)).singleElement().satisfies(h -> {
            assertThat(h.turnoOrigenId()).isEqualTo(id);
            assertThat(h.inicio()).isEqualTo(lunes("10:00"));
            assertThat(h.fin()).isEqualTo(lunes("10:30"));
        });
        assertThat(notificaciones(id)).singleElement().satisfies(n -> assertThat(n)
                .containsEntry("tipo", "CANCELACION"));
        assertThatThrownBy(() -> acciones.resumen(confirmar.valor())).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void confirmarNoInvalidaElTokenDeCancelacionYCancelarSigueSiendoPosible() {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido confirmar = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
        TokenEmitido cancelar = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));

        acciones.ejecutar(confirmar.valor(), PropositoToken.CONFIRMAR_TURNO);

        assertThat(acciones.resumen(cancelar.valor()).disponible()).isTrue();
        assertThat(acciones.ejecutar(cancelar.valor(), PropositoToken.CANCELAR_TURNO).turno().estado())
                .isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void cancelarFueraDelLimiteDa422SinCambiosNiConsumoYAlLimiteExactoSePermite() {
        Long fuera = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido tFuera = token(PropositoToken.CANCELAR_TURNO, fuera, lunes("10:00"));
        reloj.set(lunes("10:00").minus(Duration.ofMinutes(119)));

        assertThatThrownBy(() -> acciones.ejecutar(tFuera.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(LimiteCancelacionException.class);

        assertThat(estadoDe(fuera)).isEqualTo("RESERVADO");
        assertThat(usado(tFuera)).isFalse();
        assertThat(eventos.stream(HuecoLiberado.class)).isEmpty();
        assertThat(notificaciones(fuera)).isEmpty();

        reloj.set(lunes("10:00").minus(Duration.ofHours(2)));
        assertThat(acciones.ejecutar(tFuera.valor(), PropositoToken.CANCELAR_TURNO).turno().estado())
                .isEqualTo(EstadoTurno.CANCELADO);
    }
}
