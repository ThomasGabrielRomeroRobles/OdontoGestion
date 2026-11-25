package ar.edu.odontogestion.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.demo.DemoSeedService;
import ar.edu.odontogestion.application.demo.ResultadoSeedDemo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** C-12 D3: el seed de demo nunca hace fallar el arranque. */
@ExtendWith(OutputCaptureExtension.class)
class DemoSeederTest {

    @Test
    void delegaEnElServicio() {
        DemoSeedService servicio = mock(DemoSeedService.class);
        when(servicio.sembrar()).thenReturn(ResultadoSeedDemo.NADA);

        new DemoSeeder(servicio).run(new DefaultApplicationArguments());

        verify(servicio).sembrar();
    }

    @Test
    void unErrorDelSeedSeLogueaSoloConSuClaseYNoCortaElArranque(CapturedOutput salida) {
        DemoSeedService servicio = mock(DemoSeedService.class);
        when(servicio.sembrar()).thenThrow(new IllegalStateException("paciente01@demo.example 90000001"));

        assertThatCode(() -> new DemoSeeder(servicio).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(salida.getOut()).contains("IllegalStateException")
                .doesNotContain("demo.example").doesNotContain("90000001");
    }
}
