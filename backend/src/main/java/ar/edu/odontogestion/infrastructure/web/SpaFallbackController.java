package ar.edu.odontogestion.infrastructure.web;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fallback de la SPA (C-12, design D1): un {@code GET} a una ruta del frontend ({@code /agenda},
 * {@code /acciones/{token}}, …) devuelve {@code static/index.html} y el router del frontend resuelve la pantalla.
 *
 * <p>Solo coinciden rutas de 1 a 3 segmentos <b>sin punto</b> cuyo primer segmento no es {@code api}: así
 * {@code /api/**} inexistente sigue en {@code 404} {@code ProblemDetail} y {@code /assets/x.js} (exista o no)
 * queda para el handler de recursos estáticos. Los tokens de los enlaces son base64url, sin punto. El token no se
 * lee ni se valida acá: lo maneja el frontend contra la API.
 *
 * <p>Solo existe si el artefacto trae el frontend ({@code ./gradlew bootJar -PwithFrontend}); sin él las rutas
 * siguen en {@code 404}. El {@code index.html} sale con {@code no-cache} para que un build nuevo se vea al
 * recargar; los assets con hash conservan la caché del perfil {@code demo}.
 */
@Controller
@ConditionalOnResource(resources = SpaFallbackController.INDEX)
public class SpaFallbackController {

    static final String INDEX = "classpath:static/index.html";

    private static final String PRIMERO = "{s1:(?!api$)[^.]+}";

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final Resource index;

    public SpaFallbackController(ResourceLoader resourceLoader) {
        this.index = resourceLoader.getResource(INDEX);
    }

    @GetMapping({
        "/",
        "/" + PRIMERO,
        "/" + PRIMERO + "/{s2:[^.]+}",
        "/" + PRIMERO + "/{s2:[^.]+}/{s3:[^.]+}"})
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok().contentType(HTML_UTF8).cacheControl(CacheControl.noCache()).body(index);
    }
}
