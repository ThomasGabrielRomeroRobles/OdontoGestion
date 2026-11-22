package ar.edu.odontogestion.domain.token;

/** A qué recurso apunta un token: exactamente un turno, un aviso o una entrada de lista de espera. */
public sealed interface ReferenciaToken {

    long id();

    record Turno(long id) implements ReferenciaToken {
        public Turno {
            idPositivo(id);
        }
    }

    record Aviso(long id) implements ReferenciaToken {
        public Aviso {
            idPositivo(id);
        }
    }

    record EntradaListaEspera(long id) implements ReferenciaToken {
        public EntradaListaEspera {
            idPositivo(id);
        }
    }

    private static void idPositivo(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El id de referencia debe ser mayor que cero");
        }
    }
}
