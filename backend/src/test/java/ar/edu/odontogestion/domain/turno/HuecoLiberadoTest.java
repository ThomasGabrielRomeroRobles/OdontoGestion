package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HuecoLiberadoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");

    private Turno turno(long turnoId, long consultorioId, long prestacionId) {
        Consultorio consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        ReflectionTestUtils.setField(consultorio, "id", consultorioId);
        Paciente paciente = Paciente.nuevo(consultorio,
                new DatosPaciente("Ana", "Ficticia", "30111222", null, "+54 11 5555-0000", "ana@ejemplo.test", null));
        Prestacion prestacion = Prestacion.nueva(consultorio, "Consulta", 30);
        ReflectionTestUtils.setField(prestacion, "id", prestacionId);
        Turno t = Turno.reservado(consultorio, paciente, prestacion, INICIO, OrigenTurno.ODONTOLOGO);
        ReflectionTestUtils.setField(t, "id", turnoId);
        return t;
    }

    @Test
    void deArmaElEventoConLosIdsDelTurnoYElRangoLiberado() {
        Turno t = turno(41L, 1L, 3L);
        Hueco rango = new Hueco(INICIO, INICIO.plusSeconds(1800));

        assertThat(HuecoLiberado.de(t, rango))
                .isEqualTo(new HuecoLiberado(1L, 41L, 3L, INICIO, INICIO.plusSeconds(1800)));
    }

    @Test
    void usaElRangoPedidoYNoElActualDelTurno() {
        Turno t = turno(42L, 2L, 5L);
        Hueco anterior = new Hueco(INICIO.minusSeconds(3600), INICIO.minusSeconds(1800));

        HuecoLiberado evento = HuecoLiberado.de(t, anterior);

        assertThat(evento.consultorioId()).isEqualTo(2L);
        assertThat(evento.turnoOrigenId()).isEqualTo(42L);
        assertThat(evento.prestacionId()).isEqualTo(5L);
        assertThat(evento.inicio()).isEqualTo(anterior.inicio());
        assertThat(evento.fin()).isEqualTo(anterior.fin());
    }
}
