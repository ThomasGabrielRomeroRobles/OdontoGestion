package ar.edu.odontogestion.api.error;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.domain.error.DemasiadosIntentosException;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.historia.NoEsAutorException;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.TransicionInvalidaException;
import java.time.Instant;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlador solo de pruebas: ejercita cada ruta de error. No existe en el artefacto de producción. */
@RestController
@RequestMapping("/api/v1/recurso-de-prueba")
class ErrorTestController {

    record CrearRequest(@NotBlank String nombre, @Email String email) {
    }

    @PostMapping
    String crear(@Valid @RequestBody CrearRequest request) {
        return request.nombre();
    }

    @PostMapping("/explota-con-cuerpo")
    String explotaConCuerpo(@RequestBody String cuerpo) {
        throw new IllegalStateException("fallo-interno");
    }

    @GetMapping("/{id}/explota")
    String explota(@PathVariable long id) {
        throw new IllegalStateException("secreto-interno");
    }

    @GetMapping("/{id}/no-encontrado")
    String noEncontrado(@PathVariable long id) {
        throw new RecursoNoEncontradoException("Paciente " + id + " no existe");
    }

    @GetMapping("/{id}/conflicto")
    String conflicto(@PathVariable long id) {
        throw new ConflictoException("El hueco ya fue ocupado");
    }

    @GetMapping("/{id}/prohibido")
    void prohibido(@PathVariable Long id) {
        throw new NoEsAutorException();
    }

    @GetMapping("/{id}/regla")
    String regla(@PathVariable long id) {
        throw new ReglaNegocioException("La duración debe ser múltiplo de 15 minutos");
    }

    @GetMapping("/{id}/validacion-dominio")
    String validacionDominio(@PathVariable long id) {
        throw new ValidacionDominioException(List.of(
                new ErrorCampo("granularidadMin", "Debe ser mayor que cero"),
                new ErrorCampo("recordatorioHorasAntes", "Debe ser mayor que cero")));
    }

    @GetMapping("/{id}/demasiados-intentos")
    String demasiadosIntentos(@PathVariable long id) {
        throw new DemasiadosIntentosException(59.2);
    }

    /** Exceso de solicitudes públicas (C-09): la causa (IP, DNI, email, pendientes) no cambia el cuerpo. */
    @GetMapping("/{id}/demasiadas-solicitudes")
    String demasiadasSolicitudes(@PathVariable long id, @RequestParam(defaultValue = "59.2") double segundos) {
        throw new DemasiadasSolicitudesException(segundos);
    }

    /** Simula un caso de uso que informa un token inválido; la causa nunca llega al cliente. */
    @GetMapping("/token/{valor}")
    String tokenInvalido(@PathVariable String valor, @RequestParam(defaultValue = "inexistente") String causa) {
        throw new TokenInvalidoException();
    }

    /** Conflicto de agenda con 0 a 2 alternativas (C-07 D6). */
    @GetMapping("/{id}/hueco-no-disponible")
    String huecoNoDisponible(@PathVariable long id, @RequestParam(defaultValue = "2") int alternativas) {
        List<Hueco> todas = List.of(
                new Hueco(Instant.parse("2026-11-23T13:30:00Z"), Instant.parse("2026-11-23T14:00:00Z")),
                new Hueco(Instant.parse("2026-11-23T13:45:00Z"), Instant.parse("2026-11-23T14:15:00Z")));
        throw new HuecoNoDisponibleException(todas.subList(0, alternativas));
    }

    @GetMapping("/{id}/transicion-invalida")
    String transicionInvalida(@PathVariable long id) {
        throw new TransicionInvalidaException(EstadoTurno.CANCELADO, EstadoTurno.ATENDIDO);
    }

    /** El mensaje de la excepción de Spring nombra la clase de la entidad: no debe llegar al cliente. */
    @GetMapping("/{id}/modificacion-concurrente")
    String modificacionConcurrente(@PathVariable long id) {
        throw new ObjectOptimisticLockingFailureException("ar.edu.odontogestion.domain.turno.Turno", id);
    }
}
