-- V6: notificacion (C-03, mitad notificaciones). Outbox de envios a pacientes (DD-07, DD-13, RN-NO).
--  * turno_id / aviso_id / entrada_lista_espera_id son bigint NULL SIN FK: turno (V9, C-07) y aviso /
--    entrada_lista_espera (C-10) todavia no existen. La migracion que cree cada tabla agrega la FK
--    (design D1 de C-03: notificacion -> ON DELETE RESTRICT, el registro de envios no se pierde).
--  * Exactamente una referencia (num_nonnulls = 1) y coherente con el tipo.
--  * datos: variables de plantilla (sin datos clinicos). enlaces: [{proposito, venceEn}]; el token en claro
--    NUNCA se guarda aca, se emite al enviar (design D8, RN-TK-02).
--  * Idempotencia con indices unicos parciales (design D7); CONFIRMACION_TURNO puede repetirse (RN-NO-07).
-- Rollback (solo desarrollo local): DROP TABLE notificacion;
--   y borrar la fila version '6' de flyway_schema_history.
CREATE TABLE notificacion (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id          bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    tipo                    text        NOT NULL,
    canal                   text        NOT NULL DEFAULT 'EMAIL',
    destinatario            citext      NOT NULL,
    turno_id                bigint      NULL,
    aviso_id                bigint      NULL,
    entrada_lista_espera_id bigint      NULL,
    datos                   jsonb       NOT NULL DEFAULT '{}'::jsonb,
    enlaces                 jsonb       NOT NULL DEFAULT '[]'::jsonb,
    estado                  text        NOT NULL DEFAULT 'PENDIENTE',
    intentos                integer     NOT NULL DEFAULT 0,
    reintentos_manuales     integer     NOT NULL DEFAULT 0,
    error                   text        NULL,
    programada_para         timestamptz NOT NULL,
    enviada_en              timestamptz NULL,
    creado_en               timestamptz NOT NULL DEFAULT now(),
    actualizado_en          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_notificacion_tipo CHECK (tipo IN
        ('CONFIRMAR_SOLICITUD', 'CONFIRMAR_LISTA_ESPERA', 'CONFIRMACION_TURNO', 'RECORDATORIO',
         'AVISO_HUECO', 'CANCELACION', 'AUSENTE_REPROGRAMAR')),
    CONSTRAINT ck_notificacion_canal CHECK (canal IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT ck_notificacion_estado CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    CONSTRAINT ck_notificacion_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_notificacion_reintentos_manuales CHECK (reintentos_manuales >= 0),
    CONSTRAINT ck_notificacion_error_largo CHECK (error IS NULL OR char_length(error) <= 500),
    CONSTRAINT ck_notificacion_datos_objeto CHECK (jsonb_typeof(datos) = 'object'),
    CONSTRAINT ck_notificacion_enlaces_lista CHECK (jsonb_typeof(enlaces) = 'array'),
    CONSTRAINT ck_notificacion_una_referencia
        CHECK (num_nonnulls(turno_id, aviso_id, entrada_lista_espera_id) = 1),
    CONSTRAINT ck_notificacion_tipo_referencia CHECK (
        (tipo = 'AVISO_HUECO' AND aviso_id IS NOT NULL)
        OR (tipo = 'CONFIRMAR_LISTA_ESPERA' AND entrada_lista_espera_id IS NOT NULL)
        OR (tipo NOT IN ('AVISO_HUECO', 'CONFIRMAR_LISTA_ESPERA') AND turno_id IS NOT NULL))
);

-- Procesador del outbox: pendientes cuyo momento llego (design D10).
CREATE INDEX idx_notificacion_estado_programada ON notificacion (estado, programada_para);
-- Registro de envios: listado de la mas reciente a la mas antigua (design D13).
CREATE INDEX idx_notificacion_creado_en ON notificacion (creado_en DESC);

-- Idempotencia (design D7). Tambien sirven de indice por referencia.
CREATE UNIQUE INDEX uq_notificacion_turno_tipo ON notificacion (turno_id, tipo)
    WHERE turno_id IS NOT NULL AND tipo <> 'CONFIRMACION_TURNO';
CREATE UNIQUE INDEX uq_notificacion_aviso ON notificacion (aviso_id) WHERE aviso_id IS NOT NULL;
CREATE UNIQUE INDEX uq_notificacion_entrada_tipo ON notificacion (entrada_lista_espera_id, tipo)
    WHERE entrada_lista_espera_id IS NOT NULL;
-- CONFIRMACION_TURNO queda fuera del unico: indice simple para buscar por turno.
CREATE INDEX idx_notificacion_turno ON notificacion (turno_id) WHERE turno_id IS NOT NULL;
