package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Contra PostgreSQL real: una violación de FK no se confunde con un conflicto de agenda (409). */
class TraductorViolacionAgendaIT extends AbstractTurnoIT {

    @Autowired
    private TraductorViolacionAgenda traductor;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private ConsultorioRepository consultorios;

    @Test
    void unaViolacionDeFkContraLaBaseSeRelanzaComoDataIntegrityViolation() {
        Long prestacionId = crearPrestacion(30);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            Prestacion prestacion = prestaciones.findById(prestacionId).orElseThrow();
            Turno huerfano = Turno.reservado(consultorios.findById(consultorioId()).orElseThrow(),
                    pacientes.getReferenceById(987_654_321L), prestacion, lunes("10:00"), OrigenTurno.ODONTOLOGO);
            traductor.ejecutar(() -> turnos.saveAndFlush(huerfano));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }
}
