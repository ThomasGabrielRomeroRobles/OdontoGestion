package ar.edu.odontogestion.infrastructure.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.notificacion.NotificationChannel;
import ar.edu.odontogestion.application.notificacion.NotificationChannelContractTest;
import ar.edu.odontogestion.application.notificacion.ResultadoEnvio;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Canal de email contra un servidor SMTP de pruebas (GreenMail). Destinatarios ficticios. */
class EmailChannelContractTest extends NotificationChannelContractTest {

    private static final String REMITENTE = "no-reply@consultorio-ficticio.test";

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    private static JavaMailSenderImpl remitenteSmtp() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        sender.setDefaultEncoding("UTF-8");
        Properties props = new Properties();
        props.put("mail.smtp.connectiontimeout", "2000");
        props.put("mail.smtp.timeout", "2000");
        props.put("mail.smtp.writetimeout", "2000");
        sender.setJavaMailProperties(props);
        return sender;
    }

    @Override
    protected NotificationChannel canal() {
        return new EmailChannel(remitenteSmtp(), REMITENTE);
    }

    @Override
    protected void provocarRechazo(String destinatario) {
        GREEN_MAIL.stop();
    }

    @Test
    void elServidorRecibeElMensajeDesdeMailFromConAsuntoYCuerpoEnUtf8() throws Exception {
        NotificacionSaliente saliente = new NotificacionSaliente(TipoNotificacion.RECORDATORIO, DESTINATARIO,
                "Recordatorio: tu turno en Consultorio Ñandú", "Hola:\nTe esperamos el 24/11/2026 a las 10:00. ¡Gracias!\n");

        ResultadoEnvio resultado = new EmailChannel(remitenteSmtp(), REMITENTE).enviar(saliente);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Enviado.class);
        MimeMessage[] recibidos = GREEN_MAIL.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        MimeMessage recibido = recibidos[0];
        assertThat(((InternetAddress) recibido.getAllRecipients()[0]).getAddress()).isEqualTo(DESTINATARIO);
        assertThat(((InternetAddress) recibido.getFrom()[0]).getAddress()).isEqualTo(REMITENTE);
        assertThat(recibido.getSubject()).isEqualTo("Recordatorio: tu turno en Consultorio Ñandú");
        assertThat(GreenMailUtil.getBody(recibido)).isNotBlank();
        assertThat(recibido.getContent().toString()).contains("¡Gracias!").contains("10:00");
    }

    @Test
    void declaraElCanalEmail() {
        assertThat(canal().canal()).isEqualTo(CanalNotificacion.EMAIL);
    }
}
