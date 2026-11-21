-- V3: parámetros de configuración del consultorio (C-02). Cada columna es entera, positiva y tiene un
-- DEFAULT compatible con filas existentes (database-migrations: columnas nuevas con valor por defecto).
-- Rollback (solo desarrollo local): ALTER TABLE consultorio DROP COLUMN granularidad_min,
--   DROP COLUMN anticipacion_min_reserva_min, DROP COLUMN limite_cancelacion_horas,
--   DROP COLUMN recordatorio_horas_antes, DROP COLUMN vencimiento_aviso_min,
--   DROP COLUMN vencimiento_pendiente_min, DROP COLUMN tolerancia_ausente_min;
--   y borrar la fila version '3' de flyway_schema_history.
ALTER TABLE consultorio
    ADD COLUMN granularidad_min              integer NOT NULL DEFAULT 15  CHECK (granularidad_min > 0),
    ADD COLUMN anticipacion_min_reserva_min  integer NOT NULL DEFAULT 120 CHECK (anticipacion_min_reserva_min > 0),
    ADD COLUMN limite_cancelacion_horas      integer NOT NULL DEFAULT 2   CHECK (limite_cancelacion_horas > 0),
    ADD COLUMN recordatorio_horas_antes      integer NOT NULL DEFAULT 24  CHECK (recordatorio_horas_antes > 0),
    ADD COLUMN vencimiento_aviso_min         integer NOT NULL DEFAULT 60  CHECK (vencimiento_aviso_min > 0),
    ADD COLUMN vencimiento_pendiente_min     integer NOT NULL DEFAULT 30  CHECK (vencimiento_pendiente_min > 0),
    ADD COLUMN tolerancia_ausente_min        integer NOT NULL DEFAULT 15  CHECK (tolerancia_ausente_min > 0);
