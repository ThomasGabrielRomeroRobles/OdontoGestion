package ar.edu.odontogestion.api.error;

import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{id}/regla")
    String regla(@PathVariable long id) {
        throw new ReglaNegocioException("La duración debe ser múltiplo de 15 minutos");
    }
}
