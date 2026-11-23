package ar.edu.odontogestion.domain.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V10 (C-10 D1): tablas, CHECK, único parcial de entradas vigentes y FK diferidas de C-03. */
class MigracionListaEsperaIT extends AbstractListaEsperaIT {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 23);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 30);

    @Test
    void existenLasDosTablas() {
        Integer tablas = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name IN "
                + "('entrada_lista_espera', 'aviso_hueco')", Integer.class);
        assertThat(tablas).isEqualTo(2);
    }

    @Test
    void elUnicoParcialRechazaUnaSegundaEntradaVigenteDelMismoPacienteYPrestacion() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarEntrada(paciente, prestacion, "ACTIVA", D1, D2);

        assertThatThrownBy(() -> insertarEntrada(paciente, prestacion, "ACTIVA", D1, D2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_entrada_lista_espera_vigente");
        assertThatThrownBy(() -> insertarEntrada(paciente, prestacion, "PENDIENTE_CONFIRMACION", D1, D2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaEntradaCubiertaOCanceladaNoImpideInscribirseDeNuevo() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarEntrada(paciente, prestacion, "CUBIERTA", D1, D2);
        insertarEntrada(paciente, prestacion, "CANCELADA", D1, D2);
        insertarEntrada(paciente, prestacion, "ACTIVA", D1, D2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrada_lista_espera WHERE paciente_id = ?",
                Integer.class, paciente)).isEqualTo(3);
    }

    @Test
    void losCheckRechazanEstadoRangoYPendienteSinVencimiento() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        assertThatThrownBy(() -> insertarEntrada(paciente, prestacion, "INVENTADO", D1, D2))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_entrada_estado");
        assertThatThrownBy(() -> insertarEntrada(paciente, prestacion, "ACTIVA", D2, D1))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_entrada_rango");
        assertThatThrownBy(() -> insertarEntrada(paciente, prestacion, "PENDIENTE_CONFIRMACION", D1, D2, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_entrada_pendiente_con_vencimiento");
    }

    @Test
    void elAvisoExigeRangoValidoYEsUnicoPorHuecoYEntrada() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long turno = insertarTurno(paciente, prestacion, ar.edu.odontogestion.domain.turno.EstadoTurno.CANCELADO,
                lunes("10:00"), lunes("10:30"));
        Long entrada = insertarEntrada(paciente, prestacion, "ACTIVA", D1, D2);
        insertarAviso(entrada, turno, prestacion, lunes("10:00"), lunes("10:30"), "ENVIADO", lunes("09:00"));

        assertThatThrownBy(() -> insertarAviso(entrada, turno, prestacion, lunes("10:00"), lunes("10:30"),
                "ENVIADO", lunes("09:00"))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_aviso_hueco_entrada");
        assertThatThrownBy(() -> insertarAviso(entrada, turno, prestacion, lunes("11:30"), lunes("11:00"),
                "ENVIADO", lunes("09:00"))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_aviso_rango");
        assertThatThrownBy(() -> insertarAviso(entrada, turno, prestacion, lunes("12:00"), lunes("12:30"),
                "RARO", lunes("09:00"))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_aviso_estado");
    }

    @Test
    void lasFkDiferidasDeTokenYNotificacionExisten() {
        Integer fks = jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE "
                + "constraint_type = 'FOREIGN KEY' AND constraint_name IN ('fk_token_acceso_aviso', "
                + "'fk_token_acceso_entrada', 'fk_notificacion_aviso', 'fk_notificacion_entrada')", Integer.class);
        assertThat(fks).isEqualTo(4);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO token_acceso (consultorio_id, token_hash, proposito, "
                + "aviso_id, vence_en) VALUES (?, decode('00ff', 'hex'), 'ACEPTAR_AVISO', 999999999, now())",
                consultorioId())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
