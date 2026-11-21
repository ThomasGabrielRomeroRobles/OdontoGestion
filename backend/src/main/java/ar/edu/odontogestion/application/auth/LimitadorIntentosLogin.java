package ar.edu.odontogestion.application.auth;

import ar.edu.odontogestion.domain.error.DemasiadosIntentosException;
import ar.edu.odontogestion.infrastructure.config.LoginProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limitación de intentos de login en memoria (design D4), por email normalizado y por IP, con espera
 * creciente. Todas las actualizaciones por clave son atómicas ({@code compute}); el mapa tiene un tope de
 * claves para acotar la memoria ante un barrido de emails.
 */
@Component
public class LimitadorIntentosLogin {

    private static final Duration INACTIVIDAD = Duration.ofMinutes(15);

    private record Estado(int fallos, Instant bloqueadoHasta, Duration ultimaEspera, Instant ultimoFallo) {

        boolean bloqueado(Instant ahora) {
            return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
        }

        /** Inactiva: no bloqueada y sin fallos durante 15 minutos desde el último fallo o el fin del bloqueo. */
        boolean inactivo(Instant ahora) {
            Instant referencia = bloqueadoHasta != null && bloqueadoHasta.isAfter(ultimoFallo)
                    ? bloqueadoHasta : ultimoFallo;
            return !referencia.plus(INACTIVIDAD).isAfter(ahora);
        }
    }

    private final Clock clock;
    private final LoginProperties props;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    public LimitadorIntentosLogin(Clock clock, LoginProperties props) {
        this.clock = clock;
        this.props = props;
    }

    /** Lanza {@link DemasiadosIntentosException} si el email o la IP están bloqueados. */
    public void verificarNoBloqueado(String email, String ip) {
        Instant ahora = clock.instant();
        double maxRestante = 0;
        for (String clave : claves(email, ip)) {
            estados.computeIfPresent(clave, (k, e) -> e.inactivo(ahora) ? null : e);
            Estado estado = estados.get(clave);
            if (estado != null && estado.bloqueado(ahora)) {
                double restante = Duration.between(ahora, estado.bloqueadoHasta()).toMillis() / 1000.0;
                maxRestante = Math.max(maxRestante, restante);
            }
        }
        if (maxRestante > 0) {
            throw new DemasiadosIntentosException(maxRestante);
        }
    }

    public void registrarFallo(String email, String ip) {
        Instant ahora = clock.instant();
        List<String> claves = claves(email, ip);
        for (String clave : claves) {
            estados.compute(clave, (k, actual) -> siguiente(actual, ahora));
        }
        if (estados.size() > props.maxClaves()) {
            acotar(Set.copyOf(claves), ahora);
        }
    }

    public void registrarExito(String email, String ip) {
        for (String clave : claves(email, ip)) {
            estados.remove(clave);
        }
    }

    int fallosConsecutivos(String email) {
        Estado estado = estados.get(claveEmail(email));
        return estado == null ? 0 : estado.fallos();
    }

    int tamano() {
        return estados.size();
    }

    private Estado siguiente(Estado actual, Instant ahora) {
        Estado base = (actual == null || actual.inactivo(ahora)) ? null : actual;
        int fallos = (base == null ? 0 : base.fallos()) + 1;
        Duration ultimaEspera = base == null ? null : base.ultimaEspera();
        Instant bloqueadoHasta = base == null ? null : base.bloqueadoHasta();
        if (fallos >= props.maxIntentos()) {
            Duration espera = ultimaEspera == null ? props.esperaInicial()
                    : minimo(ultimaEspera.multipliedBy(2), props.esperaMaxima());
            ultimaEspera = espera;
            bloqueadoHasta = ahora.plus(espera);
        }
        return new Estado(fallos, bloqueadoHasta, ultimaEspera, ahora);
    }

    /** Descarta las claves inactivas y, si aún se supera el tope, las menos recientes (nunca las recién usadas). */
    private synchronized void acotar(Set<String> protegidas, Instant ahora) {
        estados.entrySet().removeIf(e -> !protegidas.contains(e.getKey()) && e.getValue().inactivo(ahora));
        int exceso = estados.size() - props.maxClaves();
        if (exceso <= 0) {
            return;
        }
        estados.entrySet().stream()
                .filter(e -> !protegidas.contains(e.getKey()))
                .sorted(Comparator.comparing((Map.Entry<String, Estado> e) -> e.getValue().ultimoFallo()))
                .limit(exceso)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(estados::remove);
    }

    private static Duration minimo(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static List<String> claves(String email, String ip) {
        return List.of(claveEmail(email), "ip:" + ip);
    }

    private static String claveEmail(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}
