package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regla dura #10: la agenda no necesita ni expone datos personales del paciente más allá de nombre y apellido. C-08
 * suma el contador de ausencias (dato operativo, no de contacto) y el instante de la marca de ausente.
 */
class TurnoDtoTest {

    private static List<String> campos(Class<? extends Record> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void elPacienteSoloExponeIdNombreApellidoYCantidadDeAusencias() {
        assertThat(campos(TurnoDto.PacienteResumen.class))
                .containsExactly("id", "nombre", "apellido", "cantidadAusencias")
                .doesNotContain("dni", "email", "telefono");
    }

    @Test
    void elTurnoNoTieneCamposDePersonalesNiClinicos() {
        assertThat(campos(TurnoDto.class)).containsExactly("id", "inicio", "fin", "estado", "origen", "paciente",
                "prestacion", "pendienteHasta", "canceladoPor", "motivoCancelacion", "version", "ausenteMarcadoEn");
        assertThat(campos(TurnoDto.class)).doesNotContain("dni", "email", "telefono", "token", "diagnostico");
        assertThat(campos(TurnoDto.PrestacionResumen.class)).containsExactly("id", "nombre");
    }
}
