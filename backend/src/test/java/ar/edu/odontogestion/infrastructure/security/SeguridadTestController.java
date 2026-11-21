package ar.edu.odontogestion.infrastructure.security;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador solo de pruebas: rutas privadas (/api/**) y públicas (publico, acciones, avisos).
 * Las rutas llevan "prueba" para no chocar con los controllers reales de otros changes.
 */
@RestController
@RequestMapping("/api/v1")
public class SeguridadTestController {

    public static final AtomicInteger INVOCACIONES = new AtomicInteger();

    @GetMapping("/seguridad-prueba/privado")
    String leerPrivado() {
        INVOCACIONES.incrementAndGet();
        return "ok";
    }

    @PutMapping("/seguridad-prueba/privado")
    String escribirPrivado() {
        INVOCACIONES.incrementAndGet();
        return "ok";
    }

    @PostMapping("/seguridad-prueba/privado")
    String crearPrivado() {
        INVOCACIONES.incrementAndGet();
        return "ok";
    }

    @GetMapping("/publico/prueba")
    String publico() {
        return "ok";
    }

    @GetMapping("/acciones/prueba")
    String acciones() {
        return "ok";
    }

    @GetMapping("/avisos/prueba")
    String avisos() {
        return "ok";
    }
}
