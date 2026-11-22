package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TurnoComandosIT extends AbstractTurnoIT {

    @Autowired
    private TurnoComandos comandos;

    @Test
    void unConflictoTraeHasta3AlternativasLibresDesdeElInicioPedido() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        assertThatThrownBy(() -> comandos.crear(paciente, prestacion, lunes("10:00")))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class, e -> assertThat(e.alternativas())
                        .containsExactly(new Hueco(lunes("10:30"), lunes("11:00")),
                                new Hueco(lunes("10:45"), lunes("11:15")),
                                new Hueco(lunes("11:00"), lunes("11:30"))));
        assertThat(contarTurnos()).isEqualTo(1);
    }

    @Test
    void lasAlternativasEsquivanOtrosTurnosYSeCalculanParaLaMismaPrestacion() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(60);
        insertarTurno(paciente, prestacion, EstadoTurno.CONFIRMADO, lunes("10:00"), lunes("11:00"));
        insertarTurno(paciente, prestacion, EstadoTurno.ATENDIDO, lunes("11:00"), lunes("12:00"));

        assertThatThrownBy(() -> comandos.crear(paciente, prestacion, lunes("10:00")))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class, e -> assertThat(e.alternativas())
                        .extracting(Hueco::inicio)
                        .containsExactly(lunes("12:00"), lunes("14:00"), lunes("14:15"))
                        .allSatisfy(i -> assertThat(i).isAfterOrEqualTo(lunes("10:00"))));
    }

    @Test
    void sinHuecosEnLosSiguientes7DiasLaListaEsVacia() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));
        jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) VALUES (?, ?::timestamptz, "
                + "?::timestamptz, 'IT-Turno bloqueo total')", consultorioId(), lunes("10:30").toString(),
                dia(9, "00:00").toString());

        assertThatThrownBy(() -> comandos.crear(paciente, prestacion, lunes("10:00")))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class,
                        e -> assertThat(e.alternativas()).isEmpty());
    }

    @Test
    void unaCreacionExitosaDevuelveElTurno() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        TurnoDto dto = comandos.crear(paciente, prestacion, lunes("09:00"));

        assertThat(dto.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(contarTurnos()).isEqualTo(1);
    }
}
