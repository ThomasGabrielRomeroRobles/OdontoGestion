package ar.edu.odontogestion.application.token;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenAccesoRepository;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.token.TokenVigente;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens de acceso de un solo uso para pacientes sin cuenta (enlaces de email). Solo se persiste el SHA-256.
 *
 * <p>Contrato para los consumidores (C-07, C-09, C-10, C-11):
 * <ul>
 *   <li>{@code GET} (mostrar el enlace): {@link #verificar(String, PropositoToken)}, solo lectura, nunca consume.</li>
 *   <li>{@code POST} (ejecutar la acción): {@link #consumir(String, PropositoToken)} DENTRO de la transacción de
 *       la acción, para que acción y consumo se confirmen o reviertan juntos; si la acción falla, el token sigue
 *       vigente.</li>
 *   <li>Cancelar o cerrar la referencia: {@link #revocar(ReferenciaToken, Optional)} para invalidar enlaces.</li>
 *   <li>Cualquier causa de invalidez lanza {@code TokenInvalidoException}, que la API traduce a un único
 *       {@code 410 Gone} genérico; nunca capturarla para distinguir causas ni registrar el valor del token.</li>
 *   <li>El llamador decide el vencimiento al emitir ({@link #emitir(PropositoToken, ReferenciaToken, Instant)}).</li>
 * </ul>
 */
@Service
public class TokenAccesoService {

    private final TokenAccesoRepository tokens;
    private final GeneradorTokens generador;
    private final ConsultorioActual consultorioActual;
    private final Clock clock;

    public TokenAccesoService(TokenAccesoRepository tokens, GeneradorTokens generador,
            ConsultorioActual consultorioActual, Clock clock) {
        this.tokens = tokens;
        this.generador = generador;
        this.consultorioActual = consultorioActual;
        this.clock = clock;
    }

    @Transactional
    public TokenEmitido emitir(PropositoToken proposito, ReferenciaToken referencia, Instant venceEn) {
        Instant ahora = clock.instant();
        if (!proposito.admite(referencia)) {
            throw new ValidacionDominioException("referencia", "La referencia no corresponde al propósito del token.");
        }
        if (!venceEn.isAfter(ahora)) {
            throw new ValidacionDominioException("venceEn", "El vencimiento debe ser posterior al instante actual.");
        }
        String valor = generador.generar();
        tokens.insertar(consultorioActual.id(), HashToken.sha256(valor), proposito, referencia, venceEn, ahora);
        return new TokenEmitido(valor, proposito, referencia, venceEn);
    }

    /** Solo lectura: nunca consume el token (para el GET que muestra el enlace). */
    @Transactional(readOnly = true)
    public TokenVigente verificar(String valor, PropositoToken esperado) {
        byte[] hash = HashToken.sha256(valor);
        return tokens.buscarVigente(hash, esperado, clock.instant()).orElseThrow(TokenInvalidoException::new);
    }

    /**
     * Consume el token con una única sentencia condicional. Participa de la transacción del llamador
     * (REQUIRED): invocarlo dentro de la transacción de la acción para que se confirmen o reviertan juntos.
     */
    @Transactional
    public ReferenciaToken consumir(String valor, PropositoToken esperado) {
        byte[] hash = HashToken.sha256(valor);
        return tokens.consumir(hash, esperado, clock.instant()).orElseThrow(TokenInvalidoException::new);
    }

    /**
     * Deja vencidos de inmediato los tokens vigentes de la referencia (opcionalmente de un propósito);
     * los ya usados o vencidos no se tocan. Devuelve cuántos se revocaron.
     */
    @Transactional
    public int revocar(ReferenciaToken referencia, Optional<PropositoToken> proposito) {
        return tokens.revocar(referencia, proposito, clock.instant());
    }
}
