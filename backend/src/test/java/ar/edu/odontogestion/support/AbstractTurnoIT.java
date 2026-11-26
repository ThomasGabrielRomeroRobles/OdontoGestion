package ar.edu.odontogestion.support;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base de los ITs de turnos (C-07): reloj fijo en 2026-11-20T12:00Z (viernes 09:00 en Buenos Aires), fixtures por
 * JDBC y limpieza completa de lo que crean (contenedor compartido, regla de higiene de tests). Los pacientes de
 * prueba usan DNI {@code 38xxxxxx}; las prestaciones, el prefijo {@code IT-Turno-}; los bloqueos, el motivo
 * {@code IT-Turno*}; el otro consultorio, el nombre {@link #OTRO_CONSULTORIO}. Usa el horario de trabajo del seed
 * (lunes a viernes, 09:00-13:00 y 14:00-18:00) y la granularidad de 15 minutos.
 */
public abstract class AbstractTurnoIT extends AbstractWebIntegrationTest {

    protected static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Primer lunes posterior al reloj fijo. */
    protected static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    protected static final String OTRO_CONSULTORIO = "IT-Turno otro consultorio";
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    protected MutableClock reloj;

    @BeforeEach
    void relojFijoDeTurnos() {
        reloj.set(FixedClockTestConfig.AHORA);
        limpiarTurnosDePrueba();
    }

    @AfterEach
    void limpiarAlTerminar() {
        reloj.set(FixedClockTestConfig.AHORA);
        limpiarTurnosDePrueba();
    }

    /** Hook para que las subclases borren primero lo que referencia a pacientes y turnos de prueba (C-05). */
    protected void limpiarDependientesDePrueba() {
    }

    private void limpiarTurnosDePrueba() {
        limpiarDependientesDePrueba();
        String idsTurno = "SELECT t.id FROM turno t WHERE t.paciente_id IN (SELECT id FROM paciente "
                + "WHERE dni LIKE '38%') OR t.prestacion_id IN (SELECT id FROM prestacion "
                + "WHERE nombre LIKE 'IT-Turno-%') OR t.consultorio_id IN (SELECT id FROM consultorio "
                + "WHERE nombre = '" + OTRO_CONSULTORIO + "')";
        String idsEntrada = "SELECT e.id FROM entrada_lista_espera e WHERE e.paciente_id IN (SELECT id FROM paciente "
                + "WHERE dni LIKE '38%') OR e.prestacion_id IN (SELECT id FROM prestacion WHERE nombre LIKE 'IT-Turno-%')";
        jdbc.update("DELETE FROM entrada_lista_espera WHERE consultorio_id IN (SELECT id FROM consultorio WHERE "
                + "nombre = '" + OTRO_CONSULTORIO + "')");
        String idsAviso = "SELECT a.id FROM aviso_hueco a WHERE a.entrada_lista_espera_id IN (" + idsEntrada + ")";
        jdbc.update("DELETE FROM notificacion WHERE aviso_id IN (" + idsAviso + ") OR entrada_lista_espera_id IN ("
                + idsEntrada + ")");
        jdbc.update("DELETE FROM token_acceso WHERE aviso_id IN (" + idsAviso + ") OR entrada_lista_espera_id IN ("
                + idsEntrada + ")");
        jdbc.update("DELETE FROM aviso_hueco WHERE id IN (" + idsAviso + ")");
        jdbc.update("DELETE FROM entrada_lista_espera WHERE id IN (" + idsEntrada + ")");
        jdbc.update("DELETE FROM notificacion WHERE turno_id IN (" + idsTurno + ")");
        jdbc.update("DELETE FROM token_acceso WHERE turno_id IN (" + idsTurno + ")");
        jdbc.update("DELETE FROM turno WHERE id IN (" + idsTurno + ")");
        jdbc.update("DELETE FROM paciente WHERE dni LIKE '38%'");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE 'IT-Turno-%'");
        jdbc.update("DELETE FROM bloqueo_agenda WHERE motivo LIKE 'IT-Turno%'");
        jdbc.update("DELETE FROM paciente WHERE consultorio_id IN (SELECT id FROM consultorio WHERE nombre = ?)",
                OTRO_CONSULTORIO);
        jdbc.update("DELETE FROM prestacion WHERE consultorio_id IN (SELECT id FROM consultorio WHERE nombre = ?)",
                OTRO_CONSULTORIO);
        jdbc.update("DELETE FROM consultorio WHERE nombre = ?", OTRO_CONSULTORIO);
    }

    protected Long consultorioId() {
        return jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
    }

    protected Long crearOtroConsultorio() {
        return jdbc.queryForObject("INSERT INTO consultorio (nombre) VALUES (?) RETURNING id", Long.class,
                OTRO_CONSULTORIO);
    }

    protected Long crearPaciente() {
        return crearPaciente(consultorioId(), true);
    }

    protected Long crearPacienteDeBaja() {
        return crearPaciente(consultorioId(), false);
    }

    protected Long crearPaciente(Long consultorioId, boolean activo) {
        int n = SECUENCIA.incrementAndGet();
        return jdbc.queryForObject("INSERT INTO paciente (consultorio_id, nombre, apellido, dni, telefono, email, "
                + "activo) VALUES (?, 'Ana', 'Ficticia" + n + "', ?, '+54 11 5555-0000', ?, ?) RETURNING id",
                Long.class, consultorioId, String.format("38%06d", n), "ana" + n + "@ejemplo.test", activo);
    }

    protected Long crearPrestacion(int minutos) {
        return crearPrestacion(consultorioId(), minutos, true);
    }

    protected Long crearPrestacionInactiva(int minutos) {
        return crearPrestacion(consultorioId(), minutos, false);
    }

    protected Long crearPrestacion(Long consultorioId, int minutos, boolean activa) {
        int n = SECUENCIA.incrementAndGet();
        return jdbc.queryForObject("INSERT INTO prestacion (consultorio_id, nombre, duracion_min, activa) "
                + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, consultorioId, "IT-Turno-" + n, minutos, activa);
    }

    /** Hora local de Buenos Aires del lunes {@link #LUNES}. */
    protected static Instant lunes(String horaLocal) {
        return dia(0, horaLocal);
    }

    /** Hora local de Buenos Aires, {@code offsetDias} días después del lunes {@link #LUNES}. */
    protected static Instant dia(int offsetDias, String horaLocal) {
        return LUNES.plusDays(offsetDias).atTime(LocalTime.parse(horaLocal)).atZone(BA).toInstant();
    }

    protected Long insertarTurno(Long pacienteId, Long prestacionId, EstadoTurno estado, Instant inicio,
            Instant fin) {
        Instant pendienteHasta = estado == EstadoTurno.PENDIENTE_CONFIRMACION
                ? FixedClockTestConfig.AHORA.plusSeconds(600) : null;
        return insertarTurno(pacienteId, prestacionId, estado, inicio, fin, pendienteHasta);
    }

    protected Long insertarTurno(Long pacienteId, Long prestacionId, EstadoTurno estado, Instant inicio,
            Instant fin, Instant pendienteHasta) {
        // V11 (C-08): un turno AUSENTE lleva siempre la marca (al terminar el turno) y el estado previo.
        boolean ausente = estado == EstadoTurno.AUSENTE;
        return jdbc.queryForObject("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, "
                + "estado, origen, pendiente_hasta, ausente_marcado_en, estado_previo_ausencia) VALUES (?, ?, ?, "
                + "?::timestamptz, ?::timestamptz, ?, 'ODONTOLOGO', ?::timestamptz, ?::timestamptz, ?) RETURNING id",
                Long.class, consultorioId(), pacienteId, prestacionId, inicio.toString(), fin.toString(),
                estado.name(), pendienteHasta == null ? null : pendienteHasta.toString(),
                ausente ? fin.toString() : null, ausente ? EstadoTurno.RESERVADO.name() : null);
    }

    protected int contarTurnos() {
        return jdbc.queryForObject("SELECT count(*) FROM turno WHERE paciente_id IN "
                + "(SELECT id FROM paciente WHERE dni LIKE '38%')", Integer.class);
    }

    protected String estadoDe(Long turnoId) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoId);
    }
}
