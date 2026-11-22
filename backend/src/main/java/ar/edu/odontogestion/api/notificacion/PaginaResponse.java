package ar.edu.odontogestion.api.notificacion;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Página de resultados con forma propia, sin exponer el {@code Page} de Spring (design D13). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    static <V, T> PaginaResponse<T> de(Page<V> pagina, Function<V, T> mapeo) {
        return new PaginaResponse<>(pagina.getContent().stream().map(mapeo).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
