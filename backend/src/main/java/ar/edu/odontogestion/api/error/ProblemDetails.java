package ar.edu.odontogestion.api.error;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Fábrica compartida de {@link ProblemDetail}: usada por el handler global y por la capa de seguridad. */
public final class ProblemDetails {

    static final String BASE_TIPO = "https://odontogestion.example/problemas/";

    /**
     * {@code instance} de todo error de una ruta por token (C-09 D8): la ruta de la solicitud lleva el valor del
     * token ({@code /api/v1/acciones/{token}}), así que no puede viajar en el cuerpo.
     */
    public static final String INSTANCE_RUTA_POR_TOKEN = "urn:odontogestion:ruta-por-token";

    private static final List<String> PREFIJOS_POR_TOKEN = List.of("/api/v1/acciones/", "/api/v1/avisos/");

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

    /** {@code true} si la ruta lleva un token de acceso del paciente ({@code /acciones/**}, {@code /avisos/**}). */
    public static boolean esRutaPorToken(String uri) {
        return uri != null && PREFIJOS_POR_TOKEN.stream().anyMatch(uri::startsWith);
    }

    /** {@code instance} a publicar para la ruta {@code uri}: la constante si lleva token, si no la propia ruta. */
    public static String instanceDe(String uri) {
        return esRutaPorToken(uri) ? INSTANCE_RUTA_POR_TOKEN : uri;
    }

    /**
     * Deja el error de una ruta por token sin el token: {@code instance} constante (salvo que ya sea una URN
     * constante, como la del {@code 410}) y, si algún texto del error repite el segmento del token (p. ej. el
     * {@code 404} de Spring nombra la ruta), lo reemplaza.
     */
    public static void sinToken(ProblemDetail problem, String uri) {
        if (!esRutaPorToken(uri)) {
            return;
        }
        URI actual = problem.getInstance();
        if (actual == null || !"urn".equals(actual.getScheme())) {
            problem.setInstance(URI.create(INSTANCE_RUTA_POR_TOKEN));
        }
        String token = segmentoToken(uri);
        if (!token.isEmpty()) {
            if (problem.getDetail() != null) {
                problem.setDetail(problem.getDetail().replace(token, "[enlace]"));
            }
            if (problem.getTitle() != null) {
                problem.setTitle(problem.getTitle().replace(token, "[enlace]"));
            }
        }
    }

    private static String segmentoToken(String uri) {
        for (String prefijo : PREFIJOS_POR_TOKEN) {
            if (uri.startsWith(prefijo)) {
                String resto = uri.substring(prefijo.length());
                int fin = resto.indexOf('/');
                return fin < 0 ? resto : resto.substring(0, fin);
            }
        }
        return "";
    }
}
