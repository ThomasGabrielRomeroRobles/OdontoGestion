package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * El transporte SMTP real (Gmail, MAIL_*) no debe degradarse a texto plano ni aceptar un certificado ajeno:
 * STARTTLS obligatorio y verificación de la identidad del servidor (hallazgo de seguridad de C-03).
 * Se lee el {@code application.yml} real; GreenMail no pasa por acá (sus tests arman su propio sender).
 */
class MailSmtpConfigTest {

    private static Properties aplicacion() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    @Test
    void starttlsEsObligatorio() {
        Properties props = aplicacion();

        assertThat(props.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("true");
    }

    @Test
    void seVerificaLaIdentidadDelServidor() {
        assertThat(aplicacion().getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity"))
                .isEqualTo("true");
    }
}
