package ar.edu.odontogestion.infrastructure.notificacion;

import ar.edu.odontogestion.application.notificacion.EmisorEnlaces;
import ar.edu.odontogestion.application.notificacion.EnlaceRenderizado;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link EmisorEnlaces} sobre {@link TokenAccesoService} (design D8): un token por enlace, emitido en
 * la transacción del procesamiento. URLs {@code APP_BASE_URL/acciones/{token}} o, para {@code ACEPTAR_AVISO},
 * {@code APP_BASE_URL/avisos/{token}} (rutas públicas por token, RN-TK).
 */
@Component
public class EmisorEnlacesConTokens implements EmisorEnlaces {

    private final TokenAccesoService tokens;
    private final String baseUrl;

    public EmisorEnlacesConTokens(TokenAccesoService tokens, AppProperties appProperties) {
        this.tokens = tokens;
        String base = appProperties.baseUrl();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public List<EnlaceRenderizado> emitir(ReferenciaToken referencia, List<EnlaceProgramado> enlaces) {
        return enlaces.stream().map(enlace -> {
            TokenEmitido emitido = tokens.emitir(enlace.proposito(), referencia, enlace.venceEn());
            String ruta = enlace.proposito() == PropositoToken.ACEPTAR_AVISO ? "/avisos/" : "/acciones/";
            return new EnlaceRenderizado(enlace.proposito(), baseUrl + ruta + emitido.valor(), enlace.venceEn());
        }).toList();
    }
}
