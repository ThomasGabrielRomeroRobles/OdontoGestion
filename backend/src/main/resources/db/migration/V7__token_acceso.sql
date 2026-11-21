-- V7: token_acceso (C-03, mitad tokens). Solo se guarda el SHA-256 del token (32 bytes); el valor en claro
-- existe unicamente en el email (RN-TK-02).
--  * turno_id / aviso_id / entrada_lista_espera_id son bigint NULL SIN FK: turno (V9, C-07) y aviso /
--    entrada_lista_espera (C-10) todavia no existen. La migracion que cree cada tabla agrega la FK
--    (design D1 de C-03: token -> ON DELETE CASCADE).
--  * Exactamente una referencia (num_nonnulls = 1) y coherente con el proposito.
-- Rollback (solo desarrollo local): DROP TABLE token_acceso;
--   y borrar la fila version '7' de flyway_schema_history.
CREATE TABLE token_acceso (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id          bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    token_hash              bytea       NOT NULL,
    proposito               text        NOT NULL,
    turno_id                bigint      NULL,
    aviso_id                bigint      NULL,
    entrada_lista_espera_id bigint      NULL,
    vence_en                timestamptz NOT NULL,
    usado_en                timestamptz NULL,
    creado_en               timestamptz NOT NULL DEFAULT now(),
    actualizado_en          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_token_acceso_hash UNIQUE (token_hash),
    CONSTRAINT ck_token_acceso_hash_32 CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_token_acceso_proposito CHECK (proposito IN
        ('CONFIRMAR_SOLICITUD', 'CONFIRMAR_TURNO', 'CANCELAR_TURNO', 'ACEPTAR_AVISO', 'CONFIRMAR_LISTA_ESPERA')),
    CONSTRAINT ck_token_acceso_una_referencia
        CHECK (num_nonnulls(turno_id, aviso_id, entrada_lista_espera_id) = 1),
    CONSTRAINT ck_token_acceso_proposito_referencia CHECK (
        (proposito IN ('CONFIRMAR_SOLICITUD', 'CONFIRMAR_TURNO', 'CANCELAR_TURNO') AND turno_id IS NOT NULL)
        OR (proposito = 'ACEPTAR_AVISO' AND aviso_id IS NOT NULL)
        OR (proposito = 'CONFIRMAR_LISTA_ESPERA' AND entrada_lista_espera_id IS NOT NULL))
);

CREATE INDEX idx_token_acceso_turno ON token_acceso (turno_id) WHERE turno_id IS NOT NULL;
CREATE INDEX idx_token_acceso_aviso ON token_acceso (aviso_id) WHERE aviso_id IS NOT NULL;
CREATE INDEX idx_token_acceso_entrada ON token_acceso (entrada_lista_espera_id)
    WHERE entrada_lista_espera_id IS NOT NULL;
