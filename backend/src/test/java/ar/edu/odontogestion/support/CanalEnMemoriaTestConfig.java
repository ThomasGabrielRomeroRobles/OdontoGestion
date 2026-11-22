package ar.edu.odontogestion.support;

import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Canal de notificaciones falso para todas las pruebas de integración (importado por
 * {@link AbstractIntegrationTest}): ningún test envía emails reales. Los tests que lo usan deben llamar a
 * {@link CanalEnMemoria#reiniciar()} al terminar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CanalEnMemoriaTestConfig {

    @Bean
    @Primary
    public CanalEnMemoria canalEnMemoria() {
        return new CanalEnMemoria();
    }
}
