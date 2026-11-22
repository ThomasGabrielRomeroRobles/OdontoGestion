package ar.edu.odontogestion.application.token;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.token.TokenIdentificado;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import ar.edu.odontogestion.support.TurnosFicticios;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Identificación de un token entre varios propósitos admitidos, sin consumirlo (C-09 D6). */
@Import(FixedClockTestConfig.class)
@TurnosFicticios
class TokenIdentificarIT extends AbstractIntegrationTest {

    private static final ReferenciaToken.Turno TURNO = new ReferenciaToken.Turno(900101);
    private static final Set<PropositoToken> DE_ACCION = EnumSet.of(PropositoToken.CONFIRMAR_SOLICITUD,
            PropositoToken.CONFIRMAR_TURNO, PropositoToken.CANCELAR_TURNO);

    @Autowired
    private TokenAccesoService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private MutableClock reloj;

    @AfterEach
    void limpiar() {
        reloj.set(AHORA);
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private ReferenciaToken consumir(String valor, PropositoToken proposito) {
        return tx.execute(s -> service.consumir(valor, proposito));
    }

    private boolean usado(String valor) {
        byte[] hash = HashToken.sha256(valor);
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE token_hash = ?",
                Boolean.class, hash);
    }

    @Test
    void identificaUnTokenVigenteConSuPropositoReferenciaYVencimientoSinConsumirlo() {
        TokenEmitido emitido = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, AHORA.plus(Duration.ofHours(3)));

        TokenIdentificado identificado = service.identificar(emitido.valor(), DE_ACCION);

        assertThat(identificado).isEqualTo(new TokenIdentificado(PropositoToken.CANCELAR_TURNO, TURNO,
                AHORA.plus(Duration.ofHours(3))));
        assertThat(usado(emitido.valor())).isFalse();
        // Identificar varias veces no lo gasta: el POST posterior todavía lo consume.
        service.identificar(emitido.valor(), DE_ACCION);
        service.identificar(emitido.valor(), DE_ACCION);
        assertThat(consumir(emitido.valor(), PropositoToken.CANCELAR_TURNO))
                .isEqualTo(TURNO);
    }

    @Test
    void identificaCadaUnoDeLosPropositosDeAccion() {
        for (PropositoToken proposito : DE_ACCION) {
            TokenEmitido emitido = service.emitir(proposito, TURNO, AHORA.plusSeconds(600));

            assertThat(service.identificar(emitido.valor(), DE_ACCION).proposito()).isEqualTo(proposito);
        }
    }

    @Test
    void unPropositoFueraDelConjuntoSeRechazaYElTokenSigueConsumible() {
        TokenEmitido aviso = service.emitir(PropositoToken.ACEPTAR_AVISO, new ReferenciaToken.Aviso(900007),
                AHORA.plusSeconds(600));
        TokenEmitido confirmar = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(600));

        assertThatThrownBy(() -> service.identificar(aviso.valor(), DE_ACCION))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> service.identificar(confirmar.valor(), EnumSet.of(PropositoToken.CANCELAR_TURNO)))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(consumir(aviso.valor(), PropositoToken.ACEPTAR_AVISO))
                .isEqualTo(new ReferenciaToken.Aviso(900007));
        assertThat(consumir(confirmar.valor(), PropositoToken.CONFIRMAR_TURNO))
                .isEqualTo(TURNO);
    }

    @Test
    void usadoVencidoEInexistenteSeRechazanConElMismoMensaje() {
        TokenEmitido usado = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(600));
        tx.execute(s -> service.consumir(usado.valor(), PropositoToken.CONFIRMAR_TURNO));
        TokenEmitido porVencer = service.emitir(PropositoToken.CANCELAR_TURNO, TURNO, AHORA.plusSeconds(60));
        reloj.advance(Duration.ofSeconds(61));

        Throwable deUsado = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.identificar(usado.valor(), DE_ACCION));
        Throwable deVencido = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.identificar(porVencer.valor(), DE_ACCION));
        Throwable deInexistente = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.identificar("no-existe-" + "x".repeat(30), DE_ACCION));

        assertThat(deUsado).isInstanceOf(TokenInvalidoException.class);
        assertThat(deVencido).isInstanceOf(TokenInvalidoException.class);
        assertThat(deInexistente).isInstanceOf(TokenInvalidoException.class);
        assertThat(deUsado.getMessage()).isEqualTo(deVencido.getMessage()).isEqualTo(deInexistente.getMessage());
    }

    @Test
    void unTokenRevocadoSeRechaza() {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, AHORA.plusSeconds(600));
        service.revocar(TURNO, java.util.Optional.empty());

        assertThatThrownBy(() -> service.identificar(emitido.valor(), DE_ACCION))
                .isInstanceOf(TokenInvalidoException.class);
    }
}
