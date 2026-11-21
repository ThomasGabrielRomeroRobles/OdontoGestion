-- V4: configuración de la agenda (C-06): prestaciones, horarios semanales y bloqueos, con su seed ficticio.
-- Convenciones de V2: identity, creado_en / actualizado_en, FK a consultorio con índice, opcionales como ''
-- y restricciones con nombre (el servicio las identifica por nombre para responder 422 con campo).
-- Rollback (solo desarrollo local): DROP TABLE bloqueo_agenda; DROP TABLE horario_trabajo; DROP TABLE prestacion;
--   y borrar la fila version '4' de flyway_schema_history.

CREATE TABLE prestacion (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    nombre         citext      NOT NULL,
    duracion_min   integer     NOT NULL,
    activa         boolean     NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_prestacion_duracion_positiva CHECK (duracion_min > 0),
    CONSTRAINT ck_prestacion_nombre_no_vacio CHECK (char_length(nombre) BETWEEN 1 AND 80),
    -- citext: "Consulta" y "consulta" son el mismo nombre sin lógica en Java.
    CONSTRAINT uq_prestacion_consultorio_nombre UNIQUE (consultorio_id, nombre)
);

CREATE TABLE horario_trabajo (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    dia_semana     smallint    NOT NULL,
    hora_desde     time        NOT NULL,
    hora_hasta     time        NOT NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_horario_dia_semana CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horario_rango CHECK (hora_desde < hora_hasta),
    -- Sin solapamiento por consultorio y día (regla dura #8: la garantía vive en la base). `time` no tiene tipo
    -- de rango, así que se ancla a una fecha fija (2000-01-01) para formar un tsrange semiabierto [desde, hasta):
    -- 09:00-13:00 y 13:00-14:00 no chocan. btree_gist (V1) permite combinar `=` con `&&` en el mismo índice.
    CONSTRAINT ex_horario_trabajo_sin_solapamiento EXCLUDE USING gist (
        consultorio_id WITH =,
        dia_semana WITH =,
        tsrange('2000-01-01'::date + hora_desde, '2000-01-01'::date + hora_hasta, '[)') WITH &&
    )
);

CREATE TABLE bloqueo_agenda (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    desde          timestamptz NOT NULL,
    hasta          timestamptz NOT NULL,
    motivo         text        NOT NULL DEFAULT '',
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_bloqueo_rango CHECK (desde < hasta),
    CONSTRAINT ck_bloqueo_motivo_largo CHECK (char_length(motivo) <= 200)
);

CREATE INDEX ix_prestacion_consultorio_id ON prestacion (consultorio_id);
CREATE INDEX ix_horario_trabajo_consultorio_id ON horario_trabajo (consultorio_id);
-- Consultas por intersección de rango (GET /bloqueos y, en C-07, disponibilidad). Los bloqueos sí pueden solaparse.
CREATE INDEX ix_bloqueo_agenda_rango ON bloqueo_agenda USING gist (consultorio_id, tstzrange(desde, hasta, '[)'));

-- Seed ficticio (KB 04 §Seed, regla dura #12). El consultorio ya existe porque lo inserta V2; Flyway aplica esta
-- migración una sola vez, así que los arranques posteriores no vuelven a sembrar ni pisan cambios del odontólogo.
INSERT INTO prestacion (consultorio_id, nombre, duracion_min)
SELECT c.id, p.nombre, p.duracion_min
FROM consultorio c
CROSS JOIN (VALUES ('Consulta', 30), ('Limpieza', 45), ('Obturación', 60), ('Endodoncia', 90),
                   ('Extracción', 45), ('Control', 15)) AS p (nombre, duracion_min);

INSERT INTO horario_trabajo (consultorio_id, dia_semana, hora_desde, hora_hasta)
SELECT c.id, d.dia, f.desde, f.hasta
FROM consultorio c
CROSS JOIN generate_series(1, 5) AS d (dia)
CROSS JOIN (VALUES (time '09:00', time '13:00'), (time '14:00', time '18:00')) AS f (desde, hasta);
