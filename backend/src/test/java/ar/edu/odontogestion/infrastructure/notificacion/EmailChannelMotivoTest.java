package ar.edu.odontogestion.infrastructure.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import ar.edu.odontogestion.application.notificacion.ResultadoEnvio;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** El motivo del fallo (log y {@code notificacion.error}) no debe filtrar el email del paciente. */
class EmailChannelMotivoTest {

    private static final String PACIENTE = "paciente.ficticio@ejemplo.test";
    private static final NotificacionSaliente SALIENTE = new NotificacionSaliente(TipoNotificacion.RECORDATORIO,
            PACIENTE, "Asunto", "Cuerpo");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EmailChannel.class);

    @BeforeEach
    void capturarLog() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void liberarLog() {
        logger.detachAppender(appender);
    }

    private static EmailChannel canalQueFallaCon(Exception e) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(e).when(sender).send(any(MimeMessage.class));
        return new EmailChannel(sender, "no-reply@consultorio-ficticio.test");
    }

    private static String motivo(ResultadoEnvio r) {
        return ((ResultadoEnvio.Fallido) r).motivo();
    }

    private String logCompleto() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    void elMotivoGuardadoEnmascaraLosEmailsDelMensajeDeLaExcepcion() throws Exception {
        Exception smtp = new MailSendException("Mail server rejected recipient <" + PACIENTE + ">: 550 5.1.1",
                new jakarta.mail.SendFailedException("Invalid Addresses " + PACIENTE + " y otro.x@dominio.test"));

        String motivo = motivo(canalQueFallaCon(smtp).enviar(SALIENTE));

        assertThat(motivo).doesNotContain(PACIENTE).doesNotContain("otro.x@dominio.test").contains("***@***")
                .startsWith("MailSendException (SendFailedException)");
    }

    @Test
    void unMotivoSinEmailsConservaElDetalleTecnico() throws Exception {
        Exception smtp = new MailSendException("Connection refused: connect");

        String motivo = motivo(canalQueFallaCon(smtp).enviar(SALIENTE));

        assertThat(motivo).isEqualTo("MailSendException: Connection refused: connect");
    }

    @Test
    void elMotivoTieneTopeDeLongitudDespuesDeEnmascarar() throws Exception {
        Exception smtp = new MailSendException("x".repeat(1000) + " " + PACIENTE);

        String motivo = motivo(canalQueFallaCon(smtp).enviar(SALIENTE));

        assertThat(motivo).hasSizeLessThanOrEqualTo(300).doesNotContain("@ejemplo");
    }

    @Test
    void elLogRegistraSoloLaClaseDeLaExcepcionSinElMensajeNiElEmail() throws Exception {
        Exception smtp = new MailSendException("rechazado " + PACIENTE,
                new jakarta.mail.SendFailedException("Invalid Addresses " + PACIENTE));

        canalQueFallaCon(smtp).enviar(SALIENTE);

        assertThat(appender.list).hasSize(1);
        assertThat(logCompleto()).contains("RECORDATORIO").contains("MailSendException").contains("SendFailedException")
                .doesNotContain(PACIENTE).doesNotContain("rechazado").doesNotContain("Invalid Addresses");
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    }
}
