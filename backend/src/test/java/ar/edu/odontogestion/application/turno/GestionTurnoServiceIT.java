package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.domain.turno.TransicionInvalidaException;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GestionTurnoServiceIT extends AbstractTurnoIT {

    @Autowired
    private GestionTurnoService gestion;
    @Autowired
    private ReservaTurnoService reservas;

    private Long paciente;
    private Long prestacion;

    private Long turno(EstadoTurno estado, String inicio, String fin) {
        if (paciente == null) {
            paciente = crearPaciente();
            prestacion = crearPrestacion(30);
        }
        return insertarTurno(paciente, prestacion, estado, lunes(inicio), lunes(fin));
    }

    private Map<String, Object> fila(Long id) {
        return jdbc.queryForMap("SELECT estado, inicio, fin, version, cancelado_por, motivo_cancelacion "
                + "FROM turno WHERE id = ?", id);
    }

    private static Instant instante(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    // ---- mover ----

    @Test
    void moverAUnHuecoLibreCambiaElRangoYConservaElEstado() {
        Long id = turno(EstadoTurno.CONFIRMADO, "10:00", "10:30");

        TurnoDto dto = gestion.mover(id, lunes("11:00"));

        assertThat(dto.estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(dto.inicio()).isEqualTo(lunes("11:00"));
        assertThat(dto.fin()).isEqualTo(lunes("11:30"));
        assertThat(dto.version()).isEqualTo(1L);
        assertThat(instante(fila(id).get("inicio"))).isEqualTo(lunes("11:00"));
    }

    @Test
    void moverParcialmenteSobreSuPropioRangoFuncionaYLiberaElViejo() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");

        TurnoDto dto = gestion.mover(id, lunes("10:15"));

        assertThat(dto.inicio()).isEqualTo(lunes("10:15"));
        assertThat(dto.fin()).isEqualTo(lunes("10:45"));
        // El rango viejo [10:00, 10:15) quedó libre en el mismo cambio.
        assertThat(reservas.reservar(SolicitudReserva.delOdontologo(paciente, prestacion, lunes("09:45"))).fin())
                .isEqualTo(lunes("10:15"));
    }

    @Test
    void moverAUnHuecoOcupadoDa409YElHorarioOriginalQuedaIntacto() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");
        turno(EstadoTurno.CONFIRMADO, "11:00", "11:30");

        assertThatThrownBy(() -> gestion.mover(id, lunes("11:15"))).isInstanceOf(HuecoNoDisponibleException.class);

        Map<String, Object> fila = fila(id);
        assertThat(instante(fila.get("inicio"))).isEqualTo(lunes("10:00"));
        assertThat(instante(fila.get("fin"))).isEqualTo(lunes("10:30"));
        assertThat(fila.get("version")).isEqualTo(0L);
    }

    @Test
    void moverFueraDeHorarioODentroDeUnBloqueoDa422ConCampoInicio() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");
        jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) VALUES (?, ?::timestamptz, "
                + "?::timestamptz, 'IT-Turno bloqueo')", consultorioId(), lunes("11:00").toString(),
                lunes("12:00").toString());

        for (String inicio : new String[] {"12:45", "11:30", "10:10"}) {
            assertThatThrownBy(() -> gestion.mover(id, lunes(inicio)))
                    .as(inicio)
                    .isInstanceOfSatisfying(ValidacionDominioException.class,
                            e -> assertThat(e.errores().get(0).field()).isEqualTo("inicio"));
        }
        assertThat(instante(fila(id).get("inicio"))).isEqualTo(lunes("10:00"));
    }

    @Test
    void moverNoExigeAnticipacionMinimaAlOdontologoPeroNoAdmiteElPasado() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");
        reloj.set(lunes("09:30"));

        assertThat(gestion.mover(id, lunes("10:30")).inicio()).isEqualTo(lunes("10:30"));
        assertThatThrownBy(() -> gestion.mover(id, lunes("09:00")))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void moverExpiraAntesLasPendientesVencidasDelRangoDestino() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");
        Long vencida = insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("11:00"),
                lunes("11:30"), reloj.instant().minusSeconds(30));

        TurnoDto dto = gestion.mover(id, lunes("11:00"));

        assertThat(dto.inicio()).isEqualTo(lunes("11:00"));
        assertThat(estadoDe(vencida)).isEqualTo("EXPIRADO");
    }

    @Test
    void moverUnTurnoInexistenteODeOtroConsultorioDa404() {
        Long otro = crearOtroConsultorio();
        Long pacienteAjeno = crearPaciente(otro, true);
        Long prestacionAjena = crearPrestacion(otro, 30, true);
        Long ajeno = jdbc.queryForObject("INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, "
                + "estado, origen) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'RESERVADO', 'ODONTOLOGO') "
                + "RETURNING id", Long.class, otro, pacienteAjeno, prestacionAjena, lunes("10:00").toString(),
                lunes("10:30").toString());

        assertThatThrownBy(() -> gestion.mover(999_999_999L, lunes("11:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> gestion.mover(ajeno, lunes("11:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> gestion.cancelar(ajeno, null)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> gestion.marcarAtendido(ajeno)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void moverUnTurnoEnEstadoNoMovibleDa422() {
        for (EstadoTurno estado : new EstadoTurno[] {EstadoTurno.PENDIENTE_CONFIRMACION, EstadoTurno.CANCELADO,
                EstadoTurno.EXPIRADO, EstadoTurno.AUSENTE, EstadoTurno.ATENDIDO}) {
            Long id = turno(estado, estado == EstadoTurno.PENDIENTE_CONFIRMACION ? "09:00" : "10:00",
                    estado == EstadoTurno.PENDIENTE_CONFIRMACION ? "09:30" : "10:30");

            assertThatThrownBy(() -> gestion.mover(id, lunes("15:00")))
                    .as(estado.name()).isInstanceOf(TransicionInvalidaException.class);

            assertThat(estadoDe(id)).isEqualTo(estado.name());
            jdbc.update("DELETE FROM turno WHERE id = ?", id);
        }
    }

    // ---- cancelar y atender ----

    @Test
    void cancelarRegistraQuienYElMotivoYLiberaElHueco() {
        Long id = turno(EstadoTurno.RESERVADO, "10:00", "10:30");

        TurnoDto dto = gestion.cancelar(id, "Imprevisto del consultorio");

        assertThat(dto.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(dto.canceladoPor()).isEqualTo(CanceladoPor.ODONTOLOGO);
        assertThat(dto.motivoCancelacion()).isEqualTo("Imprevisto del consultorio");
        // Hueco reutilizable: un nuevo turno en el mismo rango se acepta.
        assertThat(reservas.reservar(SolicitudReserva.delOdontologo(paciente, prestacion, lunes("10:00"))).estado())
                .isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void cancelarSinMotivoDejaElMotivoVacio() {
        Long id = turno(EstadoTurno.CONFIRMADO, "10:00", "10:30");

        TurnoDto dto = gestion.cancelar(id, null);

        assertThat(dto.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(dto.motivoCancelacion()).isNull();
        assertThat(fila(id).get("motivo_cancelacion")).isNull();
    }

    @Test
    void cancelarUnTurnoAtendidoOYaCanceladoDa422() {
        Long atendido = turno(EstadoTurno.ATENDIDO, "10:00", "10:30");
        Long cancelado = turno(EstadoTurno.CANCELADO, "11:00", "11:30");

        assertThatThrownBy(() -> gestion.cancelar(atendido, "x")).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> gestion.cancelar(cancelado, "x")).isInstanceOf(TransicionInvalidaException.class);
        assertThat(estadoDe(atendido)).isEqualTo("ATENDIDO");
    }

    @Test
    void marcarAtendidoDesdeReservadoYConfirmado() {
        Long reservado = turno(EstadoTurno.RESERVADO, "10:00", "10:30");
        Long confirmado = turno(EstadoTurno.CONFIRMADO, "11:00", "11:30");

        assertThat(gestion.marcarAtendido(reservado).estado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(gestion.marcarAtendido(confirmado).estado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(estadoDe(reservado)).isEqualTo("ATENDIDO");
    }

    @Test
    void marcarAtendidoDesdeCanceladoOPendienteDa422YNoCambiaElEstado() {
        Long cancelado = turno(EstadoTurno.CANCELADO, "10:00", "10:30");
        Long pendiente = turno(EstadoTurno.PENDIENTE_CONFIRMACION, "11:00", "11:30");

        assertThatThrownBy(() -> gestion.marcarAtendido(cancelado)).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> gestion.marcarAtendido(pendiente)).isInstanceOf(TransicionInvalidaException.class);

        assertThat(estadoDe(cancelado)).isEqualTo("CANCELADO");
        assertThat(estadoDe(pendiente)).isEqualTo("PENDIENTE_CONFIRMACION");
    }
}
