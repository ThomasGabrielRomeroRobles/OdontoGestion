package ar.edu.odontogestion.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Reloj de test avanzable (UTC), seguro entre hilos. No se usa como bean: se inyecta a mano en unitarios. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> actual;

    public MutableClock(Instant inicio) {
        this.actual = new AtomicReference<>(inicio);
    }

    public void set(Instant instante) {
        actual.set(instante);
    }

    public void advance(Duration duracion) {
        actual.updateAndGet(i -> i.plus(duracion));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return actual.get();
    }
}
