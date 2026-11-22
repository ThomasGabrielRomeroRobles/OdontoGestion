-- V9: turno (C-07). Agenda del odontologo: estados, no superposicion garantizada por la base y FKs diferidas de C-03.
--  * La no superposicion es la restriccion EXCLUDE ex_turno_sin_superposicion (regla dura #8): nunca solo un
--    chequeo en Java. Resolucion de PA-06 (design D2, aprobada 2026-11-22): ocupan agenda TODOS los estados salvo
--    CANCELADO y EXPIRADO; ATENDIDO y AUSENTE siguen ocupando su rango. EstadoTurno.ocupaAgenda() replica el
--    predicado en Java solo para la disponibilidad orientativa; un test compara ambos.
--  * Intervalo semiabierto [inicio, fin): turnos contiguos no chocan. btree_gist lo habilita V1.
--  * estado / origen / cancelado_por: text + CHECK con nombre (igual que V4, V6 y V7), mapeados con @Enumerated(STRING).
--  * version (@Version) detecta la modificacion concurrente del mismo turno (design D9).
--  * FKs diferidas de C-03 (design D3): notificacion.turno_id -> RESTRICT (el registro de envios no se pierde),
--    token_acceso.turno_id -> CASCADE. Sin indices nuevos: C-03 ya creo idx_notificacion_turno / idx_token_acceso_turno.
--  * evolucion_clinica.turno_id -> turno NO se agrega aca: la FK la suma C-05 en su propia migracion (V10 o posterior).
-- Rollback (solo desarrollo local): ALTER TABLE token_acceso DROP CONSTRAINT fk_token_acceso_turno;
--   ALTER TABLE notificacion DROP CONSTRAINT fk_notificacion_turno; DROP TABLE turno;
--   y borrar la fila version '9' de flyway_schema_history.
CREATE TABLE turno (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id     bigint       NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    paciente_id        bigint       NOT NULL REFERENCES paciente (id) ON DELETE RESTRICT,
    prestacion_id      bigint       NOT NULL REFERENCES prestacion (id) ON DELETE RESTRICT,
    inicio             timestamptz  NOT NULL,
    fin                timestamptz  NOT NULL,
    estado             text         NOT NULL,
    origen             text         NOT NULL,
    pendiente_hasta    timestamptz  NULL,
    motivo_cancelacion varchar(200) NULL,
    cancelado_por      text         NULL,
    version            bigint       NOT NULL DEFAULT 0,
    creado_en          timestamptz  NOT NULL DEFAULT now(),
    actualizado_en     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_turno_estado CHECK (estado IN
        ('PENDIENTE_CONFIRMACION', 'RESERVADO', 'CONFIRMADO', 'CANCELADO', 'AUSENTE', 'ATENDIDO', 'EXPIRADO')),
    CONSTRAINT ck_turno_origen CHECK (origen IN ('PUBLICA', 'ODONTOLOGO', 'LISTA_ESPERA')),
    CONSTRAINT ck_turno_cancelado_por CHECK (cancelado_por IN ('ODONTOLOGO', 'PACIENTE', 'SISTEMA')),
    CONSTRAINT ck_turno_motivo_largo CHECK (char_length(motivo_cancelacion) <= 200),
    CONSTRAINT ck_turno_rango CHECK (inicio < fin),
    CONSTRAINT ck_turno_pendiente_con_vencimiento
        CHECK (estado <> 'PENDIENTE_CONFIRMACION' OR pendiente_hasta IS NOT NULL),
    CONSTRAINT ex_turno_sin_superposicion EXCLUDE USING gist (
        consultorio_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado NOT IN ('CANCELADO', 'EXPIRADO'))
);

CREATE INDEX ix_turno_consultorio_inicio ON turno (consultorio_id, inicio);
CREATE INDEX ix_turno_paciente_inicio ON turno (paciente_id, inicio);
CREATE INDEX ix_turno_estado_inicio ON turno (estado, inicio);
CREATE INDEX ix_turno_prestacion_id ON turno (prestacion_id);

ALTER TABLE notificacion
    ADD CONSTRAINT fk_notificacion_turno FOREIGN KEY (turno_id) REFERENCES turno (id) ON DELETE RESTRICT;
ALTER TABLE token_acceso
    ADD CONSTRAINT fk_token_acceso_turno FOREIGN KEY (turno_id) REFERENCES turno (id) ON DELETE CASCADE;
