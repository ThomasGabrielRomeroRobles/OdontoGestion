package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canal falso para tests: registra los envíos aceptados y permite programar rechazos, fallos permanentes y una
 * demora (para probar ejecuciones superpuestas). Seguro para hilos.
 */
public class CanalEnMemoria implements NotificationChannel {

    private final List<NotificacionSaliente> enviados = new CopyOnWriteArrayList<>();
    private final Set<String> rechazados = ConcurrentHashMap.newKeySet();
    private volatile boolean fallarSiempre;
    private volatile Duration demora = Duration.ZERO;

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.EMAIL;
    }

    @Override
    public ResultadoEnvio enviar(NotificacionSaliente notificacion) {
        if (!demora.isZero()) {
            try {
                Thread.sleep(demora.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResultadoEnvio.fallido("Envío interrumpido");
            }
        }
        if (fallarSiempre) {
            return ResultadoEnvio.fallido("Canal en memoria configurado para fallar");
        }
        if (rechazados.contains(notificacion.destinatario())) {
            return ResultadoEnvio.fallido("Destinatario rechazado por el canal en memoria");
        }
        enviados.add(notificacion);
        return ResultadoEnvio.enviado();
    }

    public void rechazar(String destinatario) {
        rechazados.add(destinatario);
    }

    public void fallarSiempre() {
        fallarSiempre = true;
    }

    public void demorar(Duration demora) {
        this.demora = demora;
    }

    public List<NotificacionSaliente> enviados() {
        return new ArrayList<>(enviados);
    }

    public void reiniciar() {
        enviados.clear();
        rechazados.clear();
        fallarSiempre = false;
        demora = Duration.ZERO;
    }
}
