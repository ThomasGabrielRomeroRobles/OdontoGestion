package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Rango de fechas locales {@code [desde, hasta]} (ambas inclusive) de una consulta: invertido o > 31 días → 422. */
final class RangoConsulta {

    static final int MAX_DIAS = 31;

    private RangoConsulta() {
    }

    static void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ValidacionDominioException(List.of(new ErrorCampo(desde == null ? "desde" : "hasta",
                    "Es obligatorio.")));
        }
        if (hasta.isBefore(desde)) {
            throw new ValidacionDominioException("hasta", "Debe ser igual o posterior a desde.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
            throw new ValidacionDominioException("hasta", "El rango no puede superar los " + MAX_DIAS + " días.");
        }
    }
}
