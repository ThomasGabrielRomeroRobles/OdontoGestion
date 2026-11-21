package ar.edu.odontogestion.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final String EMAIL = "ana@ejemplo.test";
    private static final String ACTUAL = "Ficticia-Actual-1";

    @Mock
    private UsuarioRepository usuarios;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private Usuario usuario;
    private String hashOriginal;
    private PerfilService servicio;

    @BeforeEach
    void preparar() {
        hashOriginal = encoder.encode(ACTUAL);
        usuario = Usuario.nuevo(Consultorio.nuevo("C", "UTC"), EMAIL, hashOriginal, "Ana", "Perez", "MN 1");
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        servicio = new PerfilService(usuarios, encoder);
    }

    private static void esperarErrorEn(Throwable e, String campo) {
        assertThat(e).isInstanceOfSatisfying(ValidacionDominioException.class,
                v -> assertThat(v.errores()).extracting(ErrorCampo::field).containsExactly(campo));
    }

    @Test
    void obtenerDevuelveElUsuarioDeLaSesion() {
        assertThat(servicio.obtener(EMAIL))
                .isEqualTo(new PerfilVista(EMAIL, "Ana", "Perez", "MN 1"));
    }

    @Test
    void elEmailDeBusquedaSeNormalizaConTrimYMinusculas() {
        assertThat(servicio.obtener("  ANA@Ejemplo.TEST ").email()).isEqualTo(EMAIL);
    }

    @Test
    void actualizarCambiaNombreApellidoYMatriculaPeroNoElEmail() {
        PerfilVista vista = servicio.actualizar(EMAIL, new DatosPerfil("Beatriz", "Gomez", "MP 9"));

        assertThat(vista).isEqualTo(new PerfilVista(EMAIL, "Beatriz", "Gomez", "MP 9"));

        assertThat(usuario.getNombre()).isEqualTo("Beatriz");
        assertThat(usuario.getApellido()).isEqualTo("Gomez");
        assertThat(usuario.getMatricula()).isEqualTo("MP 9");
        assertThat(usuario.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void cambioDePasswordConLaActualCorrectaGuardaUnHashNuevoVerificable() {
        servicio.cambiarPassword(EMAIL, ACTUAL, "Ficticia-Nueva-2");

        assertThat(usuario.getPasswordHash()).isNotEqualTo(hashOriginal);
        assertThat(encoder.matches("Ficticia-Nueva-2", usuario.getPasswordHash())).isTrue();
        assertThat(encoder.matches(ACTUAL, usuario.getPasswordHash())).isFalse();
    }

    @Test
    void conLaActualIncorrectaFallaEnPasswordActualYElHashNoCambia() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, "Incorrecta-123", "Ficticia-Nueva-2"))
                .satisfies(e -> esperarErrorEn(e, "passwordActual"));

        assertThat(usuario.getPasswordHash()).isEqualTo(hashOriginal);
    }

    @Test
    void unaNuevaConMenosDeOchoCaracteresFallaEnPasswordNueva() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "corta12"))
                .satisfies(e -> esperarErrorEn(e, "passwordNueva"));

        assertThat(usuario.getPasswordHash()).isEqualTo(hashOriginal);
    }

    @Test
    void unaNuevaIgualALaActualFallaEnPasswordNueva() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, ACTUAL, ACTUAL))
                .satisfies(e -> esperarErrorEn(e, "passwordNueva"));

        assertThat(usuario.getPasswordHash()).isEqualTo(hashOriginal);
    }

    @Test
    void unaNuevaEnBlancoFallaEnPasswordNueva() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "         "))
                .satisfies(e -> esperarErrorEn(e, "passwordNueva"));
    }

    @Test
    void unaNuevaDeMasDeSetentaYDosBytesFalla() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "a".repeat(73)))
                .satisfies(e -> esperarErrorEn(e, "passwordNueva"));
    }

    @Test
    void cuarentaEnesSonOchentaBytesYFallanPeroTreintaYSeisSonSetentaYDosYSeAceptan() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "ñ".repeat(40)))
                .satisfies(e -> esperarErrorEn(e, "passwordNueva"));
        assertThat(usuario.getPasswordHash()).isEqualTo(hashOriginal);

        assertThatCode(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "ñ".repeat(36))).doesNotThrowAnyException();
        assertThat(encoder.matches("ñ".repeat(36), usuario.getPasswordHash())).isTrue();
    }

    @Test
    void unaNuevaDeSetentaYDosBytesExactosEnAsciiSeAcepta() {
        assertThatCode(() -> servicio.cambiarPassword(EMAIL, ACTUAL, "a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void actualIncorrectaYNuevaCortaReportanAmbosCampos() {
        assertThatThrownBy(() -> servicio.cambiarPassword(EMAIL, "Incorrecta-123", "corta"))
                .isInstanceOfSatisfying(ValidacionDominioException.class, v ->
                        assertThat(v.errores()).extracting(ErrorCampo::field)
                                .containsExactlyInAnyOrder("passwordActual", "passwordNueva"));
    }
}
