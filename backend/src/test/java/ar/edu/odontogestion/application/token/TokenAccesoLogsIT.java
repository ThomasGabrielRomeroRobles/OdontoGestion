package ar.edu.odontogestion.application.token;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
@Import(FixedClockTestConfig.class)
class TokenAccesoLogsIT extends AbstractIntegrationTest {

    private static final List<String> LOGGERS = List.of("ar.edu.odontogestion", "org.springframework.jdbc",
            "org.springframework.jdbc.core", "org.springframework.transaction", "org.hibernate.SQL");

    @Autowired
    private TokenAccesoService service;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoggingSystem logging;

    @BeforeEach
    void logsDetallados() {
        LOGGERS.forEach(l -> logging.setLogLevel(l, LogLevel.TRACE));
    }

    @AfterEach
    void restaurar() {
        LOGGERS.forEach(l -> logging.setLogLevel(l, null));
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    @Test
    void emitirVerificarYConsumirNoDejanElValorNiSuHashEnLosLogs(CapturedOutput salida) {
        TokenEmitido emitido = service.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(900301),
                AHORA.plusSeconds(3600));
        service.verificar(emitido.valor(), PropositoToken.CONFIRMAR_TURNO);
        tx.executeWithoutResult(s -> service.consumir(emitido.valor(), PropositoToken.CONFIRMAR_TURNO));
        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> service.consumir(emitido.valor(), PropositoToken.CONFIRMAR_TURNO)))
                .isInstanceOf(TokenInvalidoException.class);

        byte[] hash = HashToken.sha256(emitido.valor());
        assertThat(salida.getAll())
                .as("el logging detallado debe estar activo para que la prueba sea significativa")
                .contains("token_acceso");
        assertThat(salida.getAll()).doesNotContain(emitido.valor())
                .doesNotContain(HexFormat.of().formatHex(hash))
                .doesNotContain(Base64.getEncoder().encodeToString(hash))
                .doesNotContain(Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
    }

    @Test
    void unValorInvalidoTampocoSeRegistra(CapturedOutput salida) {
        String inexistente = "Zz9_-" + "q".repeat(38);
        assertThatThrownBy(() -> service.verificar(inexistente, PropositoToken.CANCELAR_TURNO))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> service.consumir(inexistente, PropositoToken.CANCELAR_TURNO)))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(salida.getAll()).doesNotContain(inexistente);
    }
}
