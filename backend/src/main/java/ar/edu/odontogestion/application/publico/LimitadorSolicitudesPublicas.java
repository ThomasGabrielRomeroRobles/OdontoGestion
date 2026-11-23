package ar.edu.odontogestion.application.publico;

import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.domain.paciente.Dni;
import ar.edu.odontogestion.domain.usuario.Emails;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limitación de solicitudes públicas en memoria (C-09 D4): ventana deslizante de 1 hora por clave
 * ({@code ip:}, {@code dni:} con el DNI normalizado, {@code email:} en minúsculas) con el límite
 * {@code PUBLIC_RATE_LIMIT_PER_HOUR} para las tres. Una solicitud rechazada <b>no</b> se registra, así la espera
 * no crece por insistir. Las claves de un mismo llamado se evalúan juntas: si alguna excede, ninguna se registra.
 * El tope de claves acota la memoria ante un barrido de DNI/emails. Los contadores se pierden al reiniciar
 * (aceptado para la demo local).
 *
 * <p>Todo pasa por un único candado: son operaciones de microsegundos y así el conteo es exacto con cualquier
 * cantidad de hilos. Reloj inyectado. Nunca se loguea ninguna clave (llevan DNI, email o IP).
 */
@Component
public class LimitadorSolicitudesPublicas {

    private static final Duration VENTANA = Duration.ofHours(1);
    static final int MAX_CLAVES = 10_000;

    private final Clock clock;
    private final int limitePorHora;
    private final int maxClaves;
    private final Map<String, ArrayDeque<Instant>> registros = new HashMap<>();

    @Autowired
    public LimitadorSolicitudesPublicas(Clock clock, AppProperties props) {
        this(clock, props.publicRateLimitPerHour(), MAX_CLAVES);
    }

    LimitadorSolicitudesPublicas(Clock clock, int limitePorHora, int maxClaves) {
        this.clock = clock;
        this.limitePorHora = limitePorHora;
        this.maxClaves = maxClaves;
    }

    /** Cuenta una solicitud de la IP; lanza {@link DemasiadasSolicitudesException} si excede el límite. */
    public void registrarIp(String ip) {
        registrar(List.of("ip:" + ip));
    }

    /** Cuenta una solicitud para el DNI y para el email (claves independientes); si alguno excede, ninguno suma. */
    public void registrarIdentidad(String dni, String email) {
        registrar(List.of("dni:" + Dni.normalizar(dni), "email:" + Emails.normalizar(email)));
    }

    int cantidadDeClaves() {
        synchronized (registros) {
            return registros.size();
        }
    }

    private void registrar(List<String> claves) {
        synchronized (registros) {
            Instant ahora = clock.instant();
            Instant corte = ahora.minus(VENTANA);
            double esperaMax = 0;
            for (String clave : claves) {
                ArrayDeque<Instant> usos = registros.get(clave);
                if (usos == null) {
                    continue;
                }
                podar(usos, corte);
                if (usos.isEmpty()) {
                    registros.remove(clave);
                } else if (usos.size() >= limitePorHora) {
                    double espera = Duration.between(ahora, usos.peekFirst().plus(VENTANA)).toMillis() / 1000.0;
                    esperaMax = Math.max(esperaMax, espera);
                }
            }
            if (esperaMax > 0) {
                throw new DemasiadasSolicitudesException(esperaMax);
            }
            for (String clave : claves) {
                registros.computeIfAbsent(clave, k -> new ArrayDeque<>()).addLast(ahora);
            }
            if (registros.size() > maxClaves) {
                acotar(Set.copyOf(claves), corte);
            }
        }
    }

    private static void podar(ArrayDeque<Instant> usos, Instant corte) {
        while (!usos.isEmpty() && !usos.peekFirst().isAfter(corte)) {
            usos.removeFirst();
        }
    }

    /** Descarta las claves inactivas y, si aún se supera el tope, las menos recientes (nunca las recién usadas). */
    private void acotar(Set<String> protegidas, Instant corte) {
        registros.entrySet().removeIf(e -> {
            if (protegidas.contains(e.getKey())) {
                return false;
            }
            podar(e.getValue(), corte);
            return e.getValue().isEmpty();
        });
        int exceso = registros.size() - maxClaves;
        if (exceso <= 0) {
            return;
        }
        registros.entrySet().stream()
                .filter(e -> !protegidas.contains(e.getKey()))
                .sorted(Comparator.comparing((Map.Entry<String, ArrayDeque<Instant>> e) -> e.getValue().peekLast()))
                .limit(exceso)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(registros::remove);
    }
}
