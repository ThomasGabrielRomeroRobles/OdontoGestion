package ar.edu.odontogestion.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.consultorio.BootstrapService;
import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import ar.edu.odontogestion.infrastructure.config.BootstrapProperties;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class BootstrapSeedIT extends AbstractIntegrationTest {

    private static final String PASSWORD_DE_TEST = "Ficticia-Test-123";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BootstrapSeeder seeder;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ConsultorioRepository consultorios;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void trasElArranqueHayUnConsultorioDemoYUnOdontologoConHashBcrypt() {
        var consultorio = jdbc.queryForMap(
                "SELECT nombre, granularidad_min, recordatorio_horas_antes FROM consultorio");
        assertThat(consultorio).containsEntry("nombre", "Consultorio Demo")
                .containsEntry("granularidad_min", 15)
                .containsEntry("recordatorio_horas_antes", 24);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario", String.class);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD_DE_TEST);
        assertThat(jdbc.queryForObject("SELECT email::text FROM usuario", String.class))
                .isEqualTo("odontologo@consultorio.example");
    }

    @Test
    void reEjecutarElSeedNoDuplicaNiCambiaElHashNiUnParametroEditado() throws Exception {
        String hashAntes = jdbc.queryForObject("SELECT password_hash FROM usuario", String.class);
        jdbc.update("UPDATE consultorio SET granularidad_min = 30");
        try {
            seeder.run(null);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario", String.class)).isEqualTo(hashAntes);
            assertThat(jdbc.queryForObject("SELECT granularidad_min FROM consultorio", Integer.class))
                    .isEqualTo(30);
        } finally {
            jdbc.update("UPDATE consultorio SET granularidad_min = 15");
        }
    }

    @Test
    void elLogDelSeedNoContieneLaContrasena(CapturedOutput output) {
        tx.executeWithoutResult(status -> {
            usuarios.findByEmail("odontologo@consultorio.example").ifPresent(usuarios::delete);
            usuarios.flush();
            try {
                seeder.run(null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            status.setRollbackOnly();
        });

        assertThat(output.getAll()).contains("Seed: odontólogo creado")
                .doesNotContain(PASSWORD_DE_TEST)
                .doesNotContain("odontologo@consultorio.example");
    }

    @Test
    void conUsuarioExistenteElSeedNoCreaNiTocaNadaAunqueElEmailDeBootstrapSeaOtroYFalteLaContrasena() {
        String hashAntes = jdbc.queryForObject("SELECT password_hash FROM usuario", String.class);
        BootstrapService conOtroEmailSinPassword = new BootstrapService(usuarios, consultorios, consultorioActual,
                passwordEncoder, new BootstrapProperties("otro@consultorio.example", ""));

        conOtroEmailSinPassword.ejecutar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario", String.class)).isEqualTo(hashAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio", Integer.class)).isEqualTo(1);
    }

    @Test
    void elConsultorioActualEsElDeLaMigracion() {
        Long idMigracion = jdbc.queryForObject("SELECT id FROM consultorio WHERE nombre = 'Consultorio Demo'",
                Long.class);
        assertThat(consultorioActual.id()).isEqualTo(idMigracion);
    }
}
