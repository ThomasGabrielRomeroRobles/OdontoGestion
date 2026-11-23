package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractTurnoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Conteo de pendientes vigentes por DNI o email para el máximo de la solicitud pública (C-09 4.3). */
class TurnoRepositoryPendientesIT extends AbstractTurnoIT {

    @Autowired
    private TurnoRepository turnos;

    private String dniDe(Long paciente) {
        return jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, paciente);
    }

    private String emailDe(Long paciente) {
        return jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, paciente);
    }

    private long contar(String dni, String email, Long consultorioId) {
        return turnos.contarPendientesVigentesPorDniOEmail(consultorioId, dni, email, reloj.instant());
    }

    @Test
    void cuentaLasPendientesVigentesPorDni() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"));

        assertThat(contar(dniDe(paciente), "nadie@ejemplo.test", consultorioId())).isEqualTo(2);
        assertThat(contar("38999999", "nadie@ejemplo.test", consultorioId())).isZero();
    }

    @Test
    void cuentaPorEmailDePacientesConOtroDniYSumaAmbosOrigenesSinDuplicar() {
        Long a = crearPaciente();
        Long b = crearPaciente();
        jdbc.update("UPDATE paciente SET email = ? WHERE id IN (?, ?)", "compartido@ejemplo.test", a, b);
        Long prestacion = crearPrestacion(30);
        insertarTurno(a, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"));
        insertarTurno(b, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"));

        assertThat(contar("38999999", "compartido@ejemplo.test", consultorioId())).isEqualTo(2);
        // El DNI de a y el email compartido apuntan a los mismos turnos: se cuentan una sola vez.
        assertThat(contar(dniDe(a), "compartido@ejemplo.test", consultorioId())).isEqualTo(2);
        assertThat(emailDe(a)).isEqualTo("compartido@ejemplo.test");
    }

    @Test
    void vencidasConfirmadasYCanceladasNoCuentan() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:00"), lunes("09:30"),
                reloj.instant().minusSeconds(1));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"),
                reloj.instant());
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, lunes("10:30"), lunes("11:00"));

        assertThat(contar(dniDe(paciente), emailDe(paciente), consultorioId())).isZero();
    }

    @Test
    void ignoraLosTurnosDeOtroConsultorio() {
        Long otro = crearOtroConsultorio();
        Long paciente = crearPaciente(otro, true);
        Long prestacion = crearPrestacion(otro, 30, true);
        jdbc.update("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, estado, origen, "
                + "pendiente_hasta) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'PENDIENTE_CONFIRMACION', "
                + "'PUBLICA', ?::timestamptz)", otro, paciente, prestacion, lunes("09:00").toString(),
                lunes("09:30").toString(), reloj.instant().plusSeconds(600).toString());

        assertThat(contar(dniDe(paciente), emailDe(paciente), consultorioId())).isZero();
        assertThat(contar(dniDe(paciente), emailDe(paciente), otro)).isEqualTo(1);
    }
}
