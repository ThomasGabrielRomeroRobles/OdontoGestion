package ar.edu.odontogestion.api.listaespera;

import ar.edu.odontogestion.application.listaespera.ListaEsperaService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lista de espera del odontólogo (C-10 D7): requiere sesión (SecurityConfig); las escrituras exigen CSRF. */
@RestController
@RequestMapping("/api/v1/lista-espera")
public class ListaEsperaController {

    private final ListaEsperaService servicio;

    public ListaEsperaController(ListaEsperaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<EntradaListaEsperaResponse> listar() {
        return servicio.listar().stream().map(EntradaListaEsperaResponse::de).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@PathVariable Long id) {
        servicio.cancelar(id);
    }
}
