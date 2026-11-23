package ar.edu.odontogestion.api.acciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@code /api/v1/acciones/{token}} de punta a punta (C-09 grupo 9): sin sesión, con CSRF y PostgreSQL real. "Ahora"
 * = viernes 2026-11-20 09:00 BA; los turnos son el lunes 23. Límite de cancelación del seed: 2 horas. Los tokens se
 * emiten directo con {@link TokenAccesoService}: no dependen de la solicitud pública.
 */
class AccionesApiIT extends AbstractTurnoIT {

    private static final String BASE = "/api/v1/acciones/";

    @Autowired
    private TokenAccesoService tokens;
    @Autowired
    private ObjectMapper json;

    private Long turno(EstadoTurno estado, Instant inicio) {
        return insertarTurno(crearPaciente(), crearPrestacion(30), estado, inicio, inicio.plusSeconds(1800));
    }

    private TokenEmitido token(PropositoToken proposito, Long turnoId, Instant venceEn) {
        return tokens.emitir(proposito, new ReferenciaToken.Turno(turnoId), venceEn);
    }

    private static String accion(PropositoToken accion) {
        return "{\"accion\":\"" + accion + "\"}";
    }

    private MockHttpServletResponse obtener(String token) throws Exception {
        return mockMvc.perform(get(BASE + token)).andReturn().getResponse();
    }

    private MockHttpServletResponse enviar(Cliente cliente, String token, String cuerpo) throws Exception {
        return mockMvc.perform(cliente.firmar(post(BASE + token)).contentType("application/json").content(cuerpo))
                .andReturn().getResponse();
    }

