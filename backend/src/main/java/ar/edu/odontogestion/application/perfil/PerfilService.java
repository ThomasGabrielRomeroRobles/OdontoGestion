package ar.edu.odontogestion.application.perfil;

import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.usuario.Emails;
import ar.edu.odontogestion.domain.usuario.PoliticaPassword;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil del odontólogo y cambio de contraseña. El usuario se resuelve por el email de la sesión. */
@Service
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public PerfilService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PerfilVista obtener(String email) {
        return aVista(cargar(email));
    }

    @Transactional
    public PerfilVista actualizar(String email, DatosPerfil datos) {
        Usuario usuario = cargar(email);
        usuario.actualizarPerfil(datos.nombre(), datos.apellido(), datos.matricula());
        return aVista(usuario);
    }

    /**
     * Política única ({@link PoliticaPassword}) y distinta de la actual. Si hay algún error el hash no cambia.
     */
    @Transactional
    public void cambiarPassword(String email, String passwordActual, String passwordNueva) {
        Usuario usuario = cargar(email);
        List<ErrorCampo> errores = new ArrayList<>();
        if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            errores.add(new ErrorCampo("passwordActual", "La contraseña actual es incorrecta."));
        }
        PoliticaPassword.violacion(passwordNueva)
                .ifPresentOrElse(motivo -> errores.add(new ErrorCampo("passwordNueva", motivo)), () -> {
                    if (passwordNueva.equals(passwordActual)) {
                        errores.add(new ErrorCampo("passwordNueva", "Debe ser distinta de la contraseña actual."));
                    }
                });
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
        usuario.cambiarPasswordHash(passwordEncoder.encode(passwordNueva));
    }

    private Usuario cargar(String email) {
        return usuarios.findByEmail(Emails.normalizar(email))
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private static PerfilVista aVista(Usuario u) {
        return new PerfilVista(u.getEmail(), u.getNombre(), u.getApellido(), u.getMatricula());
    }
}
