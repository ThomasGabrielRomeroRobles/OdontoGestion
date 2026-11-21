package ar.edu.odontogestion.application.consultorio;

/**
 * Proveedor del {@code consultorio_id} único del MVP (DD-01). Todos los servicios y consultas
 * posteriores filtran por este id; las rutas públicas lo necesitan también sin sesión.
 */
public interface ConsultorioActual {

    Long id();
}
