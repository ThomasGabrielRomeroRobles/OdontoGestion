package ar.edu.odontogestion.application.auth;

import ar.edu.odontogestion.domain.error.CredencialesInvalidasException;
import ar.edu.odontogestion.domain.usuario.Emails;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import java.time.Clock;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login del odontólogo: limitación de intentos, autenticación y registro del último acceso. */
@Service
public class AutenticacionService {

    private final AuthenticationManager authenticationManager;
    private final LimitadorIntentosLogin limitador;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public AutenticacionService(AuthenticationManager authenticationManager, LimitadorIntentosLogin limitador,
            UsuarioRepository usuarios, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.limitador = limitador;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional
    public UsuarioAutenticado autenticar(String email, String password, String ip) {
        String emailNormalizado = Emails.normalizar(email);
        // Se consulta ANTES de verificar la contraseña: así un bloqueo no revela si era correcta.
        limitador.verificarNoBloqueado(emailNormalizado, ip);
        Authentication autenticacion;
        try {
            autenticacion = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(emailNormalizado, password));
        } catch (BadCredentialsException | AccountStatusException | UsernameNotFoundException e) {
            limitador.registrarFallo(emailNormalizado, ip);
            throw new CredencialesInvalidasException();
        }
        limitador.registrarExito(emailNormalizado, ip);
        Usuario usuario = usuarios.findByEmail(autenticacion.getName())
                .orElseThrow(CredencialesInvalidasException::new);
        usuario.registrarAcceso(clock.instant());
        return new UsuarioAutenticado(usuario.getEmail(), usuario.getNombre(), usuario.getApellido(), autenticacion);
    }
}
