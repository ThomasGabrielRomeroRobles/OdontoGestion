package ar.edu.odontogestion.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void generaHashesBcryptConCosto12() {
        assertThat(encoder.encode("Ficticia-Test-123")).startsWith("$2a$12$");
        assertThat(encoder.encode("Otra-Ficticia-456")).startsWith("$2a$12$");
    }

    @Test
    void matchesEsVerdaderoSoloParaLaContrasenaOriginal() {
        String hash = encoder.encode("Ficticia-Test-123");

        assertThat(encoder.matches("Ficticia-Test-123", hash)).isTrue();
        assertThat(encoder.matches("Ficticia-Test-124", hash)).isFalse();
    }

    @Test
    void dosHashesDeLaMismaContrasenaDifieren() {
        assertThat(encoder.encode("Ficticia-Test-123")).isNotEqualTo(encoder.encode("Ficticia-Test-123"));
    }
}
