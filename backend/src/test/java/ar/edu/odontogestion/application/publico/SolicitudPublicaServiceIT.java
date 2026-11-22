package ar.edu.odontogestion.application.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Servicio transaccional de la solicitud pública (C-09 D2). Los pacientes nuevos usan DNI {@code 38…} para que la
 * limpieza de {@link AbstractTurnoIT} los borre. "Ahora" = viernes 2026-11-20 09:00 BA; vencimiento de pendientes
 * del seed: 30 minutos; anticipación pública: 120 minutos.
 */
class SolicitudPublicaServiceIT extends AbstractTurnoIT {

    private static final String DNI_NUEVO = "38990001";

    @Autowired
    private SolicitudPublicaService servicio;

    private static DatosSolicitudPublica solicitud(Long prestacion, Instant inicio, String dni, String email) {
        return new DatosSolicitudPublica(prestacion, inicio,
                new DatosPaciente("Beto", "Ficticio", dni, null, "+54 11 4444-0000", email, null));
    }

    private List<Map<String, Object>> turnosDelDni(String dniNormalizado) {
        return jdbc.queryForList("SELECT t.id, t.estado, t.origen, t.pendiente_hasta, t.inicio FROM turno t "
                + "JOIN paciente p ON p.id = t.paciente_id WHERE p.dni = ?", dniNormalizado);
    }

