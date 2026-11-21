package ar.edu.odontogestion.domain.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PacienteTest {

    private final Consultorio consultorio = Consultorio.nuevo("C Pacientes", "UTC");

    private static DatosPaciente datos(String dni, String email, String direccion, LocalDate nacimiento) {
        return new DatosPaciente("  Ana ", " Pérez  ", dni, nacimiento, " +54 11 5555-0001 ", email, direccion);
    }

    @Test
    void nuevoNormalizaYRecortaLosDatos() {
        Paciente p = Paciente.nuevo(consultorio,
                datos("30.123.456", " Ana.Perez@Ejemplo.TEST ", " Calle Ficticia 1 ", LocalDate.of(1990, 5, 1)));

        assertThat(p.getConsultorio()).isSameAs(consultorio);
        assertThat(p.getNombre()).isEqualTo("Ana");
        assertThat(p.getApellido()).isEqualTo("Pérez");
        assertThat(p.getDni()).isEqualTo("30123456");
        assertThat(p.getTelefono()).isEqualTo("+54 11 5555-0001");
        assertThat(p.getEmail()).isEqualTo("ana.perez@ejemplo.test");
        assertThat(p.getDireccion()).isEqualTo("Calle Ficticia 1");
        assertThat(p.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 1));
    }

    @Test
    void nuevoArrancaActivoYSinAusencias() {
        Paciente p = Paciente.nuevo(consultorio, datos("30123456", "a@ejemplo.test", null, null));

        assertThat(p.getCantidadAusencias()).isZero();
        assertThat(p.isActivo()).isTrue();
    }

    @Test
    void opcionalesAusentes() {
        Paciente p = Paciente.nuevo(consultorio, datos("30123456", "a@ejemplo.test", null, null));

        assertThat(p.getDireccion()).isEmpty();
        assertThat(p.getFechaNacimiento()).isNull();
    }

    @Test
    void dniInvalidoNoCreaElPaciente() {
        assertThatThrownBy(() -> Paciente.nuevo(consultorio, datos("123", "a@ejemplo.test", null, null)))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void actualizarDatosReemplazaLosAdministrativosConLaMismaNormalizacion() {
        Paciente p = Paciente.nuevo(consultorio, datos("30123456", "a@ejemplo.test", "Vieja 1", null));

        p.actualizarDatos(new DatosPaciente(" Luis ", " Gómez ", "7.123.456", LocalDate.of(1985, 1, 2),
                " 111 ", " Luis@Ejemplo.TEST ", null));

        assertThat(p.getNombre()).isEqualTo("Luis");
        assertThat(p.getApellido()).isEqualTo("Gómez");
        assertThat(p.getDni()).isEqualTo("7123456");
        assertThat(p.getTelefono()).isEqualTo("111");
        assertThat(p.getEmail()).isEqualTo("luis@ejemplo.test");
        assertThat(p.getDireccion()).isEmpty();
        assertThat(p.getFechaNacimiento()).isEqualTo(LocalDate.of(1985, 1, 2));
    }

    @Test
    void actualizarDatosNoTocaAusenciasNiActivo() {
        Paciente p = Paciente.nuevo(consultorio, datos("30123456", "a@ejemplo.test", null, null));

        p.actualizarDatos(datos("30123457", "b@ejemplo.test", null, null));

        assertThat(p.getCantidadAusencias()).isZero();
        assertThat(p.isActivo()).isTrue();
    }
}
