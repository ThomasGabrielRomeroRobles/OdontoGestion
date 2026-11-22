package ar.edu.odontogestion.infrastructure.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.notificacion.EnlaceRenderizado;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Adaptador de enlaces: revocación de los tokens de un intento que no se entregó. Valores ficticios. */
class EmisorEnlacesConTokensTest {

    private static final Instant VENCE = Instant.parse("2026-11-24T12:30:00Z");
    private static final ReferenciaToken.Turno TURNO = new ReferenciaToken.Turno(900101);

    private final TokenAccesoService tokens = mock(TokenAccesoService.class);
    private final EmisorEnlacesConTokens emisor = new EmisorEnlacesConTokens(tokens,
            new AppProperties("http://localhost:8080/", ZoneId.of("UTC"), 24, 30, 5, List.of("EMAIL"), "a@b.test"));

    private static TokenEmitido emitido(String valor, PropositoToken proposito) {
        return new TokenEmitido(valor, proposito, TURNO, VENCE);
    }

    @Test
    void revocarInvalidaExactamenteLosTokensDeLasUrlsRecibidas() {
        List<EnlaceRenderizado> enlaces = List.of(
                new EnlaceRenderizado(PropositoToken.CONFIRMAR_TURNO, "http://localhost:8080/acciones/tokA", VENCE),
                new EnlaceRenderizado(PropositoToken.ACEPTAR_AVISO, "http://localhost:8080/avisos/tokB", VENCE));

        emisor.revocar(enlaces);

        verify(tokens).revocarToken("tokA");
        verify(tokens).revocarToken("tokB");
    }

    @Test
    void revocarSinEnlacesNoHaceNada() {
        emisor.revocar(List.of());

        verify(tokens, never()).revocarToken(any());
    }

    @Test
    void siFallaLaEmisionAMediasRevocaLosYaEmitidosYRelanza() {
        when(tokens.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, VENCE))
                .thenReturn(emitido("tokA", PropositoToken.CONFIRMAR_TURNO));
        when(tokens.emitir(PropositoToken.CANCELAR_TURNO, TURNO, VENCE)).thenThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> emisor.emitir(TURNO, List.of(
                new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, VENCE),
                new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, VENCE))))
                .isInstanceOf(IllegalStateException.class);

        verify(tokens).revocarToken("tokA");
    }

    @Test
    void emitirDevuelveUrlsConElTokenYNoRevocaNada() {
        when(tokens.emitir(PropositoToken.CONFIRMAR_TURNO, TURNO, VENCE))
                .thenReturn(emitido("tokA", PropositoToken.CONFIRMAR_TURNO));

        List<EnlaceRenderizado> enlaces = emisor.emitir(TURNO,
                List.of(new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, VENCE)));

        assertThat(enlaces).singleElement().extracting(EnlaceRenderizado::url)
                .isEqualTo("http://localhost:8080/acciones/tokA");
        verify(tokens, never()).revocarToken(any());
    }
}
