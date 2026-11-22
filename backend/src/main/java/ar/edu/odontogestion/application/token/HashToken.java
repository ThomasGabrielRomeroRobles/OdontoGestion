package ar.edu.odontogestion.application.token;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** SHA-256 del valor del token, tras validar su formato; un formato inválido es un token inválido. */
public final class HashToken {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private HashToken() {
    }

    public static byte[] sha256(String valor) {
        if (valor == null || !FORMATO.matcher(valor).matches()) {
            throw new TokenInvalidoException();
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
