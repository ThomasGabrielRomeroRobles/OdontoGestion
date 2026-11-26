-- V12: historia clínica (C-05, stretch). Era V11 en CHANGES.md, pero V11 la usó C-08 (ausencia_turno).
--  * historia_clinica: una por paciente (RN-HC-01), con consultorio_id (raíz del agregado). Se crea en la primera
--    escritura clínica (RN-PA-05); la aplicación inserta con ON CONFLICT (paciente_id) DO NOTHING.
--  * anamnesis: una por historia; textos simples ('' cuando no hay dato, como paciente.direccion), embarazo
--    nullable. actualizado_por_id + actualizado_en registran autor y fecha del último guardado (RN-HC-05 MVP).
--  * pieza_dental_estado: estado vigente por pieza (una fila por historia + pieza). Solo las 32 piezas
--    permanentes FDI (cuadrante 1-4, pieza 1-8) y los 9 estados del MVP; sin caras (PA-09).
--  * evolucion_clinica: nota con autor (usuario_id), fecha civil de la consulta y turno opcional. turno (V9) ya
--    existe, así que la FK nace acá. editada_en marca la última edición dentro de la ventana (C-05 design D5).
--  * Las tablas hijas cuelgan de historia_clinica; todas las FK son ON DELETE RESTRICT (baja lógica, RN-PA-04).
--  * Datos clínicos: nunca en logs ni URLs (RN-HC-06); esta migración no carga datos.
-- Rollback (solo desarrollo local): DROP TABLE evolucion_clinica, pieza_dental_estado, anamnesis,
--   historia_clinica; y borrar la fila version '12' de flyway_schema_history.
CREATE TABLE historia_clinica (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    paciente_id    bigint      NOT NULL REFERENCES paciente (id) ON DELETE RESTRICT,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_historia_clinica_paciente UNIQUE (paciente_id)
);

CREATE TABLE anamnesis (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    historia_id             bigint      NOT NULL REFERENCES historia_clinica (id) ON DELETE RESTRICT,
    alergias                text        NOT NULL DEFAULT '' CHECK (char_length(alergias) <= 2000),
    medicacion_actual       text        NOT NULL DEFAULT '' CHECK (char_length(medicacion_actual) <= 2000),
    enfermedades_previas    text        NOT NULL DEFAULT '' CHECK (char_length(enfermedades_previas) <= 2000),
    antecedentes_relevantes text        NOT NULL DEFAULT '' CHECK (char_length(antecedentes_relevantes) <= 2000),
    embarazo                boolean     NULL,
    observaciones           text        NOT NULL DEFAULT '' CHECK (char_length(observaciones) <= 2000),
    actualizado_por_id      bigint      NOT NULL REFERENCES usuario (id) ON DELETE RESTRICT,
    creado_en               timestamptz NOT NULL DEFAULT now(),
    actualizado_en          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_anamnesis_historia UNIQUE (historia_id)
);

CREATE TABLE pieza_dental_estado (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    historia_id        bigint      NOT NULL REFERENCES historia_clinica (id) ON DELETE RESTRICT,
    pieza_fdi          smallint    NOT NULL,
    estado             text        NOT NULL,
    observacion        text        NOT NULL DEFAULT '' CHECK (char_length(observacion) <= 500),
    actualizado_por_id bigint      NOT NULL REFERENCES usuario (id) ON DELETE RESTRICT,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_pieza_dental_estado_historia_pieza UNIQUE (historia_id, pieza_fdi),
    CONSTRAINT ck_pieza_dental_estado_fdi_permanente
        CHECK (pieza_fdi / 10 BETWEEN 1 AND 4 AND pieza_fdi % 10 BETWEEN 1 AND 8),
    CONSTRAINT ck_pieza_dental_estado_estado
        CHECK (estado IN ('SANA', 'CARIES', 'OBTURADA', 'CORONA', 'ENDODONCIA', 'AUSENTE', 'IMPLANTE',
                          'FRACTURADA', 'A_EXTRAER'))
);

CREATE TABLE evolucion_clinica (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    historia_id    bigint      NOT NULL REFERENCES historia_clinica (id) ON DELETE RESTRICT,
    usuario_id     bigint      NOT NULL REFERENCES usuario (id) ON DELETE RESTRICT,
    turno_id       bigint      NULL REFERENCES turno (id) ON DELETE RESTRICT,
    fecha          date        NOT NULL,
    nota           text        NOT NULL CHECK (char_length(btrim(nota)) BETWEEN 1 AND 4000),
    editada_en     timestamptz NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

-- Orden cronológico del listado (fecha, alta, id) por historia.
CREATE INDEX ix_evolucion_clinica_historia_orden ON evolucion_clinica (historia_id, fecha, creado_en, id);
CREATE INDEX ix_evolucion_clinica_turno ON evolucion_clinica (turno_id) WHERE turno_id IS NOT NULL;
