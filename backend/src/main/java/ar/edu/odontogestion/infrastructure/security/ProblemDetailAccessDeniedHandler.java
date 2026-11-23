package ar.edu.odontogestion.infrastructure.security;

import ar.edu.odontogestion.api.error.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 como {@code problem+json} con detalle genérico (CSRF inválido o acceso denegado). */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    static final String DETALLE = "No tiene permiso para realizar esta operación.";

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ProblemDetails.crear(HttpStatus.FORBIDDEN,
                "Acceso denegado", "acceso-denegado", DETALLE, ProblemDetails.instanceDe(request.getRequestURI())));
    }
}
