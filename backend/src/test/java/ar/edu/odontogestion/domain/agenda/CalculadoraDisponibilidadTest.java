package ar.edu.odontogestion.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dominio puro (design D7): sin Spring, sin base. Zona del consultorio: Buenos Aires (UTC-3, sin horario de verano). */
class CalculadoraDisponibilidadTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Lunes. */
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final Instant SIN_MINIMO = Instant.EPOCH;

    private static FranjaHoraria franja(int dia, String desde, String hasta) {
        return new FranjaHoraria(dia, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    /** Instante UTC de una hora local de Buenos Aires del lunes 2026-11-23 (UTC-3). */
    private static Instant lunes(String horaLocal) {
        return LUNES.atTime(LocalTime.parse(horaLocal)).atZone(BA).toInstant();
    }

    private static Hueco hueco(String desdeLocal, String hastaLocal) {
        return new Hueco(lunes(desdeLocal), lunes(hastaLocal));
    }

    private static List<Hueco> calcular(LocalDate desde, LocalDate hasta, List<FranjaHoraria> horarios,
            List<Hueco> bloqueos, List<Hueco> ocupados, int duracionMin, int granularidadMin, Instant minimoInicio) {
        return CalculadoraDisponibilidad.calcular(desde, hasta, BA, horarios, bloqueos, ocupados, duracionMin,
                granularidadMin, minimoInicio);
    }

    private static List<Hueco> lunesSolo(List<FranjaHoraria> horarios, List<Hueco> bloqueos, List<Hueco> ocupados,
            int duracionMin, int granularidadMin, Instant minimoInicio) {
        return calcular(LUNES, LUNES, horarios, bloqueos, ocupados, duracionMin, granularidadMin, minimoInicio);
    }

    @Test
    void horarioDeUnaHoraConPrestacionDeTreintaYGranularidadDeQuinceDaTresHuecos() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "10:00")), List.of(), List.of(), 30, 15,
                SIN_MINIMO);

        assertThat(huecos).containsExactly(hueco("09:00", "09:30"), hueco("09:15", "09:45"),
                hueco("09:30", "10:00"));
    }

    @Test
    void diaSinHorarioNoTieneHuecos() {
        // El horario es de martes (2); se consulta un lunes.
        assertThat(lunesSolo(List.of(franja(2, "09:00", "13:00")), List.of(), List.of(), 30, 15, SIN_MINIMO))
                .isEmpty();
        assertThat(lunesSolo(List.of(), List.of(), List.of(), 30, 15, SIN_MINIMO)).isEmpty();
    }

    @Test
    void dosHorariosEnElMismoDiaNoGeneranHuecosQueCrucenElCorte() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "13:00"), franja(1, "14:00", "18:00")),
                List.of(), List.of(), 30, 15, SIN_MINIMO);

        assertThat(huecos).contains(hueco("12:30", "13:00"), hueco("14:00", "14:30"))
                .doesNotContain(hueco("12:45", "13:15"), hueco("13:00", "13:30"), hueco("13:45", "14:15"))
                .allSatisfy(h -> assertThat(h.fin()).isNotIn(lunes("13:15"), lunes("13:30")));
        assertThat(huecos).hasSize(15 + 15);
    }

    @Test
    void ocupadosYBloqueosDescuentanHuecosConIntervalosSemiabiertos() {
        // Horario 09:00-11:00; turno 09:00-09:30; bloqueo 10:00-11:00; prestación de 30 min, granularidad 15.
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "11:00")), List.of(hueco("10:00", "11:00")),
                List.of(hueco("09:00", "09:30")), 30, 15, SIN_MINIMO);

        // 09:15 solapa parcialmente con el turno; 09:30-10:00 es contiguo al turno y al bloqueo.
        assertThat(huecos).containsExactly(hueco("09:30", "10:00"));
    }

    @Test
    void ocupadosContiguosNoDescuentan() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "10:00")), List.of(),
                List.of(hueco("08:30", "09:00"), hueco("10:00", "10:30")), 30, 15, SIN_MINIMO);

        assertThat(huecos).containsExactly(hueco("09:00", "09:30"), hueco("09:15", "09:45"),
                hueco("09:30", "10:00"));
    }

    @Test
    void solapeParcialConUnBloqueoDescartaElHueco() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "10:00")), List.of(hueco("09:20", "09:40")),
                List.of(), 30, 15, SIN_MINIMO);

        assertThat(huecos).isEmpty();
    }

    @Test
    void horaDesdeNoAlineadaArrancaEnElPrimerCandidatoAlineado() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:10", "10:00")), List.of(), List.of(), 30, 15,
                SIN_MINIMO);

        assertThat(huecos).containsExactly(hueco("09:15", "09:45"), hueco("09:30", "10:00"));
    }

    @Test
    void duracionNoMultiploDeLaGranularidadSeToleraYNoCaeFueraDelHorario() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "10:00")), List.of(), List.of(), 20, 15,
                SIN_MINIMO);

        assertThat(huecos).containsExactly(hueco("09:00", "09:20"), hueco("09:15", "09:35"),
                hueco("09:30", "09:50"));
    }

    @Test
    void ventanaDeVariosDiasDevuelveHuecosOrdenadosPorInicio() {
        List<Hueco> huecos = calcular(LUNES, LUNES.plusDays(2),
                List.of(franja(3, "09:00", "09:30"), franja(1, "14:00", "14:30"), franja(1, "09:00", "09:30")),
                List.of(), List.of(), 30, 30, SIN_MINIMO);

        assertThat(huecos).extracting(Hueco::inicio).containsExactly(lunes("09:00"), lunes("14:00"),
                LUNES.plusDays(2).atTime(9, 0).atZone(BA).toInstant());
    }

    @Test
    void minimoInicioExcluyeLosHuecosAnterioresEIncluyeElExacto() {
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "09:00", "10:00")), List.of(), List.of(), 30, 15,
                lunes("09:15"));

        assertThat(huecos).containsExactly(hueco("09:15", "09:45"), hueco("09:30", "10:00"));
    }

    @Test
    void laConversionDeZonaUsaLaHoraLocalYNoUtc() {
        // 22:00 local de Buenos Aires = 01:00 UTC del día siguiente.
        List<Hueco> huecos = lunesSolo(List.of(franja(1, "22:00", "23:00")), List.of(), List.of(), 30, 30,
                SIN_MINIMO);

        assertThat(huecos).containsExactly(hueco("22:00", "22:30"), hueco("22:30", "23:00"));
        assertThat(huecos.get(0).inicio()).isEqualTo(Instant.parse("2026-11-24T01:00:00Z"));

        // Un turno ocupado a las 01:00Z del martes (22:00 locales del lunes) descuenta el hueco de las 22:00.
        List<Hueco> conOcupado = lunesSolo(List.of(franja(1, "22:00", "23:00")), List.of(),
                List.of(new Hueco(Instant.parse("2026-11-24T01:00:00Z"), Instant.parse("2026-11-24T01:30:00Z"))),
                30, 30, SIN_MINIMO);
        assertThat(conOcupado).containsExactly(hueco("22:30", "23:00"));
    }
}
