package ar.edu.odontogestion.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.error.CredencialesInvalidasException;
import ar.edu.odontogestion.domain.error.DemasiadosIntentosException;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    private static final String EMAIL_ENTRADA = "  Odontologo@Consultorio.EXAMPLE ";
    private static final String EMAIL = "odontologo@consultorio.example";
    private static final String PASSWORD = "Ficticia-Secreta-77";
    private static final String IP = "10.0.0.1";
    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private LimitadorIntentosLogin limitador;

    @Mock
    private UsuarioRepository usuarios;

    private AutenticacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AutenticacionService(authenticationManager, limitador, usuarios,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    static List<AuthenticationException> fallosDeAutenticacion() {
        return List.of(new BadCredentialsException("Bad credentials"), new DisabledException("User is disabled"),
                new UsernameNotFoundException("no existe"));
    }

    @Test
    void unExitoRegistraElExitoYActualizaElUltimoAccesoConElReloj() {
        Usuario usuario = Usuario.nuevo(Consultorio.nuevo("C", "UTC"), EMAIL, "h", "Ana", "Perez", "");
        var autenticado = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(autenticado);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        UsuarioAutenticado resultado = servicio.autenticar(EMAIL_ENTRADA, PASSWORD, IP);

        assertThat(resultado.email()).isEqualTo(EMAIL);
        assertThat(resultado.nombre()).isEqualTo("Ana");
        assertThat(resultado.apellido()).isEqualTo("Perez");
        assertThat(resultado.authentication()).isSameAs(autenticado);
        assertThat(usuario.getUltimoAcceso()).isEqualTo(AHORA);
        verify(limitador).verificarNoBloqueado(EMAIL, IP);
        verify(limitador).registrarExito(EMAIL, IP);
    }

    @ParameterizedTest
    @MethodSource("fallosDeAutenticacion")
    void todosLosFallosSeTraducenALaMismaExcepcionYSeRegistraElFallo(AuthenticationException causa) {
        when(authenticationManager.authenticate(any())).thenThrow(causa);

        assertThatThrownBy(() -> servicio.autenticar(EMAIL_ENTRADA, PASSWORD, IP))
                .isExactlyInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(CredencialesInvalidasException.MENSAJE);
        verify(limitador).registrarFallo(EMAIL, IP);
    }

    @Test
    void elEmailSeNormalizaAntesDeLimitarYDeAutenticar() {
        var autenticado = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(autenticado);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(
                Usuario.nuevo(Consultorio.nuevo("C", "UTC"), EMAIL, "h", "Ana", "Perez", "")));

        servicio.autenticar(EMAIL_ENTRADA, PASSWORD, IP);

        verify(limitador).verificarNoBloqueado(EMAIL, IP);
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.security.core.Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(EMAIL);
    }

    @Test
    void siEstaBloqueadoNoInvocaAlAuthenticationManager() {
        doThrow(new DemasiadosIntentosException(60)).when(limitador).verificarNoBloqueado(EMAIL, IP);

        assertThatThrownBy(() -> servicio.autenticar(EMAIL_ENTRADA, PASSWORD, IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void ningunMensajeDeExcepcionContieneLaContrasena() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("fallo con " + "x"));

        assertThatThrownBy(() -> servicio.autenticar(EMAIL_ENTRADA, PASSWORD, IP))
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(PASSWORD);
                    assertThat(e.getCause()).isNull();
                });
        assertThat(new DemasiadosIntentosException(5).getMessage()).doesNotContain(PASSWORD);
    }
}
