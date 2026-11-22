package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.application.notificacion.NotificationServiceEncolarIT.DATOS_TURNO;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.token.HashToken;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.token.TokenVigente;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Enlaces con tokens emitidos al enviar con el adaptador real (design D8, RN-TK-02). Ids ficticios. */
@Import(FixedClockTestConfig.class)
class NotificationServiceEnlacesIT extends AbstractIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("/(acciones|avisos)/([A-Za-z0-9_-]{43})");

    @Autowired
    private NotificationService service;

    @Autowired
    private TokenAccesoService tokens;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private CanalEnMemoria canal;

    @Autowired
    private MutableClock reloj;

    @AfterEach
    void limpiar() {
        reloj.set(AHORA);
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private static List<String[]> enlaces(String cuerpo) {
        List<String[]> encontrados = new ArrayList<>();
        Matcher m = TOKEN.matcher(cuerpo);
        while (m.find()) {
            encontrados.add(new String[] {m.group(1), m.group(2)});
        }
        return encontrados;
    }

    private String filaComoTexto(String where) {
        return jdbc.queryForObject("SELECT concat_ws('|', datos::text, enlaces::text, coalesce(error, '')) "
                + "FROM notificacion WHERE " + where, String.class);
    }

    @Test
    void recordatorioLlevaDosEnlacesConTokensValidosParaSuPropositoYTurno() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900101), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))))));

        service.procesarPendientes();

        String cuerpo = canal.enviados().getFirst().cuerpo();
        List<String[]> encontrados = enlaces(cuerpo);
        assertThat(encontrados).hasSize(2).allSatisfy(e -> assertThat(e[0]).isEqualTo("acciones"));
        assertThat(cuerpo).contains(appProperties.baseUrl() + "/acciones/" + encontrados.get(0)[1]);

        TokenVigente confirmar = tokens.verificar(encontrados.get(0)[1], PropositoToken.CONFIRMAR_TURNO);
        TokenVigente cancelar = tokens.verificar(encontrados.get(1)[1], PropositoToken.CANCELAR_TURNO);
        assertThat(confirmar.referencia()).isEqualTo(new ReferenciaToken.Turno(900101));
        assertThat(cancelar.referencia()).isEqualTo(new ReferenciaToken.Turno(900101));
    }

    @Test
    void avisoHuecoUsaLaRutaDeAvisos() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.AVISO_HUECO, "paciente@ejemplo.test",
                new ReferenciaToken.Aviso(900007), DATOS_TURNO,
                List.of(new EnlaceProgramado(PropositoToken.ACEPTAR_AVISO, AHORA.plus(Duration.ofHours(2))))));

        service.procesarPendientes();

        List<String[]> encontrados = enlaces(canal.enviados().getFirst().cuerpo());
        assertThat(encontrados).singleElement().satisfies(e -> {
            assertThat(e[0]).isEqualTo("avisos");
            assertThat(tokens.verificar(e[1], PropositoToken.ACEPTAR_AVISO).referencia())
                    .isEqualTo(new ReferenciaToken.Aviso(900007));
        });
    }

    @Test
    void laFilaDeLaNotificacionNoContieneLosTokensNiSuHash() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900102), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))))));

        service.procesarPendientes();

        String fila = filaComoTexto("turno_id = 900102");
        for (String[] enlace : enlaces(canal.enviados().getFirst().cuerpo())) {
            byte[] hash = HashToken.sha256(enlace[1]);
            assertThat(fila).doesNotContain(enlace[1])
                    .doesNotContain(HexFormat.of().formatHex(hash))
                    .doesNotContain(Base64.getEncoder().encodeToString(hash));
        }
    }

    @Test
    void enlaceVencidoAntesDelEnvioDejaLaNotificacionFallidaSinEmitirTokens() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.CONFIRMAR_SOLICITUD, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900103), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_SOLICITUD, AHORA.plus(Duration.ofMinutes(30))))));
        reloj.advance(Duration.ofMinutes(30));

        service.procesarPendientes();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, error, intentos FROM notificacion WHERE turno_id = 900103");
        assertThat(fila).containsEntry("estado", "FALLIDA").containsEntry("error", "ENLACE_VENCIDO")
                .containsEntry("intentos", 0);
        assertThat(canal.enviados()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_acceso WHERE turno_id = 900103", Integer.class))
                .isZero();
    }

    private int tokensVigentes(long turnoId) {
        return jdbc.queryForObject("SELECT count(*) FROM token_acceso WHERE turno_id = ? AND usado_en IS NULL "
                + "AND vence_en > ?", Integer.class, turnoId, java.sql.Timestamp.from(reloj.instant()));
    }

    private void encolarRecordatorio(long turnoId) {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(turnoId), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))))));
    }

    @Test
    void siElEnvioFallaLosTokensEmitidosEnEseIntentoQuedanInvalidosPorqueNuncaSeEntregaron() {
        canal.fallarSiempre();
        encolarRecordatorio(900104);

        service.procesarPendientes();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_acceso WHERE turno_id = 900104", Integer.class))
                .isEqualTo(2);
        assertThat(tokensVigentes(900104)).isZero();
    }

    @Test
    void elFalloNoRevocaUnTokenDelMismoTurnoYPropositoEntregadoAntes() {
        TokenEmitido entregadoAntes = tokens.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(900105),
                AHORA.plus(Duration.ofHours(24)));
        canal.fallarSiempre();
        encolarRecordatorio(900105);

        service.procesarPendientes();

        assertThat(tokens.verificar(entregadoAntes.valor(), PropositoToken.CONFIRMAR_TURNO).referencia())
                .isEqualTo(new ReferenciaToken.Turno(900105));
        assertThat(tokensVigentes(900105)).isEqualTo(1);
    }

    @Test
    void trasUnFalloElReintentoEmiteTokensNuevosVigentesYLosDelIntentoFallidoSiguenInvalidos() {
        canal.fallarSiempre();
        encolarRecordatorio(900106);
        service.procesarPendientes();
        canal.reiniciar();
        reloj.advance(Duration.ofHours(1));

        service.procesarPendientes();

        assertThat(canal.enviados()).hasSize(1);
        List<String[]> entregados = enlaces(canal.enviados().getFirst().cuerpo());
        assertThat(entregados).hasSize(2);
        assertThat(tokens.verificar(entregados.get(0)[1], PropositoToken.CONFIRMAR_TURNO).referencia())
                .isEqualTo(new ReferenciaToken.Turno(900106));
        assertThat(tokensVigentes(900106)).isEqualTo(2);
    }

    @Test
    void siElEnvioSeConcretaLosTokensQuedanVigentes() {
        encolarRecordatorio(900107);

        service.procesarPendientes();

        assertThat(canal.enviados()).hasSize(1);
        assertThat(tokensVigentes(900107)).isEqualTo(2);
    }
}
