package ar.edu.odontogestion.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Parte HTTP del login con controller propio. Spring Security 6 no hace estos pasos solo cuando el login no
 * pasa por sus filtros: rotar el id de sesión (anti fijación, RN-AU-06), renovar el token CSRF y guardar
 * explícitamente el {@code SecurityContext} en la sesión.
 */
@Component
public class SesionHttp {

    private final SessionAuthenticationStrategy estrategia;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SesionHttp(CsrfTokenRepository csrfTokenRepository) {
        this.estrategia = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    public void iniciar(Authentication autenticacion, HttpServletRequest request, HttpServletResponse response) {
        estrategia.onAuthentication(autenticacion, request, response);
        // El token nuevo es diferido: sin forzarlo, la respuesta solo traería la cookie vieja borrada.
        CsrfToken tokenNuevo = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (tokenNuevo != null) {
            tokenNuevo.getToken();
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        contextRepository.saveContext(contexto, request, response);
    }

    /** Rota el id de sesión conservando la sesión (p. ej. tras cambiar la contraseña). */
    public void rotar(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
    }
}
