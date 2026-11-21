package ar.edu.odontogestion.api.error;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Fábrica compartida de {@link ProblemDetail}: usada por el handler global y por la capa de seguridad. */
public final class ProblemDetails {

    static final String BASE_TIPO = "https://odontogestion.example/problemas/";

    private ProblemDetails() {
    }

    public static ProblemDetail crear(HttpStatus status, String titulo, String codigoTipo, String detalle) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(titulo);
        problem.setType(URI.create(BASE_TIPO + codigoTipo));
        return problem;
    }

    public static ProblemDetail crear(HttpStatus status, String titulo, String codigoTipo, String detalle,
            String instance) {
        ProblemDetail problem = crear(status, titulo, codigoTipo, detalle);
        problem.setInstance(URI.create(instance));
        return problem;
    }
}
