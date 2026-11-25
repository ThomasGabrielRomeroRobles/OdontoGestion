package ar.edu.odontogestion.application.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.support.AbstractRecordatorioIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.token.PropositoToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Concurrencia y envío efectivo del recordatorio (C-11 grupo 4, D4): dos hilos reales contra PostgreSQL. */
class RecordatorioEnvioIT extends AbstractRecordatorioIT {

    @Autowired
    private RecordatorioService servicio;
    @Autowired
    private NotificationService notificaciones;
    @Autowired
    private CanalEnMemoria canal;
    @Autowired
    private TokenAccesoService tokens;

    @BeforeEach
    @AfterEach
    void reiniciarCanal() {
        canal.reiniciar();
    }

    private List<NotificacionSaliente> recordatoriosEnviados() {
        return canal.enviados().stream().filter(n -> n.tipo() == TipoNotificacion.RECORDATORIO).toList();
    }

    @Test
    void dosCorridasConcurrentesDejanUnaSolaFilaPorTurnoYUnSoloEmail() throws Exception {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        List<Long> ids = List.of(reservado(p, pr, ahora.plus(Duration.ofHours(2))),
                reservado(p, pr, ahora.plus(Duration.ofHours(3))), reservado(p, pr, ahora.plus(Duration.ofHours(4))));

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            Future<Integer> a = hilos.submit(() -> {
                largada.await();
                return servicio.encolarRecordatorios();
            });
            Future<Integer> b = hilos.submit(() -> {
                largada.await();
                return servicio.encolarRecordatorios();
            });
            largada.countDown();
            int total = a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS);

            assertThat(total).as("entre las dos corridas se encolan exactamente los 3 turnos").isEqualTo(3);
        } finally {
            hilos.shutdownNow();
        }
        ids.forEach(id -> assertThat(recordatoriosDe(id)).isEqualTo(1));

        notificaciones.procesarPendientes();
        notificaciones.procesarPendientes();

        assertThat(recordatoriosEnviados()).hasSize(3);
    }

    @Test
    void dosCorridasYDosEnviosEntreganExactamenteUnRecordatorioPorTurnoSinDatosPersonales() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Long id = reservado(p, pr, reloj.instant().plus(Duration.ofHours(5)));
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, p);

        servicio.encolarRecordatorios();
        servicio.encolarRecordatorios();
        notificaciones.procesarPendientes();
        notificaciones.procesarPendientes();

        assertThat(recordatoriosDe(id)).isEqualTo(1);
        assertThat(recordatoriosEnviados()).singleElement().satisfies(n -> {
            assertThat(n.cuerpo()).contains("IT-Turno-");
            assertThat(n.cuerpo().split("/acciones/", -1)).as("dos enlaces /acciones/").hasSize(3);
            assertThat(n.cuerpo()).doesNotContain(dni).doesNotContain("+54 11 5555-0000").doesNotContain("Ficticia");
        });
        assertThat(jdbc.queryForObject("SELECT estado FROM notificacion WHERE turno_id = ? AND tipo = 'RECORDATORIO'",
                String.class, id)).isEqualTo("ENVIADA");
    }

    @Test
    void elEnlaceCancelarDelEmailEnviadoCancelaElTurno() throws Exception {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Long id = reservado(p, pr, reloj.instant().plus(Duration.ofHours(8)));
        servicio.encolarRecordatorios();
        notificaciones.procesarPendientes();
        Matcher m = Pattern.compile("/acciones/([A-Za-z0-9_-]+)").matcher(recordatoriosEnviados().get(0).cuerpo());
        String cancelar = null;
        while (m.find()) {
            if (tokens.identificar(m.group(1), Set.of(PropositoToken.CANCELAR_TURNO, PropositoToken.CONFIRMAR_TURNO))
                    .proposito() == PropositoToken.CANCELAR_TURNO) {
                cancelar = m.group(1);
            }
        }
        assertThat(cancelar).isNotNull();
        Cliente anonimo = clienteAnonimo();

        int estado = mockMvc.perform(anonimo.firmar(post("/api/v1/acciones/" + cancelar))
                .contentType("application/json").content("{\"accion\":\"CANCELAR_TURNO\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(200);
        assertThat(estadoDe(id)).isEqualTo("CANCELADO");
    }
}
