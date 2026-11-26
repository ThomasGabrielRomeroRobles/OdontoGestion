package ar.edu.odontogestion.application.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.historia.DatosAnamnesis;
import ar.edu.odontogestion.domain.historia.EstadoPieza;
import ar.edu.odontogestion.domain.historia.EvolucionFueraDeVentanaException;
import ar.edu.odontogestion.domain.historia.FechaEvolucionFuturaException;
import ar.edu.odontogestion.domain.historia.NoEsAutorException;
import ar.edu.odontogestion.domain.historia.PiezaFdiInvalidaException;
import ar.edu.odontogestion.domain.historia.TurnoDeOtroPacienteException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractHistoriaIT;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Casos de uso de la historia clínica (C-05) contra PostgreSQL. Reloj fijo en 2026-11-20T12:00Z (09:00 en el
 * consultorio, hoy = 2026-11-20). Autor: el odontólogo del seed. Datos clínicos ficticios.
 */
class HistoriaClinicaServiceIT extends AbstractHistoriaIT {

    private static final Instant AHORA = FixedClockTestConfig.AHORA;
    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    @Autowired
    private HistoriaClinicaService servicio;

    private static DatosAnamnesis anamnesis(String alergias, String medicacion) {
        return new DatosAnamnesis(alergias, medicacion, null, null, null, null);
    }

    private int historiasDe(Long paciente) {
        return jdbc.queryForObject("SELECT count(*) FROM historia_clinica WHERE paciente_id = ?", Integer.class,
                paciente);
    }

    // --- 3.1 consulta ---

    @Test
    void consultarPacienteSinHistoriaDevuelveFichaVaciaSinCrearla() {
        Long paciente = crearPaciente();

        HistoriaVista vista = servicio.consultar(paciente, EMAIL_SEED);

        assertThat(vista.paciente().id()).isEqualTo(paciente);
        assertThat(vista.anamnesis()).isEqualTo(AnamnesisVista.VACIA);
        assertThat(vista.piezas()).hasSize(32).allSatisfy(p -> assertThat(p.estado()).isNull());
        assertThat(vista.piezas().get(0).fdi()).isEqualTo(11);
        assertThat(vista.evoluciones()).isEmpty();
        assertThat(historiasDe(paciente)).isZero();
    }

