package ar.edu.odontogestion.application.listaespera;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Generación de avisos al liberarse un hueco (C-10 D4). "Ahora" = viernes 2026-11-20 09:00 BA; hueco el lunes 23. */
class AvisoHuecoServiceGenerarIT extends AbstractListaEsperaIT {

    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private AvisoHuecoService servicio;

    private HuecoLiberado hueco(Long prestacion, Instant inicio, Instant fin) {
        Long turnoOrigen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, inicio, fin);
        return new HuecoLiberado(consultorioId(), turnoOrigen, prestacion, inicio, fin);
    }

    private List<Map<String, Object>> avisos(Long turnoOrigen) {
        return jdbc.queryForList("SELECT * FROM aviso_hueco WHERE turno_origen_id = ? ORDER BY id", turnoOrigen);
    }

    @Test
    void tresEntradasActivasCompatiblesGeneranTresAvisosYTresNotificaciones() {
        Long prestacion = crearPrestacion(30);
        for (int i = 0; i < 3; i++) {
            insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN.plusDays(2));
        }
        HuecoLiberado h = hueco(prestacion, lunes("10:00"), lunes("10:30"));

        int creados = servicio.generarAvisos(h);

        assertThat(creados).isEqualTo(3);
        List<Map<String, Object>> as = avisos(h.turnoOrigenId());
        assertThat(as).hasSize(3).allSatisfy(a -> {
            assertThat(a.get("estado")).isEqualTo("ENVIADO");
            assertThat(a.get("prestacion_id")).isEqualTo(prestacion);
        });
        List<Map<String, Object>> ns = jdbc.queryForList("SELECT tipo, estado, enlaces::text AS enlaces FROM "
                + "notificacion WHERE aviso_id IN (SELECT id FROM aviso_hueco WHERE turno_origen_id = ?)",
                h.turnoOrigenId());
        assertThat(ns).hasSize(3).allSatisfy(n -> {
            assertThat(n.get("tipo")).isEqualTo("AVISO_HUECO");
            assertThat(n.get("estado")).isEqualTo("PENDIENTE");
            assertThat((String) n.get("enlaces")).contains("ACEPTAR_AVISO");
        });
        // vence_en = ahora + 60 min (parámetro del seed), anterior al inicio del hueco.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_hueco WHERE turno_origen_id = ? AND "
                + "vence_en = ?::timestamptz", Integer.class, h.turnoOrigenId(),
                reloj.instant().plusSeconds(3600).toString())).isEqualTo(3);
    }

    @Test
    void soloLasEntradasActivasCompatiblesDePacientesActivosRecibenAviso() {
        Long prestacion = crearPrestacion(30);
        Long otra = crearPrestacion(30);
        Long ok = insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "PENDIENTE_CONFIRMACION", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "CANCELADA", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "CUBIERTA", LUN, LUN);
        insertarEntrada(crearPaciente(), otra, "ACTIVA", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN.plusDays(1), LUN.plusDays(3));
        insertarEntrada(crearPacienteDeBaja(), prestacion, "ACTIVA", LUN, LUN);
        HuecoLiberado h = hueco(prestacion, lunes("10:00"), lunes("10:30"));

        assertThat(servicio.generarAvisos(h)).isEqualTo(1);

        assertThat(avisos(h.turnoOrigenId())).extracting(a -> a.get("entrada_lista_espera_id"))
                .containsExactly(ok);
    }

    @Test
    void sinCompatiblesNoCreaNada() {
        Long prestacion = crearPrestacion(30);
        HuecoLiberado h = hueco(prestacion, lunes("10:00"), lunes("10:30"));

        assertThat(servicio.generarAvisos(h)).isZero();

        assertThat(avisos(h.turnoOrigenId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'AVISO_HUECO' AND "
                + "creado_en >= ?::timestamptz", Integer.class, reloj.instant().toString())).isZero();
    }

    @Test
    void reprocesarElMismoEventoNoDuplicaAvisosNiNotificaciones() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        HuecoLiberado h = hueco(prestacion, lunes("10:00"), lunes("10:30"));

        assertThat(servicio.generarAvisos(h)).isEqualTo(2);
        assertThat(servicio.generarAvisos(h)).isZero();

        assertThat(avisos(h.turnoOrigenId())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE aviso_id IN (SELECT id FROM "
                + "aviso_hueco WHERE turno_origen_id = ?)", Integer.class, h.turnoOrigenId())).isEqualTo(2);
    }

    @Test
    void unHuecoSinAnticipacionMinimaNoGeneraAvisos() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LocalDate.of(2026, 11, 20), LUN);
        Instant dentroDe60Min = reloj.instant().plusSeconds(3600);
        HuecoLiberado h = hueco(prestacion, dentroDe60Min, dentroDe60Min.plusSeconds(1800));

        assertThat(servicio.generarAvisos(h)).isZero();

        assertThat(avisos(h.turnoOrigenId())).isEmpty();
    }

    @Test
    void elAvisoVenceAlInicioDelHuecoSiEsAntesQueElParametro() {
        Long prestacion = crearPrestacion(30);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LocalDate.of(2026, 11, 20), LUN);
        reloj.set(reloj.instant().minusSeconds(0)); // ahora fijo
        // Anticipación 120 min: un hueco a 130 min cumple, y 130 > 60 -> vence a los 60. Se baja el parámetro para
        // forzar el otro lado: vencimiento_aviso_min = 180 y hueco a 130 min -> vence al inicio.
        jdbc.update("UPDATE consultorio SET vencimiento_aviso_min = 180");
        try {
            Instant inicio = reloj.instant().plusSeconds(130 * 60);
            HuecoLiberado h = hueco(prestacion, inicio, inicio.plusSeconds(1800));

            assertThat(servicio.generarAvisos(h)).isEqualTo(1);

            assertThat(jdbc.queryForObject("SELECT vence_en = inicio FROM aviso_hueco WHERE turno_origen_id = ?",
                    Boolean.class, h.turnoOrigenId())).isTrue();
        } finally {
            jdbc.update("UPDATE consultorio SET vencimiento_aviso_min = 60");
        }
    }
}
