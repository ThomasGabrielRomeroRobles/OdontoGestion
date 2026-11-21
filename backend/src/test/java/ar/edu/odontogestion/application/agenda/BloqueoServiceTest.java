package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.BloqueoAgenda;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BloqueoServiceTest {

    private static final Long CID = 7L;
    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");
    private static final String DESDE = "2026-12-24T03:00:00Z";
    private static final String HASTA = "2026-12-26T03:00:00Z";

    @Mock
    private ConsultorioActual actual;

    @Mock
    private ConsultorioRepository consultorios;

    @Mock
    private BloqueoAgendaRepository bloqueos;

    private Consultorio consultorio;
    private BloqueoService servicio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        lenient().when(actual.id()).thenReturn(CID);
        lenient().when(consultorios.getReferenceById(CID)).thenReturn(consultorio);
        lenient().when(bloqueos.saveAndFlush(any(BloqueoAgenda.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new BloqueoService(actual, consultorios, bloqueos, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static void esperaErrorEn(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).contains(campo));
    }

    // --- alta y edición ---

    @Test
    void altaValidaGuardaEnUtcYConMotivoRecortado() {
        BloqueoVista vista = servicio.crear(new DatosBloqueo(DESDE, HASTA, "  Fiestas "));
        assertThat(vista.desde()).isEqualTo(Instant.parse(DESDE));
        assertThat(vista.hasta()).isEqualTo(Instant.parse(HASTA));
        assertThat(vista.motivo()).isEqualTo("Fiestas");
    }

    @Test
    void aceptaOffsetsYLosConvierteAUtc() {
        BloqueoVista vista = servicio.crear(new DatosBloqueo("2026-12-24T00:00:00-03:00", "2026-12-26T00:00-03:00", null));
        assertThat(vista.desde()).isEqualTo(Instant.parse(DESDE));
        assertThat(vista.hasta()).isEqualTo(Instant.parse(HASTA));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void sinMotivoSeGuardaTextoVacioNuncaNull(String motivo) {
        ArgumentCaptor<BloqueoAgenda> guardado = ArgumentCaptor.forClass(BloqueoAgenda.class);
        assertThat(servicio.crear(new DatosBloqueo(DESDE, HASTA, motivo)).motivo()).isEmpty();
        verify(bloqueos).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getMotivo()).isEqualTo("");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-24T00:00:00", "no-es-fecha", "24/12/2026 00:00", "2026-13-01T00:00:00Z"})
    void fechaSinZonaOMalFormadaDa422EnSuCampo(String valor) {
        esperaErrorEn(() -> servicio.crear(new DatosBloqueo(valor, HASTA, null)), "desde");
        esperaErrorEn(() -> servicio.crear(new DatosBloqueo(DESDE, valor, null)), "hasta");
        verify(bloqueos, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-24T03:00:00Z", "2026-12-23T03:00:00Z"})
    void finNoPosteriorAlInicioDa422EnHasta(String hasta) {
        esperaErrorEn(() -> servicio.crear(new DatosBloqueo(DESDE, hasta, null)), "hasta");
    }

    @Test
    void motivoDeMasDeDoscientosCaracteresDa422() {
        esperaErrorEn(() -> servicio.crear(new DatosBloqueo(DESDE, HASTA, "x".repeat(201))), "motivo");
        assertThat(servicio.crear(new DatosBloqueo(DESDE, HASTA, "x".repeat(200))).motivo()).hasSize(200);
    }

    @Test
    void edicionActualizaYDeInexistenteDa404() {
        BloqueoAgenda existente = BloqueoAgenda.nuevo(consultorio, Instant.parse(DESDE), Instant.parse(HASTA), "");
        when(bloqueos.findByIdAndConsultorio_Id(3L, CID)).thenReturn(Optional.of(existente));
        assertThat(servicio.actualizar(3L, new DatosBloqueo(DESDE, HASTA, "Vacaciones")).motivo()).isEqualTo("Vacaciones");

        when(bloqueos.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.actualizar(99L, new DatosBloqueo(DESDE, HASTA, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void edicionInvalidaNoModificaElBloqueo() {
        BloqueoAgenda existente = BloqueoAgenda.nuevo(consultorio, Instant.parse(DESDE), Instant.parse(HASTA), "Fiestas");
        when(bloqueos.findByIdAndConsultorio_Id(3L, CID)).thenReturn(Optional.of(existente));
        esperaErrorEn(() -> servicio.actualizar(3L, new DatosBloqueo(HASTA, DESDE, "Otro")), "hasta");
        assertThat(existente.getMotivo()).isEqualTo("Fiestas");
    }

    @Test
    void bajaYBajaDeInexistente() {
        BloqueoAgenda existente = BloqueoAgenda.nuevo(consultorio, Instant.parse(DESDE), Instant.parse(HASTA), "");
        when(bloqueos.findByIdAndConsultorio_Id(3L, CID)).thenReturn(Optional.of(existente));
        servicio.eliminar(3L);
        verify(bloqueos).delete(existente);

        when(bloqueos.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // --- listado: tabla de D12 ---

    @Test
    void sinFiltrosDesdeAhoraSinLimite() {
        when(bloqueos.findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(CID, AHORA)).thenReturn(List.of());
        assertThat(servicio.listar(null, null)).isEmpty();
        verify(bloqueos).findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(CID, AHORA);
    }

    @Test
    void soloDesdeUsaEseInicioSinLimite() {
        Instant desde = Instant.parse("2026-11-01T00:00:00Z");
        servicio.listar("2026-11-01T00:00:00Z", null);
        verify(bloqueos).findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(CID, desde);
    }

    @Test
    void soloHastaUsaAhoraComoInicio() {
        Instant hasta = Instant.parse("2026-12-31T00:00:00Z");
        servicio.listar(null, "2026-12-31T00:00:00Z");
        verify(bloqueos).findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(CID, AHORA, hasta);
    }

    @Test
    void ambosFiltrosDefinenElRango() {
        servicio.listar("2026-12-01T03:00:00Z", "2027-01-01T03:00:00Z");
        verify(bloqueos).findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(CID,
                Instant.parse("2026-12-01T03:00:00Z"), Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    void filtrosVaciosCuentanComoAusentes() {
        servicio.listar("", " ");
        verify(bloqueos).findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(CID, AHORA);
    }

    @Test
    void filtroMalFormadoDa422EnSuCampo() {
        esperaErrorEn(() -> servicio.listar("ayer", null), "desde");
        esperaErrorEn(() -> servicio.listar(null, "2026-12-31"), "hasta");
    }

    @Test
    void conAmbosHastaNoPosteriorADesdeDa422EnHasta() {
        esperaErrorEn(() -> servicio.listar("2026-12-01T03:00:00Z", "2026-12-01T03:00:00Z"), "hasta");
    }
}
