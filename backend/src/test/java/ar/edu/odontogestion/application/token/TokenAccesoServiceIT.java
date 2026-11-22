package ar.edu.odontogestion.application.token;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.token.TokenVigente;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(FixedClockTestConfig.class)
class TokenAccesoServiceIT extends AbstractIntegrationTest {

    private static final ReferenciaToken.Turno TURNO = new ReferenciaToken.Turno(900101);

    @Autowired
    private TokenAccesoService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private MutableClock reloj;

    @AfterEach
    void limpiar() {
        reloj.set(AHORA);
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private static java.time.Instant instante(Object timestamptz) {
        return ((java.sql.Timestamp) timestamptz).toInstant();
    }

    private int filas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000",
                Integer.class);
    }

    // ---- 3.1 emitir ----

    @Test
    void emitirPersisteSoloElHashConConsultorioYTimestampsDelReloj() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plus(Duration.ofHours(24)));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM token_acceso WHERE turno_id = 900101");
        assertThat((byte[]) fila.get("token_hash")).isEqualTo(HashToken.sha256(emitido.valor())).hasSize(32);
        assertThat(((Number) fila.get("consultorio_id")).longValue()).isEqualTo(consultorioActual.id());
        assertThat(fila.get("proposito")).isEqualTo("CONFIRMAR_TURNO");
        assertThat(fila.get("usado_en")).isNull();
        assertThat(instante(fila.get("creado_en"))).isEqualTo(AHORA);
        assertThat(instante(fila.get("actualizado_en"))).isEqualTo(AHORA);
        assertThat(instante(fila.get("vence_en"))).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        String filaComoTexto = jdbc.queryForObject(
                "SELECT t::text FROM token_acceso t WHERE turno_id = 900101", String.class);
        assertThat(filaComoTexto).doesNotContain(emitido.valor());
        assertThat(emitido.valor()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(emitido.venceEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    void emitirParaUnAvisoGuardaSoloLaReferenciaAlAviso() {
        service.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(900007), AHORA.plusSeconds(60));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM token_acceso WHERE aviso_id = 900007");
        assertThat(fila.get("turno_id")).isNull();
        assertThat(fila.get("entrada_lista_espera_id")).isNull();
    }

    @Test
    void vencimientoIgualOAnteriorAlActualEsRechazadoSinFilas() {
        assertThatThrownBy(() -> service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.minusSeconds(1)))
                .isInstanceOf(ValidacionDominioException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void propositoIncoherenteConLaReferenciaEsRechazadoEnJavaSinFilas() {
        assertThatThrownBy(() -> service.emitir(PropositoToken.ACEPTAR_AVISO, TURNO, AHORA.plusSeconds(60)))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> service.emitir(PropositoToken.CANCELAR_TURNO,
                new ReferenciaToken.EntradaListaEspera(900009), AHORA.plusSeconds(60)))
                .isInstanceOf(ValidacionDominioException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void emisionesSucesivasProducenValoresYHashesDistintos() {
        TokenEmitido a = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(60));
        TokenEmitido b = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(60));
        assertThat(a.valor()).isNotEqualTo(b.valor());
        assertThat(filas()).isEqualTo(2);
    }

    private ReferenciaToken consumirEnTx(String valor, PropositoToken proposito) {
        return tx.execute(status -> service.consumir(valor, proposito));
    }

    private java.time.Instant usadoEn(long turno) {
        java.sql.Timestamp t = jdbc.queryForObject(
                "SELECT usado_en FROM token_acceso WHERE turno_id = ?", java.sql.Timestamp.class, turno);
        return t == null ? null : t.toInstant();
    }

    private java.time.Instant actualizadoEn(String valor) {
        return jdbc.queryForObject("SELECT actualizado_en FROM token_acceso WHERE token_hash = ?",
                java.sql.Timestamp.class, (Object) HashToken.sha256(valor)).toInstant();
    }

    // ---- 3.3 verificar ----

    @Test
    void verificarDeUnTokenVigenteDevuelveReferenciaYVencimiento() {
        Instant vence = AHORA.plus(Duration.ofHours(3));
        TokenEmitido emitido = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, vence);

        TokenVigente vigente = service.verificar(emitido.valor(), PropositoToken.CANCELAR_TURNO);

        assertThat(vigente.referencia()).isEqualTo(TURNO);
        assertThat(vigente.venceEn()).isEqualTo(vence);
    }

    @Test
    void verificarRepetidamenteNoConsumeNiModificaLaFila() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        reloj.advance(Duration.ofMinutes(1));

        for (int i = 0; i < 3; i++) {
            assertThat(service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO).referencia())
                    .isEqualTo(TURNO);
        }

        assertThat(usadoEn(900101)).isNull();
        assertThat(actualizadoEn(emitido.valor())).isEqualTo(AHORA);
        assertThat(consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO)).isEqualTo(TURNO);
    }

    @Test
    void verificarConPropositoEquivocadoInexistenteOMalFormadoEsTokenInvalido() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));

        assertThatThrownBy(() -> service.verificar(emitido.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> service.verificar("B".repeat(43), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> service.verificar("corto", PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> service.verificar(null, PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // ---- 3.4 consumir ----

    @Test
    void primerConsumoDevuelveLaReferenciaYMarcaUsadoEnConElInstanteActual() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        reloj.advance(Duration.ofMinutes(5));

        ReferenciaToken ref = consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO);

        assertThat(ref).isEqualTo(TURNO);
        assertThat(usadoEn(900101)).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(actualizadoEn(emitido.valor())).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void consumoDeUnTokenDeAvisoDevuelveReferenciaAAviso() {
        TokenEmitido emitido = service.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(900007),
                AHORA.plusSeconds(3600));

        assertThat(consumirEnTx(emitido.valor(), PropositoToken.ACEPTAR_AVISO))
                .isEqualTo(new ReferenciaToken.Aviso(900007));
    }

    @Test
    void segundoConsumoEsRechazadoYUsadoEnNoCambia() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO);
        java.time.Instant primero = usadoEn(900101);
        reloj.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(usadoEn(900101)).isEqualTo(primero);
        assertThatThrownBy(() -> service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void propositoEquivocadoNoConsumeYElOriginalSigueConsumible() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));

        assertThatThrownBy(() -> consumirEnTx(emitido.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(usadoEn(900101)).isNull();
        assertThat(consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO)).isEqualTo(TURNO);
    }

    // ---- 3.5 vencimiento segun el reloj de la aplicacion ----

    @Test
    void tokenVencidoSegunElRelojEsInvalidoParaVerificarYConsumir() {
        Instant vence = Instant.parse("2026-11-20T12:30:00Z");
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, vence);

        reloj.set(Instant.parse("2026-11-20T12:29:59Z"));
        assertThat(service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO).referencia()).isEqualTo(TURNO);

        reloj.set(vence);
        assertThatThrownBy(() -> service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);

        reloj.set(Instant.parse("2026-11-20T12:31:00Z"));
        assertThatThrownBy(() -> service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(usadoEn(900101)).isNull();
    }

    // ---- 3.6 la transaccion del llamador se revierte ----

    @Test
    void siLaAccionFallaYSeRevierteElTokenSigueSinUsarYSePuedeConsumirDespues() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            assertThat(service.consumir(emitido.valor(), PropositoToken.CONFIRMAR_TURNO)).isEqualTo(TURNO);
            throw new IllegalStateException("la accion fallo");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(usadoEn(900101)).isNull();
        assertThat(consumirEnTx(emitido.valor(), PropositoToken.CONFIRMAR_TURNO)).isEqualTo(TURNO);
        assertThat(usadoEn(900101)).isNotNull();
    }

    // ---- 3.8 revocar ----

    @Test
    void revocarLosTokensDeUnTurnoDejaInvalidosSoloEsosYNoToca() {
        TokenEmitido confirmar = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        TokenEmitido cancelar = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        TokenEmitido otroTurno = service.emitir(PropositoToken.CONFIRMAR_TURNO,
                new ReferenciaToken.Turno(900102), AHORA.plusSeconds(3600));

        int revocados = service.revocar(TURNO, Optional.empty());

        assertThat(revocados).isEqualTo(2);
        assertThatThrownBy(() -> service.verificar(confirmar.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> consumirEnTx(cancelar.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(service.verificar(otroTurno.valor(), PropositoToken.CONFIRMAR_TURNO).referencia())
                .isEqualTo(new ReferenciaToken.Turno(900102));
    }

    @Test
    void revocarNoAfectaTokensYaUsadosNiYaVencidos() {
        TokenEmitido usado = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        consumirEnTx(usado.valor(), PropositoToken.CONFIRMAR_TURNO);
        Instant usadoAntes = usadoEn(900101);
        reloj.advance(Duration.ofMinutes(5));

        int revocados = service.revocar(TURNO, Optional.empty());

        assertThat(revocados).isZero();
        assertThat(usadoEn(900101)).isEqualTo(usadoAntes);
    }

    @Test
    void revocarConPropositoSoloAfectaEsePropositoYSePuedeRevocarUnAviso() {
        TokenEmitido confirmar = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        TokenEmitido cancelar = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        TokenEmitido aviso = service.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(900007),
                AHORA.plusSeconds(3600));

        assertThat(service.revocar(TURNO, Optional.of(PropositoToken.CANCELAR_TURNO))).isEqualTo(1);

        assertThat(service.verificar(confirmar.valor(), PropositoToken.CONFIRMAR_TURNO).referencia()).isEqualTo(TURNO);
        assertThatThrownBy(() -> service.verificar(cancelar.valor(), PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(service.revocar(new ReferenciaToken.Aviso(900007), Optional.empty())).isEqualTo(1);
        assertThatThrownBy(() -> service.verificar(aviso.valor(), PropositoToken.ACEPTAR_AVISO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void revocarUnTokenPorValorInvalidaSoloEseYNoLosDemasDeLaMismaReferenciaYPropositoNiAlRepetirse() {
        TokenEmitido objetivo = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        TokenEmitido mismoTurnoYPropositoEntregado = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO,
                AHORA.plusSeconds(3600));
        TokenEmitido otroProposito = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, AHORA.plusSeconds(3600));

        assertThat(service.revocarToken(objetivo.valor())).isEqualTo(1);

        assertThatThrownBy(() -> service.verificar(objetivo.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(service.verificar(mismoTurnoYPropositoEntregado.valor(), PropositoToken.CONFIRMAR_TURNO)
                .referencia()).isEqualTo(TURNO);
        assertThat(service.verificar(otroProposito.valor(), PropositoToken.CANCELAR_TURNO).referencia())
                .isEqualTo(TURNO);
        assertThat(service.revocarToken(objetivo.valor())).isZero();
    }

    @Test
    void revocarUnTokenPorValorNoTocaUnoYaUsadoNiUnoInexistente() {
        TokenEmitido usado = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        consumirEnTx(usado.valor(), PropositoToken.CONFIRMAR_TURNO);
        Instant usadoAntes = usadoEn(900101);

        assertThat(service.revocarToken(usado.valor())).isZero();
        assertThat(service.revocarToken("B".repeat(43))).isZero();
        assertThat(usadoEn(900101)).isEqualTo(usadoAntes);
    }

    // ---- 3.10 causas indistinguibles ----

    @Test
    void todasLasCausasDeInvalidezProducenLaMismaExcepcionConElMismoMensaje() {
        TokenEmitido usado = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(3600));
        consumirEnTx(usado.valor(), PropositoToken.CONFIRMAR_TURNO);
        TokenEmitido vencido = service.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(900102),
                AHORA.plusSeconds(60));
        TokenEmitido revocado = service.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(900103),
                AHORA.plusSeconds(7200));
        service.revocar(new ReferenciaToken.Turno(900103), Optional.empty());
        TokenEmitido otroProposito = service.emitir(PropositoToken.CANCELAR_TURNO, new ReferenciaToken.Turno(900104),
                AHORA.plusSeconds(7200));
        reloj.advance(Duration.ofMinutes(2));

        List<String> mensajes = new ArrayList<>();
        List<Class<?>> clases = new ArrayList<>();
        Map<String, PropositoToken> casos = new LinkedHashMap<>();
        casos.put("B".repeat(43), PropositoToken.CONFIRMAR_TURNO); // inexistente
        casos.put("mal formado!", PropositoToken.CONFIRMAR_TURNO);
        casos.put(vencido.valor(), PropositoToken.CONFIRMAR_TURNO);
        casos.put(usado.valor(), PropositoToken.CONFIRMAR_TURNO);
        casos.put(revocado.valor(), PropositoToken.CONFIRMAR_TURNO);
        casos.put(otroProposito.valor(), PropositoToken.CONFIRMAR_TURNO);
        casos.forEach((valor, proposito) -> {
            Throwable t = catchThrowable(() -> consumirEnTx(valor, proposito));
            assertThat(t).isNotNull();
            clases.add(t.getClass());
            mensajes.add(t.getMessage());
        });

        assertThat(clases).hasSize(6).containsOnly(TokenInvalidoException.class);
        assertThat(mensajes).hasSize(6).containsOnly("El enlace no es válido o ya fue utilizado.");
    }
}
