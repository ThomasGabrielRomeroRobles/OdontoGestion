package ar.edu.odontogestion.application.paciente;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.paciente.BusquedaPaciente;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Dni;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de pacientes del consultorio actual (C-04). Ningún mensaje ni log incluye DNI, email o teléfono
 * (design D9): el duplicado se informa con un texto fijo y la violación de unicidad de la base nunca llega al
 * handler genérico, que loguearía el mensaje de PostgreSQL con el valor de la clave.
 */
@Service
public class PacienteService {

    static final String RESTRICCION_DNI = "uq_paciente_consultorio_dni";
    static final String MENSAJE_DNI_DUPLICADO = "Ya existe un paciente con ese DNI.";
    static final String MENSAJE_NO_ENCONTRADO = "Paciente no encontrado.";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final PacienteRepository pacientes;
    private final Clock clock;

    public PacienteService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            PacienteRepository pacientes, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.pacientes = pacientes;
        this.clock = clock;
    }

    @Transactional
    public PacienteVista crear(DatosPaciente datos) {
        Long consultorioId = consultorioActual.id();
        Paciente nuevo = Paciente.nuevo(consultorios.getReferenceById(consultorioId), datos);
        if (pacientes.findByConsultorioIdAndDni(consultorioId, nuevo.getDni()).isPresent()) {
            throw new ConflictoException(MENSAJE_DNI_DUPLICADO);
        }
        return PacienteVista.desde(guardar(nuevo));
    }

    @Transactional(readOnly = true)
    public PacienteVista obtener(Long id) {
        return PacienteVista.desde(cargar(id));
    }

    @Transactional
    public PacienteVista actualizar(Long id, DatosPaciente datos) {
        Paciente paciente = cargar(id);
        String dni = Dni.normalizar(datos.dni());
        pacientes.findByConsultorioIdAndDni(consultorioActual.id(), dni)
                .filter(otro -> !otro.getId().equals(paciente.getId()))
                .ifPresent(otro -> {
                    throw new ConflictoException(MENSAJE_DNI_DUPLICADO);
                });
        paciente.actualizarDatos(datos);
        return PacienteVista.desde(guardar(paciente));
    }

    /**
     * Con {@code dni}: coincidencia exacta (normalizado). Si no, con {@code q} no vacío: texto o prefijo de DNI.
     * Sin ninguno: los primeros del consultorio. Siempre como máximo {@link PacienteRepository#MAXIMO_RESULTADOS}.
     */
    @Transactional(readOnly = true)
    public List<PacienteVista> buscar(String dni, String q) {
        Long consultorioId = consultorioActual.id();
        Limit tope = Limit.of(PacienteRepository.MAXIMO_RESULTADOS);
        List<Paciente> encontrados;
        if (dni != null && !dni.isBlank()) {
            encontrados = pacientes.findByConsultorioIdAndDni(consultorioId, Dni.normalizar(dni)).stream().toList();
        } else if (q != null && !q.isBlank()) {
            BusquedaPaciente b = BusquedaPaciente.de(q);
            encontrados = pacientes.buscarPorTexto(consultorioId, b.patron(), b.prefijoDni(), tope);
        } else {
            encontrados = pacientes.findByConsultorioIdOrderByApellidoAscNombreAscIdAsc(consultorioId, tope);
        }
        return encontrados.stream().map(PacienteVista::desde).toList();
    }

    /**
     * Contrato para C-07/C-09: devuelve el paciente con ese DNI sin modificar sus datos (RN-PU-04) o lo crea.
     * Se suma a la transacción de quien llama ({@code REQUIRED}) y nunca la deja marcada para rollback por un
     * duplicado: usa {@code INSERT … ON CONFLICT DO NOTHING} y, si otra transacción ganó, relee (design D5).
     */
    @Transactional
    public ResultadoBuscarOCrear buscarOCrearPorDni(DatosPaciente datos) {
        Long consultorioId = consultorioActual.id();
        // Normaliza y valida con las mismas reglas del alta, sin persistir la instancia.
        Paciente candidato = Paciente.nuevo(consultorios.getReferenceById(consultorioId), datos);
        var existente = pacientes.findByConsultorioIdAndDni(consultorioId, candidato.getDni());
        if (existente.isPresent()) {
            return new ResultadoBuscarOCrear(PacienteVista.desde(existente.get()), false);
        }
        List<Long> insertado = pacientes.insertarSiNoExiste(consultorioId, candidato.getNombre(),
                candidato.getApellido(), candidato.getDni(), candidato.getFechaNacimiento(), candidato.getTelefono(),
                candidato.getEmail(), candidato.getDireccion(), clock.instant());
        boolean creado = !insertado.isEmpty();
        Paciente paciente = (creado
                ? pacientes.findByIdAndConsultorioId(insertado.get(0), consultorioId)
                : pacientes.findByConsultorioIdAndDni(consultorioId, candidato.getDni()))
                .orElseThrow(() -> new IllegalStateException("Paciente no visible tras buscarOCrearPorDni"));
        return new ResultadoBuscarOCrear(PacienteVista.desde(paciente), creado);
    }

    private Paciente cargar(Long id) {
        return pacientes.findByIdAndConsultorioId(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADO));
    }

    /** Traduce solo la violación del DNI único a 409; cualquier otra violación se relanza tal cual. */
    private Paciente guardar(Paciente paciente) {
        try {
            return pacientes.saveAndFlush(paciente);
        } catch (DataIntegrityViolationException e) {
            if (esViolacionDeDni(e)) {
                throw new ConflictoException(MENSAJE_DNI_DUPLICADO);
            }
            throw e;
        }
    }

    private static boolean esViolacionDeDni(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && RESTRICCION_DNI.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
