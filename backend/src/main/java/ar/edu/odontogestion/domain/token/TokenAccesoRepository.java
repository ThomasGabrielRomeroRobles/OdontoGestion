package ar.edu.odontogestion.domain.token;

import java.time.Instant;
import java.util.Optional;

/** Puerto de persistencia de tokens: trabaja siempre con el hash, nunca con el valor en claro. */
public interface TokenAccesoRepository {

    void insertar(long consultorioId, byte[] hash, PropositoToken proposito, ReferenciaToken referencia,
            Instant venceEn, Instant ahora);

    /** Solo lectura: token con ese hash y propósito, sin usar y con {@code vence_en > ahora}. */
    Optional<TokenVigente> buscarVigente(byte[] hash, PropositoToken proposito, Instant ahora);

    /**
     * Solo lectura: token sin usar y con {@code vence_en > ahora} con ese hash, de cualquier propósito (C-09 D6).
     * El llamador decide si el propósito le sirve.
     */
    Optional<TokenIdentificado> buscarVigentePorHash(byte[] hash, Instant ahora);

    /** Marca {@code usado_en} con una única sentencia condicional; vacío si no hay token consumible. */
    Optional<ReferenciaToken> consumir(byte[] hash, PropositoToken proposito, Instant ahora);

    /** Deja vencidos los tokens vigentes de la referencia (opcionalmente de un propósito); devuelve cuántos. */
    int revocar(ReferenciaToken referencia, Optional<PropositoToken> proposito, Instant ahora);

    /** Deja vencido el token vigente con ese hash (uno solo); devuelve 1, o 0 si no existe, ya se usó o venció. */
    int revocarPorHash(byte[] hash, Instant ahora);
}
