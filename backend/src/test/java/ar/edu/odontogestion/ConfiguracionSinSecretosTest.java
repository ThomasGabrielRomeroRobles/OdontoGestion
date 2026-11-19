package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** Inspección de los application*.yml versionados: ninguna contraseña con valor literal. */
class ConfiguracionSinSecretosTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("^[$][{][^}]+[}]$");

    @Test
    void lasContrasenasDeLosYmlVersionadosSoloReferencianVariablesDeEntorno() throws Exception {
        Resource[] ymls = new PathMatchingResourcePatternResolver().getResources("classpath*:application*.yml");

        assertThat(ymls).isNotEmpty();
        for (Resource yml : ymls) {
            String contenido = new String(yml.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(contenidoConContrasenaLiteral(contenido))
                    .as("claves de contraseña con valor literal en %s", yml.getFilename())
                    .isEmpty();
        }
    }

    @Test
    void detectaContrasenaLiteral() {
        String yml = "spring:\n  datasource:\n    password: hunter2\n  mail:\n    password: ${MAIL_PASSWORD:}\n";

        assertThat(contenidoConContrasenaLiteral(yml)).containsExactly("spring.datasource.password");
    }

    @Test
    void aceptaPlaceholderConYSinDefecto() {
        String yml = "a:\n  password: ${X}\n  clave-password: ${Y:}\n  otra: valor\n";

        assertThat(contenidoConContrasenaLiteral(yml)).isEmpty();
    }

    private static List<String> contenidoConContrasenaLiteral(String yml) {
        List<String> infractoras = new ArrayList<>();
        Object raiz = new Yaml(new LoaderOptions()).load(new ByteArrayInputStream(yml.getBytes(StandardCharsets.UTF_8)));
        recorrer("", raiz, infractoras);
        return infractoras;
    }

    private static void recorrer(String prefijo, Object nodo, List<String> infractoras) {
        if (nodo instanceof Map<?, ?> mapa) {
            mapa.forEach((k, v) -> recorrer(prefijo.isEmpty() ? String.valueOf(k) : prefijo + "." + k, v, infractoras));
        } else if (nodo != null && prefijo.toLowerCase().contains("password")
                && !PLACEHOLDER.matcher(String.valueOf(nodo).trim()).matches()) {
            infractoras.add(prefijo);
        }
    }
}
