package ar.edu.odontogestion.support;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Base de los ITs de lista de espera (C-10): reusa el reloj fijo y la limpieza de {@link AbstractTurnoIT} (que ya
 * borra entradas y avisos de los pacientes {@code 38xxxxxx}) y suma fixtures por JDBC.
 */
public abstract class AbstractListaEsperaIT extends AbstractTurnoIT {

    protected Long insertarEntrada(Long pacienteId, Long prestacionId, String estado, LocalDate desde,
            LocalDate hasta) {
        Instant pendienteHasta = "PENDIENTE_CONFIRMACION".equals(estado) ? FixedClockTestConfig.AHORA.plusSeconds(3600)
                : null;
        return insertarEntrada(pacienteId, prestacionId, estado, desde, hasta, pendienteHasta);
    }

    protected Long insertarEntrada(Long pacienteId, Long prestacionId, String estado, LocalDate desde,
            LocalDate hasta, Instant pendienteHasta) {
        return jdbc.queryForObject("INSERT INTO entrada_lista_espera (consultorio_id, paciente_id, prestacion_id, "
                + "fecha_desde, fecha_hasta, estado, origen, pendiente_hasta) VALUES (?, ?, ?, ?, ?, ?, 'PUBLICA', "
                + "?::timestamptz) RETURNING id", Long.class, consultorioId(), pacienteId, prestacionId, desde, hasta,
                estado, pendienteHasta == null ? null : pendienteHasta.toString());
    }

    protected Long insertarAviso(Long entradaId, Long turnoOrigenId, Long prestacionId, Instant inicio, Instant fin,
            String estado, Instant venceEn) {
        return jdbc.queryForObject("INSERT INTO aviso_hueco (consultorio_id, entrada_lista_espera_id, "
                + "turno_origen_id, prestacion_id, inicio, fin, estado, vence_en) VALUES (?, ?, ?, ?, "
                + "?::timestamptz, ?::timestamptz, ?, ?::timestamptz) RETURNING id", Long.class, consultorioId(),
                entradaId, turnoOrigenId, prestacionId, inicio.toString(), fin.toString(), estado,
                venceEn.toString());
    }

    protected String estadoEntrada(Long entradaId) {
        return jdbc.queryForObject("SELECT estado FROM entrada_lista_espera WHERE id = ?", String.class, entradaId);
    }

    protected String estadoAviso(Long avisoId) {
        return jdbc.queryForObject("SELECT estado FROM aviso_hueco WHERE id = ?", String.class, avisoId);
    }
}
