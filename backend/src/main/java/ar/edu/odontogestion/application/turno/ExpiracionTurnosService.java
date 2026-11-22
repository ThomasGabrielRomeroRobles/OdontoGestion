package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expira en masa las solicitudes pendientes vencidas (RN-PU-02, design D8). Idempotente. */
@Service
public class ExpiracionTurnosService {

    private final ConsultorioActual consultorioActual;
    private final TurnoRepository turnos;
    private final Clock clock;

    public ExpiracionTurnosService(ConsultorioActual consultorioActual, TurnoRepository turnos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.turnos = turnos;
        this.clock = clock;
    }

    /** @return cantidad de turnos que pasaron a {@code EXPIRADO} */
    @Transactional
    public int expirarVencidas() {
        return turnos.expirarPendientesVencidas(consultorioActual.id(), clock.instant());
    }
}
