package ar.edu.odontogestion.application.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitadorSolicitudesPublicasTest {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    private MutableClock reloj;
    private LimitadorSolicitudesPublicas limitador;

    @BeforeEach
    void preparar() {
        reloj = new MutableClock(T0);
        limitador = new LimitadorSolicitudesPublicas(reloj, 5, 10_000);
    }

    private void cincoDeLaIp(String ip) {
        for (int i = 0; i < 5; i++) {
            limitador.registrarIp(ip);
            reloj.advance(Duration.ofMinutes(1));
        }
    }

    // --- 3.1 ventana por IP ---

    @Test
    void laSextaSolicitudDeLaMismaIpSeRechazaConLaEsperaHastaQueSalgaLaMasAntigua() {
        cincoDeLaIp("10.0.0.1"); // registros en T0, +1, +2, +3, +4 min; ahora = +5 min

        assertThatThrownBy(() -> limitador.registrarIp("10.0.0.1"))
                .isInstanceOfSatisfying(DemasiadasSolicitudesException.class,
                        e -> assertThat(e.retryAfterSegundos()).isEqualTo(Duration.ofMinutes(55).toSeconds()));
    }

    @Test
    void otraIpNoSeVeAfectada() {
        cincoDeLaIp("10.0.0.1");

        assertThatCode(() -> limitador.registrarIp("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void laVentanaSeRenuevaAlSalirLaMasAntigua() {
        cincoDeLaIp("10.0.0.1"); // ahora = T0 + 5 min

        reloj.set(T0.plus(Duration.ofHours(1))); // sale exactamente el registro de T0
        assertThatCode(() -> limitador.registrarIp("10.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limitador.registrarIp("10.0.0.1")).isInstanceOf(DemasiadasSolicitudesException.class);

        reloj.set(T0.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(5)));
        assertThatCode(() -> limitador.registrarIp("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void unaSolicitudRechazadaNoExtiendeLaEspera() {
        cincoDeLaIp("10.0.0.1");
        assertThatThrownBy(() -> limitador.registrarIp("10.0.0.1")).isInstanceOf(DemasiadasSolicitudesException.class);
        reloj.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> limitador.registrarIp("10.0.0.1"))
                .isInstanceOfSatisfying(DemasiadasSolicitudesException.class,
                        e -> assertThat(e.retryAfterSegundos()).isEqualTo(Duration.ofMinutes(45).toSeconds()));
    }

    @Test
    void elLimiteSaleDeLaConfiguracionPublicRateLimitPerHour() {
        AppProperties props = new AppProperties("http://localhost:8080", ZoneId.of("America/Argentina/Buenos_Aires"),
                24, 30, 2, List.of("EMAIL"), "no-reply@consultorio.example");
        LimitadorSolicitudesPublicas configurado = new LimitadorSolicitudesPublicas(reloj, props);

        configurado.registrarIp("10.0.0.1");
        configurado.registrarIp("10.0.0.1");

        assertThatThrownBy(() -> configurado.registrarIp("10.0.0.1"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        assertThatCode(() -> configurado.registrarIp("10.0.0.2")).doesNotThrowAnyException();
    }

    // --- 3.2 identidad ---

    @Test
    void elMismoDniConYSinPuntosCuentaComoLaMismaClave() {
        String[] variantes = {"30123456", "30.123.456", "30123456", "30.123.456", "30123456"};
        for (int i = 0; i < 5; i++) {
            limitador.registrarIdentidad(variantes[i], "persona" + i + "@ejemplo.test");
        }

        assertThatThrownBy(() -> limitador.registrarIdentidad("30.123.456", "otra@ejemplo.test"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        assertThatCode(() -> limitador.registrarIdentidad("30999888", "otra@ejemplo.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void elEmailEnMayusculasYConEspaciosEsLaMismaClave() {
        String[] variantes = {"ana@ejemplo.test", "ANA@Ejemplo.test", " ana@ejemplo.test ", "Ana@EJEMPLO.TEST",
                "ana@ejemplo.test"};
        for (int i = 0; i < 5; i++) {
            limitador.registrarIdentidad("3000000" + i, variantes[i]);
        }

        assertThatThrownBy(() -> limitador.registrarIdentidad("30111222", "ANA@ejemplo.test"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    @Test
    void dniYEmailSonIndependientesEntreSiYDeLaIp() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarIdentidad("30123456", "uno" + i + "@ejemplo.test");
        }

        assertThatThrownBy(() -> limitador.registrarIdentidad("30123456", "nuevo@ejemplo.test"))
                .as("mismo DNI, otro email").isInstanceOf(DemasiadasSolicitudesException.class);
        assertThatCode(() -> limitador.registrarIdentidad("30555666", "uno0@ejemplo.test"))
                .as("otro DNI con un email usado una vez").doesNotThrowAnyException();
        assertThatCode(() -> limitador.registrarIp("10.0.0.9")).as("la IP es otra clave").doesNotThrowAnyException();
    }

    @Test
    void unRechazoPorUnaClaveNoRegistraLaOtra() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarIdentidad("30123456", "uno" + i + "@ejemplo.test");
        }
        // El DNI está agotado; el email "libre@" no debe quedar consumido por el intento rechazado.
        assertThatThrownBy(() -> limitador.registrarIdentidad("30123456", "libre@ejemplo.test"))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        for (int i = 0; i < 5; i++) {
            int n = i;
            assertThatCode(() -> limitador.registrarIdentidad("3099900" + n, "libre@ejemplo.test"))
                    .doesNotThrowAnyException();
        }
    }

    // --- 3.3 concurrencia y tope de claves ---

    @Test
    void conVariosHilosSobreLaMismaClaveSeAceptanExactamenteLimiteSolicitudes() throws Exception {
        int hilos = 40;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger aceptadas = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                salida.await();
                try {
                    limitador.registrarIp("10.0.0.1");
                    aceptadas.incrementAndGet();
                } catch (DemasiadasSolicitudesException e) {
                    // rechazada, esperado
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> f : futuros) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(aceptadas.get()).isEqualTo(5);
    }

    @Test
    void elTopeDeClavesPurgaLasInactivasYNuncaSeSuperaAunConTodasActivas() {
        LimitadorSolicitudesPublicas chico = new LimitadorSolicitudesPublicas(reloj, 5, 10);
        for (int i = 0; i < 10; i++) {
            chico.registrarIp("10.1.0." + i);
        }
        assertThat(chico.cantidadDeClaves()).isEqualTo(10);

        reloj.advance(Duration.ofHours(2)); // todas inactivas
        chico.registrarIp("10.2.0.1");
        assertThat(chico.cantidadDeClaves()).isEqualTo(1);

        for (int i = 0; i < 50; i++) { // todas activas: el tope igual se respeta
            chico.registrarIp("10.3.0." + i);
        }
        assertThat(chico.cantidadDeClaves()).isLessThanOrEqualTo(10);
    }

    @Test
    void laClaveRecienUsadaNoSePurgaPorElTope() {
        LimitadorSolicitudesPublicas chico = new LimitadorSolicitudesPublicas(reloj, 1, 3);
        chico.registrarIp("10.0.0.1");
        for (int i = 0; i < 10; i++) {
            chico.registrarIp("10.9.0." + i);
        }
        chico.registrarIp("10.0.0.99");

        assertThatThrownBy(() -> chico.registrarIp("10.0.0.99")).isInstanceOf(DemasiadasSolicitudesException.class);
    }
}
