package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Los logs de la aplicación no registran las URLs de las solicitudes (C-09 D8): la ruta {@code /acciones/{token}}
 * lleva el token, así que ningún perfil habilita el log de pedidos web de Spring, el access log de Tomcat ni el
 * detalle de los pedidos.
 */
class ConfiguracionLogsTest {

    private static Properties cargar(String recurso) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(recurso));
        Properties props = yaml.getObject();
        assertThat(props).as(recurso).isNotNull();
        return props;
    }

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-dev.yml", "application-demo.yml"})
    void ningunPerfilRegistraLosPedidosWebNiElAccessLog(String recurso) {
        Properties props = cargar(recurso);

        for (String clave : props.stringPropertyNames()) {
            if (clave.startsWith("logging.level.org.springframework.web")
                    || clave.startsWith("logging.level.org.apache.catalina")
                    || clave.startsWith("logging.level.org.apache.tomcat")
                    || clave.startsWith("logging.level.org.springframework.security")) {
                assertThat(props.getProperty(clave).toUpperCase()).as("%s en %s", clave, recurso)
                        .isNotIn("DEBUG", "TRACE");
            }
        }
        assertThat(props.getProperty("server.tomcat.accesslog.enabled", "false")).as(recurso)
                .isNotEqualToIgnoringCase("true");
        assertThat(props.getProperty("spring.mvc.log-request-details", "false")).as(recurso)
                .isNotEqualToIgnoringCase("true");
        assertThat(props.getProperty("spring.http.log-request-details", "false")).as(recurso)
                .isNotEqualToIgnoringCase("true");
        assertThat(props.getProperty("spring.jpa.show-sql", "false")).as(recurso).isNotEqualToIgnoringCase("true");
    }

    @Test
    void elNivelRaizNoEsDebugNiTrace() {
        for (String recurso : List.of("application.yml", "application-dev.yml", "application-demo.yml")) {
            assertThat(cargar(recurso).getProperty("logging.level.root", "INFO").toUpperCase()).as(recurso)
                    .isNotIn("DEBUG", "TRACE");
        }
    }
}
