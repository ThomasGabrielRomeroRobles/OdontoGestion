-- V11: datos de la marca de ausente en turno (C-08, backend de ausentismo; design D1).
--  * ausente_marcado_en: instante de la marca; mide la ventana de 24 h para revertirla (RN-AS-06).
--  * estado_previo_ausencia: estado al que vuelve el turno si se revierte la marca (RESERVADO o CONFIRMADO).
--  * ck_turno_ausencia: un turno AUSENTE tiene siempre los dos datos y ningun otro estado los tiene, asi la
--    reversion nunca queda sin estado previo ni quedan datos colgando tras revertir.
--  * No hay datos que migrar: ningun turno estaba AUSENTE antes de C-08 (no habia caso de uso ni seed).
-- Rollback (solo desarrollo local): ALTER TABLE turno DROP CONSTRAINT ck_turno_ausencia,
--   DROP CONSTRAINT ck_turno_estado_previo_ausencia, DROP COLUMN ausente_marcado_en,
--   DROP COLUMN estado_previo_ausencia;
--   y borrar la fila version '11' de flyway_schema_history.
ALTER TABLE turno
    ADD COLUMN ausente_marcado_en     timestamptz NULL,
    ADD COLUMN estado_previo_ausencia text        NULL,
    ADD CONSTRAINT ck_turno_estado_previo_ausencia
        CHECK (estado_previo_ausencia IN ('RESERVADO', 'CONFIRMADO')),
    ADD CONSTRAINT ck_turno_ausencia
        CHECK ((estado = 'AUSENTE') = (ausente_marcado_en IS NOT NULL AND estado_previo_ausencia IS NOT NULL)
               AND (estado = 'AUSENTE' OR (ausente_marcado_en IS NULL AND estado_previo_ausencia IS NULL)));
