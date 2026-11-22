package ar.edu.odontogestion.domain.turno;

import static ar.edu.odontogestion.domain.turno.EstadoTurno.ATENDIDO;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.AUSENTE;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.CANCELADO;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.CONFIRMADO;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.EXPIRADO;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.PENDIENTE_CONFIRMACION;
import static ar.edu.odontogestion.domain.turno.EstadoTurno.RESERVADO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Matriz de transiciones de RN-TU-02 (7x7) y predicado de ocupación de agenda (design D2). */
class EstadoTurnoTest {

    private static final Map<EstadoTurno, Set<EstadoTurno>> VALIDAS = Map.of(
            PENDIENTE_CONFIRMACION, Set.of(RESERVADO, EXPIRADO, CANCELADO),
            RESERVADO, Set.of(CONFIRMADO, CANCELADO, AUSENTE, ATENDIDO),
            CONFIRMADO, Set.of(CANCELADO, AUSENTE, ATENDIDO),
            CANCELADO, Set.of(),
            EXPIRADO, Set.of(),
            AUSENTE, Set.of(),
            ATENDIDO, Set.of());

    static Stream<Arguments> todasLasCombinaciones() {
        return Stream.of(EstadoTurno.values()).flatMap(origen -> Stream.of(EstadoTurno.values())
                .map(destino -> Arguments.of(origen, destino, VALIDAS.get(origen).contains(destino))));
    }

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @MethodSource("todasLasCombinaciones")
    void puedePasarASegunLaMatrizDeRnTu02(EstadoTurno origen, EstadoTurno destino, boolean esperado) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(esperado);
    }

    @Test
    void hayExactamenteSieteEstados() {
        assertThat(EstadoTurno.values()).hasSize(7);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class, names = {"CANCELADO", "EXPIRADO"})
    void canceladoYExpiradoLiberanLaAgenda(EstadoTurno estado) {
        assertThat(estado.ocupaAgenda()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class,
            names = {"PENDIENTE_CONFIRMACION", "RESERVADO", "CONFIRMADO", "ATENDIDO", "AUSENTE"})
    void losDemasEstadosOcupanAgendaIncluidosAtendidoYAusente(EstadoTurno estado) {
        assertThat(estado.ocupaAgenda()).isTrue();
    }

    @Test
    void losEstadosFinalesNoTienenSalidas() {
        List<EstadoTurno> finales = List.of(CANCELADO, EXPIRADO, AUSENTE, ATENDIDO);
        for (EstadoTurno f : finales) {
            for (EstadoTurno destino : EstadoTurno.values()) {
                assertThat(f.puedePasarA(destino)).as(f + " -> " + destino).isFalse();
            }
        }
    }
}
