package ar.edu.odontogestion.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    private static Usuario usuario() {
        return Usuario.nuevo(Consultorio.nuevo("C", "UTC"), "a@ejemplo.test", "hash-viejo", "Ana", "Perez", "MN 1");
    }

    @Test
    void registrarAccesoGuardaElInstante() {
        Usuario u = usuario();
        Instant t1 = Instant.parse("2026-11-20T12:00:00Z");
        Instant t2 = Instant.parse("2026-11-21T09:30:00Z");

        u.registrarAcceso(t1);
        assertThat(u.getUltimoAcceso()).isEqualTo(t1);
        u.registrarAcceso(t2);
        assertThat(u.getUltimoAcceso()).isEqualTo(t2);
    }

    @Test
    void registrarAccesoRechazaNulo() {
        assertThatThrownBy(() -> usuario().registrarAcceso(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarPasswordHashReemplazaElHash() {
        Usuario u = usuario();

        u.cambiarPasswordHash("hash-nuevo");

        assertThat(u.getPasswordHash()).isEqualTo("hash-nuevo");
    }

    @Test
    void cambiarPasswordHashRechazaVacioOEnBlancoOnulo() {
        Usuario u = usuario();

        assertThatThrownBy(() -> u.cambiarPasswordHash("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> u.cambiarPasswordHash("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> u.cambiarPasswordHash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(u.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void actualizarPerfilCambiaNombreApellidoYMatriculaPeroNoElEmail() {
        Usuario u = usuario();

        u.actualizarPerfil("Beatriz", "Gomez", "MP 9");

        assertThat(u.getNombre()).isEqualTo("Beatriz");
        assertThat(u.getApellido()).isEqualTo("Gomez");
        assertThat(u.getMatricula()).isEqualTo("MP 9");
        assertThat(u.getEmail()).isEqualTo("a@ejemplo.test");
    }
}
