package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SolicitudReservaTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");

    @Test
    void publicaNacePendienteDeOrigenPublico() {
        SolicitudReserva s = SolicitudReserva.publica(7L, 3L, INICIO, 120);

        assertThat(s.pacienteId()).isEqualTo(7L);
        assertThat(s.prestacionId()).isEqualTo(3L);
        assertThat(s.inicio()).isEqualTo(INICIO);
        assertThat(s.origen()).isEqualTo(OrigenTurno.PUBLICA);
        assertThat(s.estadoInicial()).isEqualTo(EstadoTurno.PENDIENTE_CONFIRMACION);
    }

    @Test
    void laPoliticaPublicaExigeLaAnticipacionIndicada() {
        SolicitudReserva con120 = SolicitudReserva.publica(7L, 3L, INICIO, 120);
        SolicitudReserva con0 = SolicitudReserva.publica(7L, 3L, INICIO, 0);

        assertThat(con120.politica().minimoInicio(AHORA)).isEqualTo(Instant.parse("2026-11-20T14:00:00Z"));
        assertThat(con0.politica().minimoInicio(AHORA)).isEqualTo(AHORA);
    }

    @Test
    void delOdontologoSigueSiendoReservadaSinAnticipacion() {
        SolicitudReserva s = SolicitudReserva.delOdontologo(7L, 3L, INICIO);

        assertThat(s.estadoInicial()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(s.politica().minimoInicio(AHORA)).isEqualTo(AHORA);
    }
}
