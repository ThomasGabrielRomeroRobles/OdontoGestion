package ar.edu.odontogestion.domain.listaespera;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reglas puras de qué huecos se ofrecen a la lista de espera (C-10 D4, RN-LE). Sin acceso a datos: la consulta
 * {@code buscarCompatibles} aplica la misma regla de fechas en la base.
 */
public final class CompatibilidadHueco {

    private CompatibilidadHueco() {
    }

    /** Día calendario del inicio del hueco en la zona del consultorio. */
    public static LocalDate fechaLocal(Instant inicio, ZoneId zona) {
        return inicio.atZone(zona).toLocalDate();
    }

    /** Misma prestación y fecha local del hueco dentro de {@code [desde, hasta]}, bordes incluidos. */
    public static boolean compatible(long prestacionEntrada, LocalDate desde, LocalDate hasta, long prestacionHueco,
            Instant inicioHueco, ZoneId zona) {
        LocalDate fecha = fechaLocal(inicioHueco, zona);
        return prestacionEntrada == prestacionHueco && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /** RN-LE-10: un hueco que empieza antes de {@code ahora + anticipación} (o justo en ese instante) no se ofrece. */
    public static boolean ofrecible(Instant inicio, Instant ahora, int anticipacionMinReservaMin) {
        return inicio.isAfter(ahora.plusSeconds(anticipacionMinReservaMin * 60L));
    }

    /** {@code min(ahora + vencimientoAvisoMin, inicio)}: el aviso nunca vive más que el hueco. */
    public static Instant venceEn(Instant ahora, int vencimientoAvisoMin, Instant inicio) {
        Instant porParametro = ahora.plusSeconds(vencimientoAvisoMin * 60L);
        return porParametro.isBefore(inicio) ? porParametro : inicio;
    }
}
