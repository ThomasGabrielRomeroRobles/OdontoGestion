package ar.edu.odontogestion.application.paciente;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del historial de ausencias de un paciente del consultorio actual (US-061, C-08 design D6). */
@Service
public class AusenciaPacienteService {

    private final ConsultorioActual consultorioActual;
    private final PacienteRepository pacientes;
    private final TurnoRepository turnos;

    public AusenciaPacienteService(ConsultorioActual consultorioActual, PacienteRepository pacientes,
            TurnoRepository turnos) {
        this.consultorioActual = consultorioActual;
        this.pacientes = pacientes;
        this.turnos = turnos;
    }

    /**
     * @throws RecursoNoEncontradoException el paciente no existe o es de otro consultorio (404, mismo mensaje que la
     *                                      ficha)
     */
    @Transactional(readOnly = true)
    public HistorialAusencias historial(Long pacienteId) {
        Long consultorioId = consultorioActual.id();
        Paciente paciente = pacientes.findByIdAndConsultorioId(pacienteId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(PacienteService.MENSAJE_NO_ENCONTRADO));
        return new HistorialAusencias(paciente.getCantidadAusencias(),
                turnos.ausenciasDelPaciente(consultorioId, pacienteId).stream()
                        .map(t -> new HistorialAusencias.Ausencia(t.getId(), t.getInicio(),
                                t.getPrestacion().getNombre()))
                        .toList());
    }
}
