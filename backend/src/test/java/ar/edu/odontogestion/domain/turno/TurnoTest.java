package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Dominio puro (sin Spring): fábricas, cálculo de {@code fin} y transiciones (design D4). */
class TurnoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-20T12:30:00Z");
    private static final int TOLERANCIA = 15;
    /** Inicio + tolerancia exacto: el primer instante en que se puede marcar ausente (RN-AS-01). */
    private static final Instant FIN_TOLERANCIA = INICIO.plus(Duration.ofMinutes(TOLERANCIA));
    private static final Instant PASADA_TOLERANCIA = INICIO.plus(Duration.ofMinutes(20));

    private final Consultorio consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
    private final Paciente paciente = Paciente.nuevo(consultorio,
            new DatosPaciente("Ana", "Ficticia", "30111222", null, "+54 11 5555-0000", "ana@ejemplo.test", null));

    private Prestacion prestacion(int minutos) {
        return Prestacion.nueva(consultorio, "Consulta", minutos);
    }

    private Turno reservado() {
        return Turno.reservado(consultorio, paciente, prestacion(30), INICIO, OrigenTurno.ODONTOLOGO);
    }

    private Turno pendiente() {
        return Turno.pendiente(consultorio, paciente, prestacion(30), INICIO, OrigenTurno.PUBLICA, VENCE);
    }

    private Turno enEstado(EstadoTurno estado) {
        Turno t = reservado();
        switch (estado) {
            case RESERVADO -> { }
            case CONFIRMADO -> t.confirmar();
            case ATENDIDO -> t.marcarAtendido();
            case AUSENTE -> t.marcarAusente(PASADA_TOLERANCIA, TOLERANCIA);
            case CANCELADO -> t.cancelar(CanceladoPor.ODONTOLOGO, null);
            case PENDIENTE_CONFIRMACION -> t = pendiente();
            case EXPIRADO -> {
                t = pendiente();
                t.expirar();
            }
        }
        return t;
    }

    @Test
    void reservadoCalculaFinConLaDuracionYNaceReservado() {
        Turno t = Turno.reservado(consultorio, paciente, prestacion(45), INICIO, OrigenTurno.ODONTOLOGO);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(t.getInicio()).isEqualTo(INICIO);
        assertThat(t.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(45)));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.ODONTOLOGO);
        assertThat(t.getPendienteHasta()).isNull();
        assertThat(t.getCanceladoPor()).isNull();
    }

    @Test
    void reservadoConOtraDuracionYOrigenCalculaOtroFin() {
        Turno t = Turno.reservado(consultorio, paciente, prestacion(15), INICIO, OrigenTurno.LISTA_ESPERA);

        assertThat(t.getFin()).isEqualTo(INICIO.plusSeconds(15 * 60));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.LISTA_ESPERA);
    }

    @Test
    void pendienteNaceConVencimiento() {
        Turno t = pendiente();

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.PENDIENTE_CONFIRMACION);
        assertThat(t.getPendienteHasta()).isEqualTo(VENCE);
        assertThat(t.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(30)));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.PUBLICA);
    }

    @Test
    void marcarAtendidoDesdeReservadoYConfirmado() {
        Turno r = enEstado(EstadoTurno.RESERVADO);
        r.marcarAtendido();
        Turno c = enEstado(EstadoTurno.CONFIRMADO);
        c.marcarAtendido();

        assertThat(r.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(c.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
    }

    @Test
    void marcarAtendidoDesdeCanceladoOPendienteLanzaYNoCambiaElEstado() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.CANCELADO, EstadoTurno.PENDIENTE_CONFIRMACION,
                EstadoTurno.EXPIRADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(t::marcarAtendido)
                    .as(origen.name())
                    .isInstanceOf(TransicionInvalidaException.class)
                    .extracting(e -> ((TransicionInvalidaException) e).codigo())
                    .isEqualTo("transicion-invalida");
            assertThat(t.getEstado()).as(origen.name()).isEqualTo(origen);
        }
    }

    @Test
    void transicionInvalidaEsUnaReglaDeNegocioConMensajeSinDatosPersonales() {
        Turno t = enEstado(EstadoTurno.CANCELADO);

        assertThatThrownBy(t::marcarAtendido)
                .isInstanceOf(ar.edu.odontogestion.domain.error.ReglaNegocioException.class)
                .hasMessageContaining("CANCELADO")
                .hasMessageContaining("ATENDIDO")
                .hasMessageNotContaining("Ficticia")
                .hasMessageNotContaining("ejemplo.test");
    }

    @Test
    void expirarSoloDesdePendiente() {
        Turno p = pendiente();
        p.expirar();

        assertThat(p.getEstado()).isEqualTo(EstadoTurno.EXPIRADO);
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO,
                EstadoTurno.CANCELADO, EstadoTurno.EXPIRADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            Turno t = enEstado(origen);
            assertThatThrownBy(t::expirar).as(origen.name()).isInstanceOf(TransicionInvalidaException.class);
            assertThat(t.getEstado()).isEqualTo(origen);
        }
    }

    @Test
    void confirmarSolicitudPasaDePendienteAReservado() {
        Turno p = pendiente();
        p.confirmarSolicitud();

        assertThat(p.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThatThrownBy(p::confirmarSolicitud).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void confirmarSoloDesdeReservado() {
        Turno r = reservado();
        r.confirmar();

        assertThat(r.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThatThrownBy(r::confirmar).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(pendiente()::confirmar).isInstanceOf(TransicionInvalidaException.class);
    }

    // ---- C-08: marcar ausente y revertir (design D2) ----

    @Test
    void marcarAusenteDesdeReservadoYConfirmadoGuardaLaMarcaYElEstadoPrevio() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO}) {
            Turno t = enEstado(origen);

            t.marcarAusente(FIN_TOLERANCIA, TOLERANCIA);

            assertThat(t.getEstado()).as(origen.name()).isEqualTo(EstadoTurno.AUSENTE);
            assertThat(t.getAusenteMarcadoEn()).isEqualTo(FIN_TOLERANCIA);
            assertThat(t.getEstadoPrevioAusencia()).isEqualTo(origen);
        }
    }

    @Test
    void marcarAusenteAntesDeLaToleranciaEsRechazadoSinCambios() {
        Turno t = reservado();

        assertThatThrownBy(() -> t.marcarAusente(FIN_TOLERANCIA.minusSeconds(1), TOLERANCIA))
                .isInstanceOf(AusenteAntesDeToleranciaException.class);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(t.getAusenteMarcadoEn()).isNull();
        assertThat(t.getEstadoPrevioAusencia()).isNull();
    }

    @Test
    void laToleranciaDependeDelParametro() {
        Turno t = reservado();

        assertThatThrownBy(() -> t.marcarAusente(PASADA_TOLERANCIA, 30))
                .isInstanceOf(AusenteAntesDeToleranciaException.class);
        t.marcarAusente(PASADA_TOLERANCIA, 0);
        assertThat(t.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
    }

    @Test
    void marcarAusenteDesdeOtrosEstadosEsTransicionInvalidaSinCambios() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.PENDIENTE_CONFIRMACION, EstadoTurno.CANCELADO,
                EstadoTurno.EXPIRADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            Turno t = enEstado(origen);
            Instant marcaAntes = t.getAusenteMarcadoEn();

            assertThatThrownBy(() -> t.marcarAusente(PASADA_TOLERANCIA.plusSeconds(60), TOLERANCIA))
                    .as(origen.name()).isInstanceOf(TransicionInvalidaException.class);

            assertThat(t.getEstado()).isEqualTo(origen);
            assertThat(t.getAusenteMarcadoEn()).isEqualTo(marcaAntes);
        }
    }

    @Test
    void elEstadoSeValidaAntesQueLaTolerancia() {
        // Un turno atendido antes de la tolerancia informa la transición, no la tolerancia.
        Turno t = enEstado(EstadoTurno.ATENDIDO);

        assertThatThrownBy(() -> t.marcarAusente(INICIO, TOLERANCIA)).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void revertirDentroDeLas24HorasVuelveAlEstadoPrevioYLimpiaLaMarca() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO}) {
            Turno t = enEstado(origen);
            t.marcarAusente(PASADA_TOLERANCIA, TOLERANCIA);

            t.revertirAusencia(PASADA_TOLERANCIA.plus(Duration.ofHours(24)).minusSeconds(1));

            assertThat(t.getEstado()).as(origen.name()).isEqualTo(origen);
            assertThat(t.getAusenteMarcadoEn()).isNull();
            assertThat(t.getEstadoPrevioAusencia()).isNull();
        }
    }

    @Test
    void revertirAlCumplirseLas24HorasEsRechazadoSinCambios() {
        Turno t = reservado();
        t.marcarAusente(PASADA_TOLERANCIA, TOLERANCIA);

        assertThatThrownBy(() -> t.revertirAusencia(PASADA_TOLERANCIA.plus(Duration.ofHours(24))))
                .isInstanceOf(ReversionFueraDePlazoException.class);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(t.getAusenteMarcadoEn()).isEqualTo(PASADA_TOLERANCIA);
        assertThat(t.getEstadoPrevioAusencia()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void revertirUnTurnoQueNoEstaAusenteEsTransicionInvalida() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.ATENDIDO,
                EstadoTurno.CANCELADO}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(() -> t.revertirAusencia(PASADA_TOLERANCIA)).as(origen.name())
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThat(t.getEstado()).isEqualTo(origen);
        }
    }

    @Test
    void despuesDeRevertirSePuedeVolverAMarcarAusente() {
        Turno t = reservado();
        t.marcarAusente(PASADA_TOLERANCIA, TOLERANCIA);
        t.revertirAusencia(PASADA_TOLERANCIA.plusSeconds(60));

        Instant segundaMarca = PASADA_TOLERANCIA.plusSeconds(120);
        t.marcarAusente(segundaMarca, TOLERANCIA);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(t.getAusenteMarcadoEn()).isEqualTo(segundaMarca);
    }

    @Test
    void unTurnoAusenteNoSeMueveNiSeCancelaNiSeAtiende() {
        Turno t = enEstado(EstadoTurno.AUSENTE);

        assertThatThrownBy(() -> t.mover(INICIO.plus(Duration.ofDays(1)))).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(() -> t.cancelar(CanceladoPor.ODONTOLOGO, null))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(t::marcarAtendido).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(t::confirmar).isInstanceOf(TransicionInvalidaException.class);
        assertThat(t.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
    }

    // ---- Grupo 7: cancelar y mover ----

    @Test
    void cancelarDesdePendienteReservadoOConfirmadoRegistraQuienYElMotivo() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.PENDIENTE_CONFIRMACION, EstadoTurno.RESERVADO,
                EstadoTurno.CONFIRMADO}) {
            Turno t = enEstado(origen);

            t.cancelar(CanceladoPor.ODONTOLOGO, "Imprevisto del consultorio");

            assertThat(t.getEstado()).as(origen.name()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(t.getCanceladoPor()).isEqualTo(CanceladoPor.ODONTOLOGO);
            assertThat(t.getMotivoCancelacion()).isEqualTo("Imprevisto del consultorio");
        }
    }

    @Test
    void cancelarConMotivoVacioOEnBlancoLoGuardaComoNulo() {
        for (String vacio : new String[] {null, "", "   "}) {
            Turno t = enEstado(EstadoTurno.CONFIRMADO);

            t.cancelar(CanceladoPor.PACIENTE, vacio);

            assertThat(t.getMotivoCancelacion()).as("motivo [" + vacio + "]").isNull();
            assertThat(t.getCanceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
        }
    }

    @Test
    void cancelarAceptaUnMotivoDe200YRechazaUnoDe201() {
        Turno ok = enEstado(EstadoTurno.RESERVADO);
        ok.cancelar(CanceladoPor.ODONTOLOGO, "x".repeat(200));
        Turno largo = enEstado(EstadoTurno.RESERVADO);

        assertThat(ok.getMotivoCancelacion()).hasSize(200);
        assertThatThrownBy(() -> largo.cancelar(CanceladoPor.ODONTOLOGO, "x".repeat(201)))
                .isInstanceOfSatisfying(ar.edu.odontogestion.domain.error.ValidacionDominioException.class,
                        e -> assertThat(e.errores().get(0).field()).isEqualTo("motivo"));
        assertThat(largo.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(largo.getCanceladoPor()).isNull();
    }

    @Test
    void cancelarUnTurnoFinalizadoLanzaYNoModificaNada() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE,
                EstadoTurno.CANCELADO, EstadoTurno.EXPIRADO}) {
            Turno t = enEstado(origen);
            CanceladoPor antes = t.getCanceladoPor();
            String motivoAntes = t.getMotivoCancelacion();

            assertThatThrownBy(() -> t.cancelar(CanceladoPor.ODONTOLOGO, "otro motivo"))
                    .as(origen.name()).isInstanceOf(TransicionInvalidaException.class);

            assertThat(t.getEstado()).isEqualTo(origen);
            assertThat(t.getCanceladoPor()).isEqualTo(antes);
            assertThat(t.getMotivoCancelacion()).isEqualTo(motivoAntes);
        }
    }

    @Test
    void moverDesdeReservadoConservaDuracionEstadoPacienteYPrestacionYDevuelveElRangoAnterior() {
        Turno t = Turno.reservado(consultorio, paciente, prestacion(45), INICIO, OrigenTurno.ODONTOLOGO);
        Instant nuevo = INICIO.plus(Duration.ofHours(2));

        ar.edu.odontogestion.domain.agenda.Hueco anterior = t.mover(nuevo);

        assertThat(anterior).isEqualTo(new ar.edu.odontogestion.domain.agenda.Hueco(INICIO,
                INICIO.plus(Duration.ofMinutes(45))));
        assertThat(t.getInicio()).isEqualTo(nuevo);
        assertThat(t.getFin()).isEqualTo(nuevo.plus(Duration.ofMinutes(45)));
        assertThat(t.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(t.getPaciente()).isSameAs(paciente);
        assertThat(t.getPrestacion().getDuracionMin()).isEqualTo(45);
    }

    @Test
    void moverDesdeConfirmadoConservaElEstadoYAdmiteUnSolapeConSuPropioRango() {
        Turno t = enEstado(EstadoTurno.CONFIRMADO);

        t.mover(INICIO.plus(Duration.ofMinutes(15)));

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(t.getInicio()).isEqualTo(INICIO.plus(Duration.ofMinutes(15)));
        assertThat(t.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(45)));
    }

    @Test
    void moverDesdeCualquierOtroEstadoLanzaYNoCambiaElHorario() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.PENDIENTE_CONFIRMACION, EstadoTurno.CANCELADO,
                EstadoTurno.EXPIRADO, EstadoTurno.AUSENTE, EstadoTurno.ATENDIDO}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(() -> t.mover(INICIO.plus(Duration.ofHours(1))))
                    .as(origen.name())
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining(origen.name());

            assertThat(t.getInicio()).isEqualTo(INICIO);
            assertThat(t.getEstado()).isEqualTo(origen);
        }
    }

    // --- C-09: acciones del paciente ---

    private static final int LIMITE_HORAS = 2;

    @Test
    void limiteCancelacionEsElInicioMenosLasHorasDelLimite() {
        Turno t = reservado();

        assertThat(t.limiteCancelacion(2)).isEqualTo(INICIO.minus(Duration.ofHours(2)));
        assertThat(t.limiteCancelacion(24)).isEqualTo(INICIO.minus(Duration.ofHours(24)));
    }

    @Test
    void cancelarPorPacienteDentroDelLimiteCancelaConPacienteYSinMotivo() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO}) {
            Turno t = enEstado(origen);

            t.cancelarPorPaciente(INICIO.minus(Duration.ofHours(3)), LIMITE_HORAS);

            assertThat(t.getEstado()).as(origen.name()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(t.getCanceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
            assertThat(t.getMotivoCancelacion()).isNull();
        }
    }

    @Test
    void cancelarPorPacienteJustoEnElLimiteEsInclusivo() {
        Turno t = reservado();

        t.cancelarPorPaciente(INICIO.minus(Duration.ofHours(LIMITE_HORAS)), LIMITE_HORAS);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void cancelarPorPacienteFueraDelLimiteLanzaYNoCambiaNada() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(() -> t.cancelarPorPaciente(
                    INICIO.minus(Duration.ofHours(LIMITE_HORAS)).plusSeconds(1), LIMITE_HORAS))
                    .isInstanceOf(LimiteCancelacionException.class);
            assertThatThrownBy(() -> t.cancelarPorPaciente(INICIO.minus(Duration.ofMinutes(119)), LIMITE_HORAS))
                    .isInstanceOf(LimiteCancelacionException.class);

            assertThat(t.getEstado()).isEqualTo(origen);
            assertThat(t.getCanceladoPor()).isNull();
        }
    }

    @Test
    void cancelarPorPacienteDesdeOtrosEstadosEsTransicionInvalidaAunDentroDelLimite() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE,
                EstadoTurno.CANCELADO, EstadoTurno.EXPIRADO, EstadoTurno.PENDIENTE_CONFIRMACION}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(() -> t.cancelarPorPaciente(INICIO.minus(Duration.ofHours(10)), LIMITE_HORAS))
                    .as(origen.name()).isInstanceOf(TransicionInvalidaException.class);
            assertThat(t.getEstado()).isEqualTo(origen);
        }
    }

    @Test
    void transicionInvalidaGanaSobreElLimiteCuandoElEstadoYaNoAdmiteCancelar() {
        Turno atendido = enEstado(EstadoTurno.ATENDIDO);

        assertThatThrownBy(() -> atendido.cancelarPorPaciente(INICIO.plus(Duration.ofHours(1)), LIMITE_HORAS))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void pendienteVencidaEsVerdaderaDesdeElInstanteDeVencimiento() {
        Turno t = pendiente();

        assertThat(t.pendienteVencida(VENCE.minusSeconds(1))).isFalse();
        assertThat(t.pendienteVencida(VENCE)).isTrue();
        assertThat(t.pendienteVencida(VENCE.plusSeconds(1))).isTrue();
    }

    @Test
    void pendienteVencidaEsFalsaParaTurnosQueNoSonPendientes() {
        assertThat(reservado().pendienteVencida(VENCE.plus(Duration.ofDays(30)))).isFalse();
        assertThat(enEstado(EstadoTurno.EXPIRADO).pendienteVencida(VENCE.plus(Duration.ofDays(30)))).isFalse();
    }
}
