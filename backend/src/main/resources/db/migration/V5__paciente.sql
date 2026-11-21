-- V5: paciente (C-04). Pacientes sin cuenta, identificados por DNI único por consultorio (RN-PA-01).
--  * dni se guarda normalizado (solo dígitos, 7 u 8); la aplicación normaliza antes de guardar y de buscar.
--  * email y telefono obligatorios para poder notificar (RN-PA-03); email citext para comparar sin mayúsculas.
--  * fecha_nacimiento es una fecha civil (date), no un instante; direccion opcional se guarda como '' (como en V2).
--  * cantidad_ausencias la mantiene C-08 (RN-AS-02); activo es la baja lógica (RN-PA-04).
--  * El índice único (consultorio_id, dni) cubre las búsquedas por DNI y por prefijo de DNI; consultorio_id
--    queda indexado como primera columna de los tres índices.
-- Rollback (solo desarrollo local): DROP TABLE paciente;
--   y borrar la fila version '5' de flyway_schema_history.
CREATE TABLE paciente (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id     bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    nombre             text        NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
    apellido           text        NOT NULL CHECK (char_length(apellido) BETWEEN 1 AND 80),
    dni                text        NOT NULL CHECK (dni ~ '^[0-9]{7,8}$'),
    fecha_nacimiento   date        NULL,
    telefono           text        NOT NULL CHECK (char_length(telefono) BETWEEN 1 AND 40),
    email              citext      NOT NULL CHECK (char_length(email) BETWEEN 3 AND 254),
    direccion          text        NOT NULL DEFAULT '',
    cantidad_ausencias integer     NOT NULL DEFAULT 0 CHECK (cantidad_ausencias >= 0),
    activo             boolean     NOT NULL DEFAULT true,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_paciente_consultorio_dni UNIQUE (consultorio_id, dni)
);

CREATE INDEX ix_paciente_consultorio_email ON paciente (consultorio_id, email);
CREATE INDEX ix_paciente_consultorio_apellido_nombre ON paciente (consultorio_id, apellido, nombre);
