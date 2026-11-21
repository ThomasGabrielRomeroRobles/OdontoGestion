-- V2: consultorio y usuario (C-02). Convención de timestamps del proyecto: creado_en / actualizado_en.
--  * Los opcionales se guardan como '' (no NULL) para evitar tres estados.
--  * No hay restricción de fila única en consultorio (KB 04); la unicidad la resuelve ConsultorioActual.
-- Rollback (solo desarrollo local): DROP TABLE usuario; DROP TABLE consultorio;
--   y borrar la fila version '2' de flyway_schema_history.
CREATE TABLE consultorio (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         text        NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 120),
    direccion      text        NOT NULL DEFAULT '',
    telefono       text        NOT NULL DEFAULT '',
    email_contacto citext      NOT NULL DEFAULT '',
    zona_horaria   text        NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    email          citext      NOT NULL CONSTRAINT uq_usuario_email UNIQUE,
    password_hash  text        NOT NULL,
    nombre         text        NOT NULL,
    apellido       text        NOT NULL,
    matricula      text        NOT NULL DEFAULT '',
    activo         boolean     NOT NULL DEFAULT true,
    ultimo_acceso  timestamptz NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_usuario_consultorio_id ON usuario (consultorio_id);

-- Consultorio único del MVP (datos ficticios, DD-11 / regla 12). Va en la migración y no en el seed Java porque las
-- migraciones siguientes (C-06, C-04, ...) necesitan un consultorio_id existente antes de que corra cualquier runner.
INSERT INTO consultorio (nombre, direccion, telefono, email_contacto)
VALUES ('Consultorio Demo', 'Calle Ficticia 123, CABA', '+54 11 0000-0000', 'contacto@consultorio.example');
