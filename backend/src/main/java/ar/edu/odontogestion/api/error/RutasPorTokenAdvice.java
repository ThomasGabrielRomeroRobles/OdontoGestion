package ar.edu.odontogestion.api.error;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Saneamiento final de los errores de las rutas por token (C-09 D8): Spring completa {@code instance} con la ruta de
 * la solicitud cuando el {@link ProblemDetail} no la trae, y esa ruta contiene el valor del token. Este advice corre
 * justo antes de escribir el cuerpo y deja {@code instance} constante en {@code /api/v1/acciones/**} y
 * {@code /api/v1/avisos/**}; el resto de las rutas no se toca.
 */
@RestControllerAdvice
public class RutasPorTokenAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            ProblemDetails.sinToken(problem, request.getURI().getRawPath());
        }
        return body;
    }
}
