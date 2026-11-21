package ar.edu.odontogestion.application.agenda;

/** Datos de alta o edición de una prestación, tal como llegan de la API (las reglas las valida el servicio). */
public record DatosPrestacion(String nombre, Integer duracionMin) {
}
