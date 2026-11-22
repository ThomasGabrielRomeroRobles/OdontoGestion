package ar.edu.odontogestion.application.token;

import java.security.SecureRandom;
import java.util.Base64;

/** Genera valores de token: 32 bytes (256 bits) de un SecureRandom, base64url sin relleno (43 caracteres). */
public class GeneradorTokens {

    private static final int BYTES = 32;

    private final SecureRandom random;

    public GeneradorTokens(SecureRandom random) {
        this.random = random;
    }

    public String generar() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