    @Test
    void pacienteInexistenteEs404() {
        assertThatThrownBy(() -> servicio.consultar(999_999_999L, EMAIL_SEED))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void pacienteDeOtroConsultorioEsInexistente() {
        Long ajeno = crearPaciente(crearOtroConsultorio(), true);
        assertThatThrownBy(() -> servicio.consultar(ajeno, EMAIL_SEED))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // --- 3.2 anamnesis y odontograma ---

    @Test
    void guardarAnamnesisCreaLaHistoriaYRegistraAutorYFecha() {
        Long paciente = crearPaciente();

        AnamnesisVista a = servicio.guardarAnamnesis(paciente, anamnesis(" Penicilina ", null), EMAIL_SEED);

        assertThat(a.alergias()).isEqualTo("Penicilina");
        assertThat(a.medicacionActual()).isEmpty();
        assertThat(a.embarazo()).isNull();
        assertThat(a.actualizadoPor().id()).isEqualTo(usuarioSeedId());
        assertThat(a.actualizadoPor().nombre()).isNotBlank().doesNotContain("@");
        assertThat(a.actualizadoEn()).isEqualTo(AHORA);
        assertThat(historiasDe(paciente)).isEqualTo(1);
    }

    @Test
    void reemplazarAnamnesisVaciaLosCamposOmitidos() {
        Long paciente = crearPaciente();
        servicio.guardarAnamnesis(paciente, anamnesis("Penicilina", "Ibuprofeno"), EMAIL_SEED);
        reloj.advance(Duration.ofMinutes(5));

        AnamnesisVista a = servicio.guardarAnamnesis(paciente, anamnesis("Ninguna", null), EMAIL_SEED);

        assertThat(a.alergias()).isEqualTo("Ninguna");
        assertThat(a.medicacionActual()).isEmpty();
        assertThat(a.actualizadoEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM anamnesis a JOIN historia_clinica h ON h.id = "
                + "a.historia_id WHERE h.paciente_id = ?", Integer.class, paciente)).isEqualTo(1);
    }

    @Test
    void escriturasSucesivasUsanLaMismaHistoria() {
        Long paciente = crearPaciente();
        servicio.guardarAnamnesis(paciente, anamnesis("Penicilina", null), EMAIL_SEED);
        servicio.registrarPieza(paciente, 16, EstadoPieza.CARIES, null, EMAIL_SEED);
        servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota ficticia", null, null), EMAIL_SEED);

        assertThat(historiasDe(paciente)).isEqualTo(1);
        HistoriaVista vista = servicio.consultar(paciente, EMAIL_SEED);
        assertThat(vista.anamnesis().alergias()).isEqualTo("Penicilina");
        assertThat(vista.piezas()).filteredOn(p -> p.fdi() == 16).singleElement()
                .satisfies(p -> assertThat(p.estado()).isEqualTo(EstadoPieza.CARIES));
        assertThat(vista.evoluciones()).hasSize(1);
    }

    @Test
    void registrarPiezaGuardaEstadoObservacionAutorYFecha() {
        Long paciente = crearPaciente();

        PiezaVista p = servicio.registrarPieza(paciente, 16, EstadoPieza.CARIES, " Oclusal ", EMAIL_SEED);

        assertThat(p.fdi()).isEqualTo(16);
        assertThat(p.estado()).isEqualTo(EstadoPieza.CARIES);
        assertThat(p.observacion()).isEqualTo("Oclusal");
        assertThat(p.actualizadoPor().id()).isEqualTo(usuarioSeedId());
        assertThat(p.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void actualizarPiezaRegistradaNoDuplica() {
        Long paciente = crearPaciente();
        servicio.registrarPieza(paciente, 16, EstadoPieza.CARIES, null, EMAIL_SEED);

        PiezaVista p = servicio.registrarPieza(paciente, 16, EstadoPieza.OBTURADA, "Resina", EMAIL_SEED);

        assertThat(p.estado()).isEqualTo(EstadoPieza.OBTURADA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pieza_dental_estado e JOIN historia_clinica h "
                + "ON h.id = e.historia_id WHERE h.paciente_id = ? AND e.pieza_fdi = 16", Integer.class, paciente))
                .isEqualTo(1);
    }

    @Test
    void bordesDelRangoFdi() {
        Long paciente = crearPaciente();
        for (int valida : new int[] {11, 18, 41, 48}) {
            assertThat(servicio.registrarPieza(paciente, valida, EstadoPieza.SANA, null, EMAIL_SEED).fdi())
                    .isEqualTo(valida);
        }
        for (int invalida : new int[] {10, 49, 20}) {
            assertThatThrownBy(() -> servicio.registrarPieza(paciente, invalida, EstadoPieza.SANA, null, EMAIL_SEED))
                    .isInstanceOf(PiezaFdiInvalidaException.class);
        }
    }

    @Test
    void piezaInvalidaNoCreaHistoria() {
        Long paciente = crearPaciente();
        for (int invalida : new int[] {19, 51}) {
            assertThatThrownBy(() -> servicio.registrarPieza(paciente, invalida, EstadoPieza.SANA, null, EMAIL_SEED))
                    .isInstanceOf(PiezaFdiInvalidaException.class);
        }
        assertThat(historiasDe(paciente)).isZero();
    }

    @Test
    void todosLosEstadosAceptados() {
        Long paciente = crearPaciente();
        for (EstadoPieza estado : EstadoPieza.values()) {
            assertThat(servicio.registrarPieza(paciente, 21, estado, null, EMAIL_SEED).estado()).isEqualTo(estado);
        }
    }

    // --- 3.3 evoluciones ---

    @Test
    void altaConTurnoDelPacienteYFechaPorDefectoHoy() {
        Long paciente = crearPaciente();
        Long turno = insertarTurno(paciente, crearPrestacion(30), EstadoTurno.ATENDIDO, dia(-1, "10:00"),
                dia(-1, "10:30"));

        EvolucionVista e = servicio.registrarEvolucion(paciente,
                new DatosEvolucion(" Obturación pieza 16 ", null, turno), EMAIL_SEED);

        assertThat(e.id()).isNotNull();
        assertThat(e.nota()).isEqualTo("Obturación pieza 16");
        assertThat(e.turnoId()).isEqualTo(turno);
        assertThat(e.fecha()).isEqualTo(HOY);
        assertThat(e.autor().id()).isEqualTo(usuarioSeedId());
        assertThat(e.creadaEn()).isEqualTo(AHORA);
        assertThat(e.editadaEn()).isNull();
        assertThat(e.editable()).isTrue();
    }

    @Test
    void turnoDeOtroPacienteOInexistenteEs422() {
        Long paciente = crearPaciente();
        Long otro = crearPaciente();
        Long turnoAjeno = insertarTurno(otro, crearPrestacion(30), EstadoTurno.ATENDIDO, dia(-1, "10:00"),
                dia(-1, "10:30"));

        assertThatThrownBy(() -> servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Nota", null, turnoAjeno), EMAIL_SEED))
                .isInstanceOf(TurnoDeOtroPacienteException.class);
        assertThatThrownBy(() -> servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Nota", null, 999_999_999L), EMAIL_SEED))
                .isInstanceOf(TurnoDeOtroPacienteException.class);
        assertThat(historiasDe(paciente)).isZero();
    }

    @Test
    void fechaFuturaEs422YHoyEsAceptada() {
        Long paciente = crearPaciente();
        assertThatThrownBy(() -> servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Nota", HOY.plusDays(1), null), EMAIL_SEED))
                .isInstanceOf(FechaEvolucionFuturaException.class);
        assertThat(servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota", HOY, null), EMAIL_SEED)
                .fecha()).isEqualTo(HOY);
    }

    @Test
    void listadoEnOrdenCronologico() {
        Long paciente = crearPaciente();
        EvolucionVista a = servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Primera del 20", LocalDate.of(2026, 11, 20), null), EMAIL_SEED);
        reloj.advance(Duration.ofMinutes(1));
        EvolucionVista b = servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Del 5 de octubre", LocalDate.of(2026, 10, 5), null), EMAIL_SEED);
        reloj.advance(Duration.ofMinutes(1));
        EvolucionVista c = servicio.registrarEvolucion(paciente,
                new DatosEvolucion("Segunda del 20", LocalDate.of(2026, 11, 20), null), EMAIL_SEED);

        List<Long> ids = servicio.listarEvoluciones(paciente, EMAIL_SEED).stream().map(EvolucionVista::id).toList();

        assertThat(ids).containsExactly(b.id(), a.id(), c.id());
    }

    @Test
    void edicionDentroDeLaVentanaConservaElAlta() {
        Long paciente = crearPaciente();
        EvolucionVista e = servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota", null, null), EMAIL_SEED);
        reloj.advance(Duration.ofHours(10));

        EvolucionVista editada = servicio.editarEvolucion(paciente, e.id(),
                new DatosEvolucion("Nota corregida", null, null), EMAIL_SEED);

        assertThat(editada.nota()).isEqualTo("Nota corregida");
        assertThat(editada.fecha()).isEqualTo(e.fecha());
        assertThat(editada.creadaEn()).isEqualTo(AHORA);
        assertThat(editada.editadaEn()).isEqualTo(AHORA.plus(Duration.ofHours(10)));
    }

    @Test
    void edicionJustoEnElLimiteSeAceptaYUnSegundoDespuesNo() {
        Long paciente = crearPaciente();
        EvolucionVista e = servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota", null, null), EMAIL_SEED);
        reloj.advance(Duration.ofHours(24));
        servicio.editarEvolucion(paciente, e.id(), new DatosEvolucion("En el límite", null, null), EMAIL_SEED);
        reloj.advance(Duration.ofSeconds(1));

        assertThatThrownBy(() -> servicio.editarEvolucion(paciente, e.id(),
                new DatosEvolucion("Tarde", null, null), EMAIL_SEED))
                .isInstanceOf(EvolucionFueraDeVentanaException.class);
        assertThat(servicio.listarEvoluciones(paciente, EMAIL_SEED)).singleElement().satisfies(v -> {
            assertThat(v.nota()).isEqualTo("En el límite");
            assertThat(v.editable()).isFalse();
        });
    }

    @Test
    void otroUsuarioNoEsElAutor() {
        Long paciente = crearPaciente();
        String otro = crearUsuario("it-hc", "Ficticia-Otro-123", true);
        EvolucionVista e = servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota", null, null), otro);

        assertThat(servicio.listarEvoluciones(paciente, EMAIL_SEED)).singleElement()
                .satisfies(v -> assertThat(v.editable()).isFalse());
        assertThatThrownBy(() -> servicio.editarEvolucion(paciente, e.id(),
                new DatosEvolucion("Nueva", null, null), EMAIL_SEED))
                .isInstanceOf(NoEsAutorException.class);
    }

    @Test
    void evolucionDeOtroPacienteEs404() {
        Long paciente = crearPaciente();
        Long otro = crearPaciente();
        servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota", null, null), EMAIL_SEED);
        EvolucionVista ajena = servicio.registrarEvolucion(otro, new DatosEvolucion("Nota", null, null), EMAIL_SEED);

        assertThatThrownBy(() -> servicio.editarEvolucion(paciente, ajena.id(),
                new DatosEvolucion("Nueva", null, null), EMAIL_SEED))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