    private int notificacionesConfirmarSolicitud(Long turnoId) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ? "
                + "AND tipo = 'CONFIRMAR_SOLICITUD'", Integer.class, turnoId);
    }

    private int cantidadDePacientes(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    @Test
    void dniNuevoCreaElPacienteYUnTurnoPendientePublicoConVencimientoYNotificacion() {
        Long prestacion = crearPrestacion(30);

        ResultadoSolicitud resultado = servicio.solicitar(
                solicitud(prestacion, lunes("10:00"), "38.990.001", "Nuevo.Beto@Ejemplo.test"));

        assertThat(resultado).isEqualTo(ResultadoSolicitud.PENDIENTE_CREADA);
        assertThat(cantidadDePacientes(DNI_NUEVO)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email::text FROM paciente WHERE dni = ?", String.class, DNI_NUEVO))
                .isEqualTo("nuevo.beto@ejemplo.test");
        Map<String, Object> turno = turnosDelDni(DNI_NUEVO).get(0);
        assertThat(turno).containsEntry("estado", "PENDIENTE_CONFIRMACION").containsEntry("origen", "PUBLICA");
        assertThat(((java.sql.Timestamp) turno.get("pendiente_hasta")).toInstant())
                .isEqualTo(reloj.instant().plusSeconds(30 * 60));
        assertThat(notificacionesConfirmarSolicitud(((Number) turno.get("id")).longValue())).isEqualTo(1);
    }

    @Test
    void dniExistenteReutilizaAlPacienteSinTocarSusDatosYNotificaAlEmailRegistrado() {
        Long existente = crearPaciente();
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, existente);
        String emailRegistrado = jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class,
                existente);
        Long prestacion = crearPrestacion(30);

        servicio.solicitar(solicitud(prestacion, lunes("10:00"), dni, "otra@ejemplo.test"));

        assertThat(jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, existente))
                .isEqualTo(emailRegistrado);
        assertThat(jdbc.queryForObject("SELECT nombre FROM paciente WHERE id = ?", String.class, existente))
                .isEqualTo("Ana");
        assertThat(cantidadDePacientes(dni)).isEqualTo(1);
        Long turnoId = ((Number) turnosDelDni(dni).get(0).get("id")).longValue();
        assertThat(jdbc.queryForObject("SELECT destinatario::text FROM notificacion WHERE turno_id = ?",
                String.class, turnoId)).isEqualTo(emailRegistrado);
    }

    @Test
    void pacienteDadoDeBajaDevuelveElResultadoGenericoSinTurnoNiNotificacion() {
        Long deBaja = crearPacienteDeBaja();
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, deBaja);
        Long prestacion = crearPrestacion(30);

        ResultadoSolicitud resultado = servicio.solicitar(
                solicitud(prestacion, lunes("10:00"), dni, "baja@ejemplo.test"));

        assertThat(resultado).isEqualTo(ResultadoSolicitud.PACIENTE_DE_BAJA);
        assertThat(turnosDelDni(dni)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT activo FROM paciente WHERE id = ?", Boolean.class, deBaja)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'CONFIRMAR_SOLICITUD' "
                + "AND turno_id IN (SELECT id FROM turno WHERE prestacion_id = ?)", Integer.class, prestacion))
                .isZero();
    }

    @Test
    void unaPendienteVencidaDelMismoHorarioQuedaExpiradaAntesDeSolicitar() {
        Long otro = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long vencida = insertarTurno(otro, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("10:00"),
                lunes("10:30"), reloj.instant().minusSeconds(60));

        ResultadoSolicitud resultado = servicio.solicitar(
                solicitud(prestacion, lunes("10:00"), DNI_NUEVO, "nuevo@ejemplo.test"));

        assertThat(resultado).isEqualTo(ResultadoSolicitud.PENDIENTE_CREADA);
        assertThat(estadoDe(vencida)).isEqualTo("EXPIRADO");
        assertThat(turnosDelDni(DNI_NUEVO)).hasSize(1);
    }

    @Test
    void laTerceraPendienteVigenteDelMismoDniDa429SinEfectosYConEsperaDelVencimiento() {
        Long paciente = crearPaciente();
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, paciente);
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"));

        assertThatThrownBy(() -> servicio.solicitar(solicitud(prestacion, lunes("11:00"), dni, "x@ejemplo.test")))
                .isInstanceOfSatisfying(DemasiadasSolicitudesException.class,
                        e -> assertThat(e.retryAfterSegundos()).isEqualTo(30 * 60));

        assertThat(turnosDelDni(dni)).hasSize(2);
    }

    @Test
    void laTerceraPendienteVigenteDelMismoEmailConOtroDniDa429YNoCreaPaciente() {
        Long paciente = crearPaciente();
        String email = jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, paciente);
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"));

        assertThatThrownBy(() -> servicio.solicitar(solicitud(prestacion, lunes("11:00"), DNI_NUEVO, email)))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        assertThat(cantidadDePacientes(DNI_NUEVO)).isZero();
    }

    @Test
    void pendientesVencidasOConfirmadasNoCuentanParaElMaximo() {
        Long paciente = crearPaciente();
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, paciente);
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"),
                reloj.instant().minusSeconds(1));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("09:30"), lunes("10:00"));
        insertarTurno(paciente, prestacion, EstadoTurno.CONFIRMADO, lunes("10:00"), lunes("10:30"));

        ResultadoSolicitud resultado = servicio.solicitar(solicitud(prestacion, lunes("11:00"), dni, "x@ejemplo.test"));

        assertThat(resultado).isEqualTo(ResultadoSolicitud.PENDIENTE_CREADA);
    }

    @Test
    void horarioInvalidoDa422ConCampoInicioYNoDejaPacienteNiTurno() {
        Long prestacion = crearPrestacion(30);
        crearBloqueo(lunes("14:00"), lunes("16:00"));

        for (Instant inicio : List.of(dia(6, "10:00"), lunes("14:30"), reloj.instant().plusSeconds(3600),
                lunes("13:00"))) {
            assertThatThrownBy(() -> servicio.solicitar(solicitud(prestacion, inicio, DNI_NUEVO, "n@ejemplo.test")))
                    .as("inicio %s", inicio).isInstanceOfSatisfying(ValidacionDominioException.class,
                            e -> assertThat(e.errores()).anyMatch(err -> err.field().equals("inicio")));
        }

        assertThat(cantidadDePacientes(DNI_NUEVO)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE prestacion_id = ?", Integer.class,
                prestacion)).isZero();
    }

    @Test
    void prestacionInactivaDa422EInexistenteDa404() {
        Long inactiva = crearPrestacionInactiva(30);

        assertThatThrownBy(() -> servicio.solicitar(solicitud(inactiva, lunes("10:00"), DNI_NUEVO, "n@ejemplo.test")))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> servicio.solicitar(
                solicitud(999_999_999L, lunes("10:00"), DNI_NUEVO, "n@ejemplo.test")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(cantidadDePacientes(DNI_NUEVO)).isZero();
    }

    private void crearBloqueo(Instant desde, Instant hasta) {
        jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) VALUES (?, ?::timestamptz, "
                + "?::timestamptz, 'IT-Turno bloqueo')", consultorioId(), desde.toString(), hasta.toString());
    }
}
