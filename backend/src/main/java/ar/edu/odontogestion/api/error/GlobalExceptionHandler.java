package ar.edu.odontogestion.api.error;

import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.DomainException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Único punto de traducción de excepciones a respuestas {@code application/problem+json}. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String DETALLE_GENERICO = "Ocurrió un error inesperado. Intente nuevamente más tarde.";
    static final String DETALLE_VALIDACION = "La solicitud contiene datos inválidos.";
    static final String DETALLE_CUERPO_ILEGIBLE = "El cuerpo de la solicitud no es un JSON válido.";

    private static final String BASE_TIPO = "https://odontogestion.example/problemas/";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETALLE_VALIDACION);
        problem.setTitle("Solicitud inválida");
        problem.setProperty("errors", errores);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETALLE_CUERPO_ILEGIBLE);
        problem.setTitle("Solicitud inválida");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail handleNoEncontrado(RecursoNoEncontradoException ex) {
        return dominio(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex);
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail handleConflicto(ConflictoException ex) {
        return dominio(HttpStatus.CONFLICT, "Conflicto", ex);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail handleReglaNegocio(ReglaNegocioException ex) {
        return dominio(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida", ex);
    }

    private static ProblemDetail dominio(HttpStatus status, String titulo, DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(titulo);
        problem.setType(URI.create(BASE_TIPO + ex.codigo()));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleInesperado(Exception ex) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, DETALLE_GENERICO);
        problem.setTitle("Error interno");
        return problem;
    }
}
