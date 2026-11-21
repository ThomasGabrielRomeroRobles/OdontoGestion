package ar.edu.odontogestion.application.consultorio;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.usuario.Emails;
import ar.edu.odontogestion.domain.usuario.PoliticaPassword;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import ar.edu.odontogestion.infrastructure.config.BootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra al odontólogo inicial (US-003). El consultorio demo lo inserta la migración V2. Idempotente: si ya
 * existe algún usuario no hace nada (nunca crea un segundo usuario ni pisa la contraseña elegida).
 */
@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final UsuarioRepository usuarios;
    private final ConsultorioRepository consultorios;
    private final ConsultorioActual consultorioActual;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrap;

    public BootstrapService(UsuarioRepository usuarios, ConsultorioRepository consultorios,
            ConsultorioActual consultorioActual, PasswordEncoder passwordEncoder, BootstrapProperties bootstrap) {
        this.usuarios = usuarios;
        this.consultorios = consultorios;
        this.consultorioActual = consultorioActual;
        this.passwordEncoder = passwordEncoder;
        this.bootstrap = bootstrap;
    }

    @Transactional
    public void ejecutar() {
        if (usuarios.count() > 0) {
            return;
        }
        String password = bootstrap.odontologoPassword();
        if (PoliticaPassword.violacion(password).isPresent()) {
            throw new IllegalStateException("Falta o es inválida la variable BOOTSTRAP_ODONTOLOGO_PASSWORD "
                    + "(entre 8 caracteres y 72 bytes UTF-8).");
        }
        Consultorio consultorio = consultorios.findById(consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Consultorio no encontrado"));
        usuarios.save(Usuario.nuevo(consultorio, Emails.normalizar(bootstrap.odontologoEmail()),
                passwordEncoder.encode(password), "Odontólogo Demo", "Ficticio", "MN 0000"));
        log.info("Seed: odontólogo creado");
    }
}
