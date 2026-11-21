package ar.edu.odontogestion.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.DemasiadosIntentosException;
import ar.edu.odontogestion.infrastructure.config.LoginProperties;
import ar.edu.odontogestion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitadorIntentosLoginTest {

    private static final String EMAIL = "odontologo@consultorio.example";
    private static final String IP = "10.0.0.1";

    private MutableClock reloj;
    private LimitadorIntentosLogin limitador;

    @BeforeEach
    void preparar() {
        reloj = new MutableClock(Instant.parse("2026-11-20T12:00:00Z"));
        limitador = nuevo(5, 10_000);
    }

    private LimitadorIntentosLogin nuevo(int maxIntentos, int maxClaves) {
        return new LimitadorIntentosLogin(reloj,
                new LoginProperties(maxIntentos, Duration.ofSeconds(60), Duration.ofSeconds(900), maxClaves));
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrarFallo(EMAIL, IP);
        }
    }

    @Test
    void cuatroFallosTodaviaPermitenElIntento() {
        fallar(4);

        assertThatCode(() -> limitador.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void elQuintoFalloBloqueaPorSesentaSegundos() {
        fallar(5);

        assertThatThrownBy(() -> limitador.verificarNoBloqueado(EMAIL, IP))
                .isInstanceOfSatisfying(DemasiadosIntentosException.class,
                        e -> assertThat(e.retryAfterSegundos()).isEqualTo(60));
    }

    @Test
    void laEsperaRestanteDisminuyeConElTiempo() {
        fallar(5);
        reloj.advance(Duration.ofSeconds(25));

        assertThatThrownBy(() -> limitador.verificarNoBloqueado(EMAIL, IP))
                .isInstanceOfSatisfying(DemasiadosIntentosException.class,
                        e -> assertThat(e.retryAfterSegundos()).isEqualTo(35));
    }

    @Test
    void elBloqueoAplicaAunqueLasCredencialesSeanCorrectasPorqueSeConsultaAntes() {
        fallar(5);

        // el servicio consulta antes de autenticar: ninguna contraseña (buena o mala) llega a verificarse
        assertThatThrownBy(() -> limitador.verificarNoBloqueado(EMAIL, "10.9.9.9"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void alosSesentaYUnSegundosSePermiteNuevamente() {
        fallar(5);
        reloj.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limitador.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void unNuevoFalloTrasElBloqueoDuplicaLaEsperaHastaElTope() {
        fallar(5);
        long espera = 60;
        List<Long> esperas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            reloj.advance(Duration.ofSeconds(espera + 1)); // vence la espera vigente
            limitador.registrarFallo(EMAIL, IP);
            try {
                limitador.verificarNoBloqueado(EMAIL, IP);
            } catch (DemasiadosIntentosException e) {
                espera = e.retryAfterSegundos();
                esperas.add(espera);
            }
        }

        assertThat(esperas).containsExactly(120L, 240L, 480L, 900L, 900L, 900L);
    }

    @Test
    void unExitoReiniciaElContador() {
        fallar(4);
        limitador.registrarExito(EMAIL, IP);
        fallar(4);

        assertThatCode(() -> limitador.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();
        assertThat(limitador.fallosConsecutivos(EMAIL)).isEqualTo(4);
    }

    @Test
    void cincoFallosDesdeUnaMismaIpConEmailsDistintosBloqueanLaIp() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarFallo("persona" + i + "@ejemplo.test", IP);
        }

        assertThatThrownBy(() -> limitador.verificarNoBloqueado("otra@ejemplo.test", IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> limitador.verificarNoBloqueado("otra@ejemplo.test", "10.0.0.2"))
                .doesNotThrowAnyException();
    }

    @Test
    void lasClavesInactivasPorQuinceMinutosSePurgan() {
        fallar(4);
        reloj.advance(Duration.ofMinutes(16));

        assertThatCode(() -> limitador.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();
        assertThat(limitador.tamano()).isZero();
        fallar(1);
        assertThat(limitador.fallosConsecutivos(EMAIL)).isEqualTo(1);
    }

    @Test
    void lasClavesActivasDentroDeLosQuinceMinutosNoSePurgan() {
        fallar(4);
        reloj.advance(Duration.ofMinutes(10));

        limitador.verificarNoBloqueado(EMAIL, IP);

        assertThat(limitador.fallosConsecutivos(EMAIL)).isEqualTo(4);
    }

    @Test
    void elEmailSeNormalizaEnMayusculasYEspacios() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarFallo("  ODONTOLOGO@Consultorio.Example ", "10.0.1." + i);
        }

        assertThatThrownBy(() -> limitador.verificarNoBloqueado(EMAIL, "10.0.9.9"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void conMuchasClavesElMapaNoSuperaElTopeYConservaLaClaveNueva() {
        LimitadorIntentosLogin chico = nuevo(5, 10);
        for (int i = 0; i < 40; i++) {
            chico.registrarFallo("u" + i + "@ejemplo.test", "10.1.0." + i);
            reloj.advance(Duration.ofSeconds(1));
        }
        assertThat(chico.tamano()).isLessThanOrEqualTo(10);

        for (int i = 0; i < 5; i++) {
            chico.registrarFallo("nuevo@ejemplo.test", "10.2.0.1");
        }

        assertThat(chico.tamano()).isLessThanOrEqualTo(10);
        assertThatThrownBy(() -> chico.verificarNoBloqueado("nuevo@ejemplo.test", "10.3.0.1"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void veinteHilosRegistrandoFallosDelMismoEmailNoPierdenNingunoYLoBloquean() throws Exception {
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tareas = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            String ip = "10.5.0." + i;
            tareas.add(pool.submit(() -> {
                largada.await();
                limitador.registrarFallo(EMAIL, ip);
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> tarea : tareas) {
            tarea.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(limitador.fallosConsecutivos(EMAIL)).isEqualTo(20);
        assertThatThrownBy(() -> limitador.verificarNoBloqueado(EMAIL, "10.6.0.1"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }
}
