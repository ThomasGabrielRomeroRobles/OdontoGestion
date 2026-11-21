package ar.edu.odontogestion.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.server.Session;

/** Verifica el binding real de application.yml (sin levantar la app ni esperar tiempo real). */
class SessionConfigIT {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServerProperties.class)
    static class PropiedadesDelServidor {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropiedadesDelServidor.class);
    }

    @Test
    void elTimeoutPorDefectoEsDeTreintaMinutos() {
        runner().run(ctx -> assertThat(ctx.getBean(ServerProperties.class).getServlet().getSession().getTimeout())
                .isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void sessionTimeoutMinSobrescribeElTimeoutEfectivo() {
        runner().withPropertyValues("SESSION_TIMEOUT_MIN=10").run(ctx ->
                assertThat(ctx.getBean(ServerProperties.class).getServlet().getSession().getTimeout())
                        .isEqualTo(Duration.ofMinutes(10)));
    }

    @Test
    void laPropertyDeSpringTambienSobrescribeElTimeout() {
        runner().withPropertyValues("server.servlet.session.timeout=10m").run(ctx ->
                assertThat(ctx.getBean(ServerProperties.class).getServlet().getSession().getTimeout())
                        .isEqualTo(Duration.ofMinutes(10)));
    }

    @Test
    void laCookieEsHttpOnlySameSiteLaxYNoSecureAlComienzo() {
        runner().run(ctx -> {
            var cookie = ctx.getBean(ServerProperties.class).getServlet().getSession().getCookie();
            assertThat(cookie.getHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
            assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.getName()).isEqualTo("JSESSIONID");
        });
    }

    @Test
    void sessionCookieSecureTrueActivaElAtributoSecure() {
        runner().withPropertyValues("SESSION_COOKIE_SECURE=true").run(ctx ->
                assertThat(ctx.getBean(ServerProperties.class).getServlet().getSession().getCookie().getSecure())
                        .isTrue());
    }

    @Test
    void elIdDeSesionSoloViajaPorCookieNuncaEnLaUrl() {
        runner().run(ctx -> assertThat(ctx.getBean(ServerProperties.class).getServlet().getSession()
                .getTrackingModes()).containsExactly(Session.SessionTrackingMode.COOKIE));
    }
}
