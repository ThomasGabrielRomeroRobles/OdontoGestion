package ar.edu.odontogestion.infrastructure.consultorio;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Resuelve de forma perezosa el único consultorio existente (el seed corre después del arranque del
 * contexto, por eso no se resuelve en {@code @PostConstruct}) y cachea el id.
 */
@Component
public class ConsultorioActualUnico implements ConsultorioActual {

    private final ConsultorioRepository consultorios;

    private volatile Long idCacheado;

    public ConsultorioActualUnico(ConsultorioRepository consultorios) {
        this.consultorios = consultorios;
    }

    @Override
    public Long id() {
        Long cacheado = idCacheado;
        if (cacheado != null) {
            return cacheado;
        }
        List<Long> ids = consultorios.idsOrdenados(Pageable.ofSize(2));
        if (ids.isEmpty()) {
            throw new IllegalStateException("No hay consultorio configurado");
        }
        if (ids.size() > 1) {
            throw new IllegalStateException("Se esperaba un único consultorio");
        }
        idCacheado = ids.get(0);
        return idCacheado;
    }
}
