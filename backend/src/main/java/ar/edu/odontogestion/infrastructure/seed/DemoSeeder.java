package ar.edu.odontogestion.infrastructure.seed;

import ar.edu.odontogestion.application.demo.DemoSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra los datos ficticios de la demo al arrancar (C-12 D2). Solo existe con {@code app.seed-demo.enabled=true}
 * (lo activa el perfil {@code demo}) y corre después del consultorio y el odontólogo ({@code @Order(100)}) y del
 * catálogo de C-06 ({@code 200}). El seed nunca hace fallar el arranque: un error se loguea solo con su clase.
 */
@Component
@Order(BootstrapOrden.DEMO)
@ConditionalOnProperty(name = "app.seed-demo.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final DemoSeedService servicio;

    public DemoSeeder(DemoSeedService servicio) {
        this.servicio = servicio;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            servicio.sembrar();
        } catch (RuntimeException e) {
            log.warn("Seed de demo: no se pudo sembrar ({}); la aplicación sigue sin datos de ejemplo",
                    e.getClass().getSimpleName());
        }
    }
}