    private boolean usado(TokenEmitido t) {
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE turno_id = ? AND "
                + "proposito = ?", Boolean.class, ((ReferenciaToken.Turno) t.referencia()).id(),
                t.proposito().name());
    }

    // --- 9.1 ---

    @Test
    void getDevuelveElResumenSinSesionYSinDatosDelPaciente() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse r = obtener(t.valor());

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.fieldNames()).toIterable().containsExactlyInAnyOrder("accion", "venceEn", "disponible",
                "motivo", "limiteCancelacion", "turno");
        assertThat(cuerpo.get("accion").asText()).isEqualTo("CANCELAR_TURNO");
        assertThat(cuerpo.get("venceEn").asText()).isEqualTo(lunes("10:00").toString());
        assertThat(cuerpo.get("disponible").asBoolean()).isTrue();
        assertThat(cuerpo.get("motivo").isNull()).isTrue();
        assertThat(cuerpo.get("limiteCancelacion").asText()).isEqualTo(lunes("08:00").toString());
        assertThat(cuerpo.get("turno").fieldNames()).toIterable().containsExactlyInAnyOrder("consultorio",
                "prestacion", "inicio", "fin", "estado");
        assertThat(cuerpo.get("turno").get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(r.getContentAsString()).doesNotContain("Ana").doesNotContain("@ejemplo.test")
                .doesNotContainPattern("38\\d{6}");
    }

    @Test
    void postConCsrfEjecutaLaAccionYDevuelveElEstadoNuevo() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse r = enviar(clienteAnonimo(), t.valor(), accion(PropositoToken.CONFIRMAR_TURNO));

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.get("turno").get("estado").asText()).isEqualTo("CONFIRMADO");
        assertThat(cuerpo.get("disponible").asBoolean()).isFalse();
        assertThat(cuerpo.get("venceEn").isNull()).isTrue();
        assertThat(estadoDe(id)).isEqualTo("CONFIRMADO");
        assertThat(usado(t)).isTrue();
    }

    @Test
    void accionAusenteODesconocidaDa400SinConsumirElToken() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
        Cliente c = clienteAnonimo();

        MockHttpServletResponse sinAccion = enviar(c, t.valor(), "{}");
        MockHttpServletResponse desconocida = enviar(c, t.valor(), "{\"accion\":\"BORRAR_TODO\"}");
        MockHttpServletResponse sinCuerpo = enviar(c, t.valor(), "");

        assertThat(sinAccion.getStatus()).isEqualTo(400);
        assertThat(sinAccion.getContentAsString()).contains("\"field\":\"accion\"");
        assertThat(desconocida.getStatus()).isEqualTo(400);
        assertThat(sinCuerpo.getStatus()).isEqualTo(400);
        assertThat(usado(t)).isFalse();
        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
    }

    @Test
    void usadoVencidoEInexistenteDan410ConElMismoCuerpoEnGetYPost() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido usado = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
        enviar(clienteAnonimo(), usado.valor(), accion(PropositoToken.CONFIRMAR_TURNO));
        TokenEmitido vencido = token(PropositoToken.CANCELAR_TURNO, id, reloj.instant().plusSeconds(60));
        reloj.advance(Duration.ofSeconds(61));
        String inexistente = "inexistente-" + "x".repeat(31);
        Cliente c = clienteAnonimo();

        List<MockHttpServletResponse> gets = List.of(obtener(usado.valor()), obtener(vencido.valor()), obtener(inexistente));
        List<MockHttpServletResponse> posts = List.of(
                enviar(c, usado.valor(), accion(PropositoToken.CONFIRMAR_TURNO)),
                enviar(c, vencido.valor(), accion(PropositoToken.CANCELAR_TURNO)),
                enviar(c, inexistente, accion(PropositoToken.CONFIRMAR_TURNO)));

        assertThat(gets).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(410));
        assertThat(posts).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(410));
        String referencia = gets.get(2).getContentAsString();
        assertThat(referencia).contains("/token-invalido");
        assertThat(gets).allSatisfy(r -> assertThat(r.getContentAsString()).isEqualTo(referencia));
        assertThat(posts).allSatisfy(r -> assertThat(r.getContentAsString()).isEqualTo(referencia));
    }

    // --- 9.2 ---

    @Test
    void postSinCabeceraCsrfDa403YNoCambiaNadaMientrasQueElGetEntregaLaCookie() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse sinCsrf = mockMvc.perform(post(BASE + t.valor()).contentType("application/json")
                .content(accion(PropositoToken.CONFIRMAR_TURNO))).andReturn().getResponse();

        assertThat(sinCsrf.getStatus()).isEqualTo(403);
        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
        assertThat(usado(t)).isFalse();

        MockHttpServletResponse previa = obtener(t.valor());
        Cookie xsrf = previa.getCookie("XSRF-TOKEN");
        assertThat(previa.getStatus()).isEqualTo(200);
        assertThat(xsrf).as("el GET sin cookies entrega XSRF-TOKEN").isNotNull();
        MockHttpServletResponse conCookie = mockMvc.perform(post(BASE + t.valor()).cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                        .content(accion(PropositoToken.CONFIRMAR_TURNO))).andReturn().getResponse();
        assertThat(conCookie.getStatus()).isEqualTo(200);
    }

    @Test
    void elGetDeUnTokenInvalidoTambienEntregaLaCookieCsrf() throws Exception {
        MockHttpServletResponse r = obtener("inexistente-" + "x".repeat(31));

        assertThat(r.getStatus()).isEqualTo(410);
        assertThat(r.getCookie("XSRF-TOKEN")).isNotNull();
    }

    // --- 9.3 ---

    @Test
    void dosPostSimultaneosConElMismoTokenDejanUnSolo200YUnSolo410() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < 5; ronda++) {
                Long id = turno(EstadoTurno.RESERVADO, lunes("10:00").plus(Duration.ofHours(ronda)));
                TokenEmitido t = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));
                Cliente a = clienteAnonimo();
                Cliente b = clienteAnonimo();
                CountDownLatch salida = new CountDownLatch(1);
                Future<Integer> fa = pool.submit(() -> {
                    salida.await();
                    return enviar(a, t.valor(), accion(PropositoToken.CANCELAR_TURNO)).getStatus();
                });
                Future<Integer> fb = pool.submit(() -> {
                    salida.await();
                    return enviar(b, t.valor(), accion(PropositoToken.CANCELAR_TURNO)).getStatus();
                });
                salida.countDown();

                List<Integer> estados = List.of(fa.get(30, TimeUnit.SECONDS), fb.get(30, TimeUnit.SECONDS));

                assertThat(estados).as("ronda %d", ronda).containsExactlyInAnyOrder(200, 410);
                assertThat(estadoDe(id)).isEqualTo("CANCELADO");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ? AND tipo = "
                        + "'CANCELACION'", Integer.class, id)).as("una única CANCELACION").isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- 9.4 ---

    @Test
    void confirmarYCancelarAlMismoTiempoDejanUnEstadoCoherenteConLasRespuestas() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < 8; ronda++) {
                Long id = turno(EstadoTurno.RESERVADO, lunes("10:00").plus(Duration.ofHours(ronda)));
                TokenEmitido confirmar = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
                TokenEmitido cancelar = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));
                Cliente a = clienteAnonimo();
                Cliente b = clienteAnonimo();
                CountDownLatch salida = new CountDownLatch(1);
                Future<Integer> fConfirmar = pool.submit(() -> {
                    salida.await();
                    return enviar(a, confirmar.valor(), accion(PropositoToken.CONFIRMAR_TURNO)).getStatus();
                });
                Future<Integer> fCancelar = pool.submit(() -> {
                    salida.await();
                    return enviar(b, cancelar.valor(), accion(PropositoToken.CANCELAR_TURNO)).getStatus();
                });
                salida.countDown();

                int rConfirmar = fConfirmar.get(30, TimeUnit.SECONDS);
                int rCancelar = fCancelar.get(30, TimeUnit.SECONDS);
                String estado = estadoDe(id);

                assertThat(rConfirmar).as("ronda %d confirmar", ronda).isIn(200, 409, 410, 422);
                assertThat(rCancelar).as("ronda %d cancelar", ronda).isIn(200, 409);
                if (rCancelar == 200) {
                    assertThat(estado).as("ronda %d", ronda).isEqualTo("CANCELADO");
                    assertThat(jdbc.queryForObject("SELECT cancelado_por FROM turno WHERE id = ?", String.class, id))
                            .isEqualTo("PACIENTE");
                } else {
                    assertThat(estado).as("ronda %d", ronda).isEqualTo("CONFIRMADO");
                    assertThat(rConfirmar).isEqualTo(200);
                    assertThat(usado(cancelar)).as("el token de cancelar sigue vigente").isFalse();
                }
                if (rConfirmar == 200) {
                    assertThat(usado(confirmar)).isTrue();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- 9.5 ---

    @Test
    void elLimiteDeCancelacionPorLaApiDentroEnElLimiteYFuera() throws Exception {
        Cliente c = clienteAnonimo();
        Long dentro = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido tDentro = token(PropositoToken.CANCELAR_TURNO, dentro, lunes("10:00"));
        Long enLimite = turno(EstadoTurno.CONFIRMADO, lunes("12:00"));
        TokenEmitido tLimite = token(PropositoToken.CANCELAR_TURNO, enLimite, lunes("12:00"));
        Long fuera = turno(EstadoTurno.RESERVADO, lunes("14:00"));
        TokenEmitido tFuera = token(PropositoToken.CANCELAR_TURNO, fuera, lunes("14:00"));

        reloj.set(lunes("10:00").minus(Duration.ofHours(3)));
        assertThat(enviar(c, tDentro.valor(), accion(PropositoToken.CANCELAR_TURNO)).getStatus()).isEqualTo(200);
        assertThat(estadoDe(dentro)).isEqualTo("CANCELADO");

        reloj.set(lunes("12:00").minus(Duration.ofHours(2)));
        assertThat(enviar(c, tLimite.valor(), accion(PropositoToken.CANCELAR_TURNO)).getStatus()).isEqualTo(200);
        assertThat(estadoDe(enLimite)).isEqualTo("CANCELADO");

        reloj.set(lunes("14:00").minus(Duration.ofMinutes(119)));
        MockHttpServletResponse rechazada = enviar(c, tFuera.valor(), accion(PropositoToken.CANCELAR_TURNO));
        assertThat(rechazada.getStatus()).isEqualTo(422);
        assertThat(rechazada.getContentAsString()).contains("/limite-cancelacion");
        assertThat(estadoDe(fuera)).isEqualTo("RESERVADO");
        assertThat(usado(tFuera)).isFalse();
        assertThat(obtener(tFuera.valor()).getStatus()).isEqualTo(200);
    }

    @Test
    void unTurnoYaAtendidoDa422TransicionInvalidaConElTokenVigente() throws Exception {
        Long id = turno(EstadoTurno.ATENDIDO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse r = enviar(clienteAnonimo(), t.valor(), accion(PropositoToken.CONFIRMAR_TURNO));

        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("/transicion-invalida");
        assertThat(estadoDe(id)).isEqualTo("ATENDIDO");
        assertThat(usado(t)).isFalse();
    }

    @Test
    void unaAccionQueNoCoincideConElPropositoDa410() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse r = enviar(clienteAnonimo(), t.valor(), accion(PropositoToken.CANCELAR_TURNO));

        assertThat(r.getStatus()).isEqualTo(410);
        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
        assertThat(usado(t)).isFalse();
    }
}
