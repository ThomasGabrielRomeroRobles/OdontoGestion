package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.agenda.CalculadoraDisponibilidad;
import ar.edu.odontogestion.domain.agenda.FranjaHoraria;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Valida que un turno nuevo o movido respete el horario del consultorio (design D5/D7): inicio alineado a la
 * granularidad, íntegramente dentro de un horario de trabajo, fuera de bloqueos y no en el pasado ni antes de la
 * anticipación de la política. Reutiliza {@link CalculadoraDisponibilidad} para no duplicar las reglas de horario.
 * No decide la superposición con otros turnos: eso es de la base (regla dura #8). Todo error es {@code 422} con
 * campo {@code inicio}.
 */
@Component
public class ValidadorHorarioTurno {

    private static final String CAMPO = "inicio";

    private final HorarioTrabajoRepository horarios;
    private final BloqueoAgendaRepository bloqueos;
    private final Clock clock;

    public ValidadorHorarioTurno(HorarioTrabajoRepository horarios, BloqueoAgendaRepository bloqueos, Clock clock) {
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.clock = clock;
    }

    public void validar(Consultorio consultorio, Instant inicio, int duracionMin, PoliticaAnticipacion politica) {
        Instant ahora = clock.instant();
        if (inicio.isBefore(ahora)) {
            throw error("No puede ser anterior al momento actual.");
        }
        if (inicio.isBefore(politica.minimoInicio(ahora))) {
            throw error("No cumple la anticipación mínima de reserva.");
        }

        ZoneId zona = ZoneId.of(consultorio.getZonaHoraria());
        int granularidad = consultorio.getParametros().granularidadMin();
        ZonedDateTime local = inicio.atZone(zona);
        LocalDate dia = local.toLocalDate();
        Hueco pedido = new Hueco(inicio, inicio.plus(Duration.ofMinutes(duracionMin)));

        List<FranjaHoraria> franjas = horarios
                .findByConsultorio_IdAndDiaSemana(consultorio.getId(), (short) dia.getDayOfWeek().getValue())
                .stream().map(h -> new FranjaHoraria(h.getDiaSemana(), h.getHoraDesde(), h.getHoraHasta())).toList();
        boolean esCandidato = CalculadoraDisponibilidad.calcular(dia, dia, zona, franjas, List.of(), List.of(),
                duracionMin, granularidad, Instant.EPOCH).contains(pedido);
        if (!esCandidato) {
            throw error(estaAlineado(local, granularidad)
                    ? "Debe quedar dentro de un horario de trabajo."
                    : "Debe estar alineado a la granularidad de " + granularidad + " minutos.");
        }

        if (!bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(consultorio.getId(),
                pedido.inicio(), pedido.fin()).isEmpty()) {
            throw error("Cae dentro de un bloqueo de la agenda.");
        }
    }

    private static boolean estaAlineado(ZonedDateTime local, int granularidadMin) {
        return local.getSecond() == 0 && local.getNano() == 0
                && (local.getHour() * 60 + local.getMinute()) % granularidadMin == 0;
    }

    private static ValidacionDominioException error(String mensaje) {
        return new ValidacionDominioException(CAMPO, mensaje);
    }
}
