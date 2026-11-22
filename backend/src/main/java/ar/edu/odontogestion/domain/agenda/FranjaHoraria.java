package ar.edu.odontogestion.domain.agenda;

import java.time.LocalTime;

/** Horario de trabajo en hora local del consultorio: {@code diaSemana} ISO (1 = lunes … 7 = domingo). */
public record FranjaHoraria(int diaSemana, LocalTime desde, LocalTime hasta) {
}
