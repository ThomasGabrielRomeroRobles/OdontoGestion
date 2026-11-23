package ar.edu.odontogestion.api.listaespera;

import ar.edu.odontogestion.application.listaespera.AvisoAceptadoResponse;
import ar.edu.odontogestion.application.listaespera.AvisoComandos;
import ar.edu.odontogestion.application.listaespera.ResumenAvisoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos de hueco liberado por enlace de email (C-10 D5): sin sesión (SecurityConfig), el {@code POST} exige CSRF. El
 * {@code GET} muestra el hueco sin consumir el token; el {@code POST} lo acepta (el primero gana). El valor del
 * token viaja solo en la ruta: no se loguea ni se devuelve en ningún cuerpo de error.
 */
@RestController
@RequestMapping("/api/v1/avisos")
public class AvisoController {

    private final AvisoComandos comandos;

    public AvisoController(AvisoComandos comandos) {
        this.comandos = comandos;
    }

    @GetMapping("/{token}")
    public ResumenAvisoResponse resumen(@PathVariable String token) {
        return comandos.resumen(token);
    }

    @PostMapping("/{token}/aceptar")
    public AvisoAceptadoResponse aceptar(@PathVariable String token) {
        return comandos.aceptar(token);
    }
}
