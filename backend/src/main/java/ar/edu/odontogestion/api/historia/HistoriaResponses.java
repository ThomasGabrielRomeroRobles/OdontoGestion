package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.application.historia.AnamnesisVista;
import ar.edu.odontogestion.application.historia.AutorVista;
import ar.edu.odontogestion.application.historia.EvolucionVista;
import ar.edu.odontogestion.application.historia.HistoriaVista;
import ar.edu.odontogestion.application.historia.PiezaVista;
import ar.edu.odontogestion.domain.historia.EstadoPieza;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTOs de respuesta de la historia clínica (contrato C-05 design D7). Sin entidades ni email del autor. */
public final class HistoriaResponses {

    private HistoriaResponses() {
    }

    public record Autor(Long id, String nombre) {

        static Autor from(AutorVista v) {
            return v == null ? null : new Autor(v.id(), v.nombre());
        }
    }

    public record Paciente(Long id, String nombre, String apellido) {
    }

    public record Anamnesis(String alergias, String medicacionActual, String enfermedadesPrevias,
            String antecedentesRelevantes, Boolean embarazo, String observaciones, Autor actualizadoPor,
            Instant actualizadoEn) {

        static Anamnesis from(AnamnesisVista v) {
            return new Anamnesis(v.alergias(), v.medicacionActual(), v.enfermedadesPrevias(),
                    v.antecedentesRelevantes(), v.embarazo(), v.observaciones(), Autor.from(v.actualizadoPor()),
                    v.actualizadoEn());
        }
    }

    public record Pieza(int fdi, EstadoPieza estado, String observacion, Autor actualizadoPor,
            Instant actualizadoEn) {

        static Pieza from(PiezaVista v) {
            return new Pieza(v.fdi(), v.estado(), v.observacion(), Autor.from(v.actualizadoPor()), v.actualizadoEn());
        }
    }

    public record Evolucion(Long id, LocalDate fecha, String nota, Long turnoId, Autor autor, Instant creadaEn,
            Instant editadaEn, boolean editable) {

        static Evolucion from(EvolucionVista v) {
            return new Evolucion(v.id(), v.fecha(), v.nota(), v.turnoId(), Autor.from(v.autor()), v.creadaEn(),
                    v.editadaEn(), v.editable());
        }
    }

    public record Historia(Paciente paciente, Anamnesis anamnesis, List<Pieza> piezas, List<Evolucion> evoluciones) {

        static Historia from(HistoriaVista v) {
            return new Historia(new Paciente(v.paciente().id(), v.paciente().nombre(), v.paciente().apellido()),
                    Anamnesis.from(v.anamnesis()), v.piezas().stream().map(Pieza::from).toList(),
                    v.evoluciones().stream().map(Evolucion::from).toList());
        }
    }
}
