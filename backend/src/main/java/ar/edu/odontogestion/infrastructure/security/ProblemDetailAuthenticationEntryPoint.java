package ar.edu.odontogestion.infrastructure.security;

import ar.edu.odontogestion.api.error.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 como {@code problem+json}, sin {@code WWW-Authenticate} (evita el diálogo de HTTP Basic del navegador). */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String DETALLE = "Debe iniciar sesión para acceder a este recurso.";

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ProblemDetails.crear(HttpStatus.UNAUTHORIZED,
                "No autenticado", "no-autenticado", DETALLE, ProblemDetails.instanceDe(request.getRequestURI())));
    }
}
