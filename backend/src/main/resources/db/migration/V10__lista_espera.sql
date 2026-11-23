-- V10: lista de espera (C-10). Entradas del paciente y avisos de hueco liberado.
--  * entrada_lista_espera: un paciente se anota por prestacion y rango de fechas (RN-LE). estado/origen: text + CHECK
--    con nombre (igual que V4, V6, V7 y V9), mapeados con @Enumerated(STRING). Un paciente no puede tener dos entradas
--    vigentes (PENDIENTE_CONFIRMACION o ACTIVA) de la misma prestacion: lo decide el unico parcial
--    uq_entrada_lista_espera_vigente, nunca solo un chequeo en Java (regla dura #8).
--  * aviso_hueco: un aviso por entrada compatible con un hueco (turno_origen_id + inicio). El unico
--    (turno_origen_id, inicio, entrada_lista_espera_id) hace idempotente la generacion. "El primero gana" lo
--    garantizan el bloqueo FOR UPDATE de los avisos del hueco y ex_turno_sin_superposicion (V9).
--  * version (@Version) detecta modificaciones concurrentes de la misma fila.
--  * FKs diferidas de C-03 (V6/V7): token_acceso -> CASCADE, notificacion -> RESTRICT (el registro de envios no se
--    pierde). Las columnas hoy son siempre nulas para estos propositos, asi que no hay datos que violen las FK.
-- Rollback (solo desarrollo local): ALTER TABLE token_acceso DROP CONSTRAINT fk_token_acceso_aviso,
--   DROP CONSTRAINT fk_token_acceso_entrada; ALTER TABLE notificacion DROP CONSTRAINT fk_notificacion_aviso,
--   DROP CONSTRAINT fk_notificacion_entrada; DROP TABLE aviso_hueco, entrada_lista_espera;
--   y borrar la fila version '10' de flyway_schema_history.
CREATE TABLE entrada_lista_espera (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id     bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    paciente_id        bigint      NOT NULL REFERENCES paciente (id) ON DELETE RESTRICT,
    prestacion_id      bigint      NOT NULL REFERENCES prestacion (id) ON DELETE RESTRICT,
    fecha_desde        date        NOT NULL,
    fecha_hasta        date        NOT NULL,
    estado             text        NOT NULL,
    origen             text        NOT NULL,
    pendiente_hasta    timestamptz NULL,
    turno_resultante_id bigint     NULL REFERENCES turno (id) ON DELETE RESTRICT,
    version            bigint      NOT NULL DEFAULT 0,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_entrada_estado CHECK (estado IN
        ('PENDIENTE_CONFIRMACION', 'ACTIVA', 'CUBIERTA', 'CANCELADA', 'VENCIDA')),
    CONSTRAINT ck_entrada_origen CHECK (origen IN ('PUBLICA', 'ODONTOLOGO')),
    CONSTRAINT ck_entrada_rango CHECK (fecha_desde <= fecha_hasta),
    CONSTRAINT ck_entrada_pendiente_con_vencimiento
        CHECK (estado <> 'PENDIENTE_CONFIRMACION' OR pendiente_hasta IS NOT NULL)
);

CREATE UNIQUE INDEX uq_entrada_lista_espera_vigente ON entrada_lista_espera (paciente_id, prestacion_id)
    WHERE estado IN ('PENDIENTE_CONFIRMACION', 'ACTIVA');
CREATE INDEX ix_entrada_consultorio_prestacion_estado ON entrada_lista_espera (consultorio_id, prestacion_id, estado);
CREATE INDEX ix_entrada_paciente ON entrada_lista_espera (paciente_id);

CREATE TABLE aviso_hueco (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id          bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    entrada_lista_espera_id bigint      NOT NULL REFERENCES entrada_lista_espera (id) ON DELETE RESTRICT,
    turno_origen_id         bigint      NOT NULL REFERENCES turno (id) ON DELETE RESTRICT,
    prestacion_id           bigint      NOT NULL REFERENCES prestacion (id) ON DELETE RESTRICT,
    inicio                  timestamptz NOT NULL,
    fin                     timestamptz NOT NULL,
    estado                  text        NOT NULL,
    vence_en                timestamptz NOT NULL,
    version                 bigint      NOT NULL DEFAULT 0,
    creado_en               timestamptz NOT NULL DEFAULT now(),
    actualizado_en          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_aviso_estado CHECK (estado IN ('ENVIADO', 'ACEPTADO', 'EXPIRADO', 'CERRADO')),
    CONSTRAINT ck_aviso_rango CHECK (inicio < fin),
    CONSTRAINT uq_aviso_hueco_entrada UNIQUE (turno_origen_id, inicio, entrada_lista_espera_id)
);

CREATE INDEX ix_aviso_hueco ON aviso_hueco (turno_origen_id, inicio);
CREATE INDEX ix_aviso_entrada ON aviso_hueco (entrada_lista_espera_id);
CREATE INDEX ix_aviso_vencimiento ON aviso_hueco (vence_en) WHERE estado = 'ENVIADO';

ALTER TABLE token_acceso
    ADD CONSTRAINT fk_token_acceso_aviso FOREIGN KEY (aviso_id) REFERENCES aviso_hueco (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_token_acceso_entrada FOREIGN KEY (entrada_lista_espera_id)
        REFERENCES entrada_lista_espera (id) ON DELETE CASCADE;
ALTER TABLE notificacion
    ADD CONSTRAINT fk_notificacion_aviso FOREIGN KEY (aviso_id) REFERENCES aviso_hueco (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_notificacion_entrada FOREIGN KEY (entrada_lista_espera_id)
        REFERENCES entrada_lista_espera (id) ON DELETE RESTRICT;
