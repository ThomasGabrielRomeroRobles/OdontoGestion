package ar.edu.odontogestion.infrastructure.token;

import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenAccesoRepository;
import ar.edu.odontogestion.domain.token.TokenVigente;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación con {@link JdbcClient} (design D3 de C-03): comparte la conexión de la transacción de
 * Spring en curso, por lo que el consumo participa de la transacción del llamador. Los instantes vienen
 * siempre del reloj de la aplicación, nunca de {@code now()} de la base.
 */
@Repository
public class JdbcTokenAccesoRepository implements TokenAccesoRepository {

    private final JdbcClient jdbc;

    public JdbcTokenAccesoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(long consultorioId, byte[] hash, PropositoToken proposito, ReferenciaToken referencia,
            Instant venceEn, Instant ahora) {
        jdbc.sql("""
                INSERT INTO token_acceso (consultorio_id, token_hash, proposito, turno_id, aviso_id,
                                          entrada_lista_espera_id, vence_en, creado_en, actualizado_en)
                VALUES (:consultorio, :hash, :proposito, :turno, :aviso, :entrada, :venceEn, :ahora, :ahora)
                """)
                .param("consultorio", consultorioId)
                .param("hash", hash)
                .param("proposito", proposito.name())
                .param("turno", referencia instanceof ReferenciaToken.Turno t ? t.id() : null, java.sql.Types.BIGINT)
                .param("aviso", referencia instanceof ReferenciaToken.Aviso a ? a.id() : null, java.sql.Types.BIGINT)
                .param("entrada", referencia instanceof ReferenciaToken.EntradaListaEspera e ? e.id() : null,
                        java.sql.Types.BIGINT)
                .param("venceEn", utc(venceEn))
                .param("ahora", utc(ahora))
                .update();
    }

    @Override
    public Optional<TokenVigente> buscarVigente(byte[] hash, PropositoToken proposito, Instant ahora) {
        return jdbc.sql("""
                SELECT turno_id, aviso_id, entrada_lista_espera_id, vence_en
                  FROM token_acceso
                 WHERE token_hash = :hash AND proposito = :proposito
                   AND usado_en IS NULL AND vence_en > :ahora
                """)
                .param("hash", hash)
                .param("proposito", proposito.name())
                .param("ahora", utc(ahora))
                .query((rs, n) -> new TokenVigente(referencia(rs),
                        rs.getObject("vence_en", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public Optional<ReferenciaToken> consumir(byte[] hash, PropositoToken proposito, Instant ahora) {
        return jdbc.sql("""
                UPDATE token_acceso
                   SET usado_en = :ahora, actualizado_en = :ahora
                 WHERE token_hash = :hash AND proposito = :proposito
                   AND usado_en IS NULL AND vence_en > :ahora
                RETURNING turno_id, aviso_id, entrada_lista_espera_id
                """)
                .param("hash", hash)
                .param("proposito", proposito.name())
                .param("ahora", utc(ahora))
                .query((rs, n) -> referencia(rs))
                .optional();
    }

    @Override
    public int revocar(ReferenciaToken referencia, Optional<PropositoToken> proposito, Instant ahora) {
        String columna = switch (referencia) {
            case ReferenciaToken.Turno t -> "turno_id";
            case ReferenciaToken.Aviso a -> "aviso_id";
            case ReferenciaToken.EntradaListaEspera e -> "entrada_lista_espera_id";
        };
        // La columna sale de un switch cerrado (sealed), nunca de entrada externa.
        return jdbc.sql("""
                UPDATE token_acceso
                   SET vence_en = :ahora, actualizado_en = :ahora
                 WHERE %s = :id AND usado_en IS NULL AND vence_en > :ahora
                   AND (CAST(:proposito AS text) IS NULL OR proposito = :proposito)
                """.formatted(columna))
                .param("id", referencia.id())
                .param("ahora", utc(ahora))
                .param("proposito", proposito.map(PropositoToken::name).orElse(null), java.sql.Types.VARCHAR)
                .update();
    }

    private static ReferenciaToken referencia(ResultSet rs) throws SQLException {
        long turno = rs.getLong("turno_id");
        if (!rs.wasNull()) {
            return new ReferenciaToken.Turno(turno);
        }
        long aviso = rs.getLong("aviso_id");
        if (!rs.wasNull()) {
            return new ReferenciaToken.Aviso(aviso);
        }
        return new ReferenciaToken.EntradaListaEspera(rs.getLong("entrada_lista_espera_id"));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
