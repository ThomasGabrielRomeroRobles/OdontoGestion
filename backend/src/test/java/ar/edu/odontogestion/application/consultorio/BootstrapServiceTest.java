package ar.edu.odontogestion.application.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import ar.edu.odontogestion.infrastructure.config.BootstrapProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BootstrapServiceTest {

    private static final String EMAIL = "odontologo@consultorio.example";
    private static final String PASSWORD = "Ficticia-Test-123";

    @Mock
    private ConsultorioRepository consultorios;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private ConsultorioActual consultorioActual;

    @Mock
    private PasswordEncoder encoder;

    private Consultorio consultorio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        lenient().when(consultorioActual.id()).thenReturn(1L);
        lenient().when(consultorios.findById(1L)).thenReturn(Optional.of(consultorio));
    }

    private BootstrapService servicio(String email, String password) {
        return new BootstrapService(usuarios, consultorios, consultorioActual, encoder,
                new BootstrapProperties(email, password));
    }

    @Test
    void sinUsuariosCreaAlOdontologoActivoConEmailNormalizadoHashYConsultorioActual() {
        when(usuarios.count()).thenReturn(0L);
        when(encoder.encode(PASSWORD)).thenReturn("$2a$12$hash");

        servicio("  Odontologo@Consultorio.EXAMPLE ", PASSWORD).ejecutar();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captor.capture());
        Usuario u = captor.getValue();
        assertThat(u.getEmail()).isEqualTo(EMAIL);
        assertThat(u.getPasswordHash()).isEqualTo("$2a$12$hash").doesNotContain("Ficticia");
        assertThat(u.isActivo()).isTrue();
        assertThat(u.getConsultorio()).isSameAs(consultorio);
        verify(consultorios, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {EMAIL, "otro@consultorio.example"})
    void conUsuariosExistentesNoHaceNadaAunqueElEmailDeBootstrapSeaDistinto(String emailDeBootstrap) {
        when(usuarios.count()).thenReturn(1L);

        servicio(emailDeBootstrap, "OtraClave-999").ejecutar();

        verify(usuarios, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void conUsuariosExistentesYSinContrasenaArrancaSinError(String password) {
        when(usuarios.count()).thenReturn(1L);

        servicio(EMAIL, password).ejecutar();

        verify(usuarios, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc1234"})
    void sinUsuariosYSinContrasenaValidaFallaSinMostrarElValor(String password, CapturedOutput output) {
        when(usuarios.count()).thenReturn(0L);

        assertThatThrownBy(() -> servicio(EMAIL, password).ejecutar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ODONTOLOGO_PASSWORD")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("abc1234"));
        verify(usuarios, never()).save(any());
        assertThat(output.getAll()).doesNotContain("abc1234");
    }

    @Test
    void unaContrasenaDeMasDeSetentaYDosBytesTambienFalla() {
        String larga = "ñ".repeat(37); // 74 bytes en UTF-8
        when(usuarios.count()).thenReturn(0L);

        assertThatThrownBy(() -> servicio(EMAIL, larga).ejecutar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ODONTOLOGO_PASSWORD")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(larga));
    }

    @Test
    void elLogNoContieneNiLaContrasenaNiElEmail(CapturedOutput output) {
        when(usuarios.count()).thenReturn(0L);
        when(encoder.encode(PASSWORD)).thenReturn("$2a$12$hash");

        servicio(EMAIL, PASSWORD).ejecutar();

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(EMAIL);
    }
}
