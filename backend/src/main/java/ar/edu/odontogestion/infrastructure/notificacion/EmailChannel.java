package ar.edu.odontogestion.infrastructure.notificacion;

import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import ar.edu.odontogestion.application.notificacion.NotificationChannel;
import ar.edu.odontogestion.application.notificacion.ResultadoEnvio;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Canal de email (RN-NO-01) sobre Spring Mail. Credenciales SMTP solo por variables de entorno ({@code MAIL_*});
 * remitente {@code MAIL_FROM}. Texto plano UTF-8. Nunca lanza: cualquier error se devuelve como
 * {@link ResultadoEnvio.Fallido} con un motivo técnico que no incluye el cuerpo (puede tener enlaces con token).
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);
    private static final int MAX_MOTIVO = 300;

    private final JavaMailSender mailSender;
    private final String remitente;

    @Autowired
    public EmailChannel(JavaMailSender mailSender, AppProperties appProperties) {
        this(mailSender, appProperties.mailFrom());
    }

    EmailChannel(JavaMailSender mailSender, String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.EMAIL;
    }

    @Override
    public ResultadoEnvio enviar(NotificacionSaliente notificacion) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(notificacion.destinatario());
            helper.setSubject(notificacion.asunto());
            helper.setText(notificacion.cuerpo(), false);
            mailSender.send(mensaje);
            return ResultadoEnvio.enviado();
        } catch (Exception e) {
            String motivo = motivo(e);
            log.warn("Fallo el envío de email de tipo {}: {}", notificacion.tipo(), motivo);
            return ResultadoEnvio.fallido(motivo);
        }
    }

    private static String motivo(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String detalle = raiz.getMessage() == null ? "" : ": " + raiz.getMessage();
        String motivo = e.getClass().getSimpleName() + (raiz != e ? " (" + raiz.getClass().getSimpleName() + ")" : "")
                + detalle;
        return motivo.length() > MAX_MOTIVO ? motivo.substring(0, MAX_MOTIVO) : motivo;
    }
}
