package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.application.turno.ReservaTurnoService;
import ar.edu.odontogestion.application.turno.SolicitudReserva;
import ar.edu.odontogestion.application.turno.TurnoDto;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.listaespera.AvisoHueco;
import ar.edu.odontogestion.domain.listaespera.AvisoHuecoRepository;
import ar.edu.odontogestion.domain.listaespera.AvisoNoDisponibleException;
import ar.edu.odontogestion.domain.listaespera.AvisoVencidoException;
import ar.edu.odontogestion.domain.listaespera.EstadoAviso;
import ar.edu.odontogestion.domain.listaespera.EstadoEntrada;
import ar.edu.odontogestion.domain.listaespera.HuecoDeAviso;
import ar.edu.odontogestion.domain.listaespera.CompatibilidadHueco;
import ar.edu.odontogestion.domain.listaespera.EntradaListaEspera;
import ar.edu.odontogestion.domain.listaespera.EntradaListaEsperaRepository;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisos de hueco liberado (C-10 D4, D5, D6): los genera al liberarse un hueco, los acepta (el primero gana) y los
 * vence. Los logs llevan solo ids, nunca emails, DNI, nombres ni tokens.
 */
@Service
public class AvisoHuecoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoHuecoService.class);

    private final ConsultorioActual consultorioActual;
    private final ConsultorioService consultorioService;
    private final ConsultorioRepository consultorios;
    private final PrestacionRepository prestaciones;
    private final EntradaListaEsperaRepository entradas;
    private final AvisoHuecoRepository avisos;
    private final NotificationService notificaciones;
    private final TokenAccesoService tokens;
    private final ReservaTurnoService reservas;
    private final AppProperties appProperties;
    private final Clock clock;

    public AvisoHuecoService(ConsultorioActual consultorioActual, ConsultorioService consultorioService,
            ConsultorioRepository consultorios, PrestacionRepository prestaciones,
            EntradaListaEsperaRepository entradas, AvisoHuecoRepository avisos, NotificationService notificaciones,
            TokenAccesoService tokens, ReservaTurnoService reservas, AppProperties appProperties, Clock clock) {
        this.tokens = tokens;
        this.reservas = reservas;
        this.consultorioActual = consultorioActual;
        this.consultorioService = consultorioService;
        this.consultorios = consultorios;
        this.prestaciones = prestaciones;
        this.entradas = entradas;
        this.avisos = avisos;
        this.notificaciones = notificaciones;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /**
     * Crea un aviso {@code ENVIADO} y encola el email por cada entrada {@code ACTIVA} compatible con el hueco, todo en
     * una transacción propia (en {@code AFTER_COMMIT} la original ya no admite escrituras). Idempotente: reprocesar el
     * mismo evento no duplica avisos ni emails.
     *
     * @return cantidad de avisos creados
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int generarAvisos(HuecoLiberado hueco) {
        Instant ahora = clock.instant();
        ParametrosConsultorio parametros = consultorioService.parametrosVigentes();
        if (!CompatibilidadHueco.ofrecible(hueco.inicio(), ahora, parametros.anticipacionMinReservaMin())) {
            return 0;
        }
        LocalDate fechaLocal = CompatibilidadHueco.fechaLocal(hueco.inicio(), appProperties.timezone());
        List<EntradaListaEspera> compatibles = entradas.buscarCompatibles(hueco.consultorioId(),
                hueco.prestacionId(), fechaLocal);
        if (compatibles.isEmpty()) {
            return 0;
        }
        Instant venceEn = CompatibilidadHueco.venceEn(ahora, parametros.vencimientoAvisoMin(), hueco.inicio());
        String consultorio = consultorios.findById(hueco.consultorioId()).orElseThrow().getNombre();
        String prestacion = prestaciones.findByIdAndConsultorio_Id(hueco.prestacionId(), hueco.consultorioId())
                .orElseThrow().getNombre();
        int creados = 0;
        for (EntradaListaEspera entrada : compatibles) {
            String email = entrada.getPaciente().getEmail();
            if (email == null || email.isBlank()) {
                log.warn("No se avisa a la entrada {}: el paciente no tiene email", entrada.getId());
                continue;
            }
            List<Long> insertado = avisos.insertarSiNoExiste(hueco.consultorioId(), entrada.getId(),
                    hueco.turnoOrigenId(), hueco.prestacionId(), hueco.inicio(), hueco.fin(), venceEn, ahora);
            if (insertado.isEmpty()) {
                continue;
            }
            notificaciones.encolar(TipoNotificacion.AVISO_HUECO, email, new ReferenciaToken.Aviso(insertado.get(0)),
                    Map.of("consultorio", consultorio, "inicio", hueco.inicio().toString(),
                            "prestacion", prestacion),
                    List.of(new EnlaceProgramado(PropositoToken.ACEPTAR_AVISO, venceEn)));
            creados++;
        }
        return creados;
    }

    /**
     * Qué muestra el enlace, sin consumirlo. Un aviso {@code ENVIADO} vencido lanza {@link AvisoVencidoException}
     * (el llamador lo marca {@code EXPIRADO} en una transacción nueva y responde 410).
     *
     * @throws TokenInvalidoException token inexistente, usado, vencido, revocado o de otro propósito (410)
     */
    @Transactional(readOnly = true)
    public ResumenAvisoResponse resumen(String token) {
        Long consultorioId = consultorioActual.id();
        AvisoHueco aviso = avisoDe(token, consultorioId);
        if (aviso.getEstado() == EstadoAviso.ENVIADO && aviso.vencido(clock.instant())) {
            throw new AvisoVencidoException(aviso.getId());
        }
        boolean disponible = aviso.getEstado() == EstadoAviso.ENVIADO
                && aviso.getEntrada().getEstado() == EstadoEntrada.ACTIVA;
        return new ResumenAvisoResponse(aviso.getConsultorio().getNombre(), nombrePrestacion(aviso),
                aviso.getInicio(), aviso.getFin(), aviso.getVenceEn(), disponible);
    }

    /**
     * Acepta el aviso: el primero gana (C-10 D5). Orden de bloqueos: avisos del hueco ({@code FOR UPDATE}, por id) →
     * token propio → inserción en {@code turno} (la superposición la decide {@code ex_turno_sin_superposicion}).
     * Todo en una transacción: ante cualquier excepción se revierte, también el consumo del token.
     *
     * @throws TokenInvalidoException enlace inválido (410)
     * @throws AvisoVencidoException el aviso venció (410; el llamador lo marca {@code EXPIRADO})
     * @throws AvisoNoDisponibleException otro aviso del hueco ganó, el aviso se cerró o la entrada ya no está activa (409)
     * @throws ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException el rango lo ocupó otro turno (409)
     */
    @Transactional
    public AvisoAceptadoResponse aceptar(String token) {
        Long consultorioId = consultorioActual.id();
        Long avisoId = idDeAviso(token);
        HuecoDeAviso hueco = avisos.huecoDe(avisoId, consultorioId).orElseThrow(TokenInvalidoException::new);
        // Serializa en la base las aceptaciones del mismo hueco; las entidades se cargan recién acá, con el
        // estado vigente tras la espera del bloqueo.
        AvisoHueco aviso = avisos.bloquearDelHueco(consultorioId, hueco.turnoOrigenId(), hueco.inicio()).stream()
                .filter(a -> a.getId().equals(avisoId)).findFirst().orElseThrow(TokenInvalidoException::new);
        Instant ahora = clock.instant();
        if (aviso.getEstado() == EstadoAviso.ENVIADO && aviso.vencido(ahora)) {
            throw new AvisoVencidoException(avisoId);
        }
        if (aviso.getEstado() != EstadoAviso.ENVIADO || aviso.getEntrada().getEstado() != EstadoEntrada.ACTIVA) {
            throw new AvisoNoDisponibleException();
        }
        Long pacienteId = aviso.getEntrada().getPaciente().getId();
        Long entradaId = aviso.getEntrada().getId();
        Long prestacionId = aviso.getPrestacionId();
        Instant inicio = aviso.getInicio();

        tokens.consumir(token, PropositoToken.ACEPTAR_AVISO);
        TurnoDto turno = reservas.reservar(SolicitudReserva.desdeListaEspera(pacienteId, prestacionId, inicio));

        // reservar() limpia el contexto de persistencia: se releen las filas (siguen bloqueadas) antes de cambiarlas.
        AvisoHueco ganador = avisos.findById(avisoId).orElseThrow();
        EntradaListaEspera entrada = entradas.findById(entradaId).orElseThrow();
        ganador.aceptar();
        entrada.cubrir(turno.id());
        avisos.saveAndFlush(ganador);
        entradas.saveAndFlush(entrada);
        avisos.cerrarEnviadosDelHueco(consultorioId, hueco.turnoOrigenId(), hueco.inicio(), avisoId, ahora);
        return new AvisoAceptadoResponse(turno.prestacion().nombre(), turno.inicio(), turno.fin(), turno.estado());
    }

    /** Marca {@code EXPIRADO} un aviso vencido, en una transacción propia (tras el rollback de la operación). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarExpirado(Long avisoId) {
        avisos.findByIdAndConsultorio_Id(avisoId, consultorioActual.id()).ifPresent(a -> {
            if (a.getEstado() == EstadoAviso.ENVIADO) {
                a.expirar();
                avisos.saveAndFlush(a);
            }
        });
    }

    /**
     * Cierra los avisos {@code ENVIADO} del hueco del aviso dado: el hueco lo ocupó otro turno (409 de la base). En
     * una transacción propia, tras el rollback de la aceptación.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cerrarAvisosDelHueco(Long avisoId) {
        Long consultorioId = consultorioActual.id();
        avisos.huecoDe(avisoId, consultorioId).ifPresent(h -> {
            avisos.bloquearDelHueco(consultorioId, h.turnoOrigenId(), h.inicio());
            avisos.cerrarEnviadosDelHueco(consultorioId, h.turnoOrigenId(), h.inicio(), -1L, clock.instant());
        });
    }

    /** Id del aviso al que apunta el token (solo lectura, sin consumirlo). */
    @Transactional(readOnly = true)
    public Long idDeAviso(String token) {
        if (!(tokens.verificar(token, PropositoToken.ACEPTAR_AVISO).referencia() instanceof ReferenciaToken.Aviso a)) {
            throw new TokenInvalidoException();
        }
        return a.id();
    }

    /** Barrido de vencimiento (C-10 D6): {@code ENVIADO} con {@code vence_en <= ahora} pasa a {@code EXPIRADO}. */
    @Transactional
    public int expirarVencidos() {
        return avisos.expirarVencidos(consultorioActual.id(), clock.instant());
    }

    private AvisoHueco avisoDe(String token, Long consultorioId) {
        Long id = idDeAviso(token);
        return avisos.findByIdAndConsultorio_Id(id, consultorioId).orElseThrow(TokenInvalidoException::new);
    }

    private String nombrePrestacion(AvisoHueco aviso) {
        return prestaciones.findByIdAndConsultorio_Id(aviso.getPrestacionId(), aviso.getConsultorio().getId())
                .orElseThrow().getNombre();
    }
}
