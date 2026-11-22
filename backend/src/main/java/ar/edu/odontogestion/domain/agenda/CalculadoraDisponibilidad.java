package ar.edu.odontogestion.domain.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cálculo puro de huecos libres (design D7): sin Spring ni JPA. Un candidato empieza en un minuto del día alineado
 * a la granularidad dentro de un horario de trabajo y dura lo que la prestación; se descartan los que se solapan
 * (intervalos semiabiertos {@code [)}) con bloqueos u ocupados o empiezan antes de {@code minimoInicio}.
 * Es orientativo: la garantía de no superposición es la restricción de la base (regla dura #8).
 */
public final class CalculadoraDisponibilidad {

    private CalculadoraDisponibilidad() {
    }

    /**
     * @param desde primer día local (inclusive)
     * @param hasta último día local (inclusive)
     * @param bloqueos bloqueos de agenda {@code [desde, hasta)}
     * @param ocupados intervalos de turnos que ocupan agenda {@code [inicio, fin)}
     * @param minimoInicio primer inicio admitido (política de anticipación)
     * @return huecos ordenados por inicio
     */
    public static List<Hueco> calcular(LocalDate desde, LocalDate hasta, ZoneId zona, List<FranjaHoraria> horarios,
            List<Hueco> bloqueos, List<Hueco> ocupados, int duracionMin, int granularidadMin,
            Instant minimoInicio) {
        List<Hueco> huecos = new ArrayList<>();
        Duration duracion = Duration.ofMinutes(duracionMin);
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            int diaSemana = dia.getDayOfWeek().getValue();
            for (FranjaHoraria franja : horarios) {
                if (franja.diaSemana() != diaSemana) {
                    continue;
                }
                LocalTime inicioLocal = primerAlineado(franja.desde(), granularidadMin);
                while (inicioLocal != null && !inicioLocal.plusMinutes(duracionMin).isAfter(franja.hasta())
                        && !desbordaElDia(inicioLocal, duracionMin)) {
                    Instant inicio = dia.atTime(inicioLocal).atZone(zona).toInstant();
                    Hueco candidato = new Hueco(inicio, inicio.plus(duracion));
                    if (!inicio.isBefore(minimoInicio) && !seSolapaConAlguno(candidato, bloqueos)
                            && !seSolapaConAlguno(candidato, ocupados)) {
                        huecos.add(candidato);
                    }
                    inicioLocal = inicioLocal.plusMinutes(granularidadMin);
                    if (inicioLocal.equals(LocalTime.MIDNIGHT)) {
                        inicioLocal = null;
                    }
                }
            }
        }
        huecos.sort(Comparator.comparing(Hueco::inicio));
        return List.copyOf(huecos);
    }

    /** Intersección de intervalos semiabiertos: el contiguo no se solapa. */
    private static boolean seSolapaConAlguno(Hueco candidato, List<Hueco> intervalos) {
        for (Hueco otro : intervalos) {
            if (candidato.inicio().isBefore(otro.fin()) && otro.inicio().isBefore(candidato.fin())) {
                return true;
            }
        }
        return false;
    }

    /** Primer minuto del día alineado a la granularidad que no es anterior a {@code desde}. */
    private static LocalTime primerAlineado(LocalTime desde, int granularidadMin) {
        int minuto = desde.getHour() * 60 + desde.getMinute();
        int resto = minuto % granularidadMin;
        int alineado = resto == 0 ? minuto : minuto + (granularidadMin - resto);
        return alineado >= 24 * 60 ? null : LocalTime.of(alineado / 60, alineado % 60);
    }

    /** Evita que {@code plusMinutes} dé la vuelta pasada la medianoche. */
    private static boolean desbordaElDia(LocalTime inicio, int duracionMin) {
        return inicio.getHour() * 60 + inicio.getMinute() + duracionMin > 24 * 60;
    }
}
