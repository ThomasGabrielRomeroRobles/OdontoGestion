package ar.edu.odontogestion.infrastructure.seed;

import ar.edu.odontogestion.application.consultorio.BootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Dispara el seed inicial al arrancar la aplicación. */
@Component
@Order(BootstrapOrden.CONSULTORIO_Y_ODONTOLOGO)
public class BootstrapSeeder implements ApplicationRunner {

    private final BootstrapService servicio;

    public BootstrapSeeder(BootstrapService servicio) {
        this.servicio = servicio;
    }

    @Override
    public void run(ApplicationArguments args) {
        servicio.ejecutar();
    }
}
