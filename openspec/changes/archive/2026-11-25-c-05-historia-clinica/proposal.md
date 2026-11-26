# Proposal

## Why

El odontólogo hoy gestiona turnos, pacientes y ausencias, pero no tiene dónde registrar la información clínica de cada paciente (alergias, medicación, estado de las piezas, tratamientos realizados): la atiende "a ciegas" o en papel. C-05 cubre la Épica 8 (US-070, US-071, US-072) con una historia clínica **simple** (DD-09): una por paciente (RN-HC-01), accesible solo con la sesión del odontólogo (RN-HC-02) y con autor y fecha en cada modificación (RN-HC-03/04/05). Es un change **stretch** (DD-16): todos los changes obligatorios ya están en `main` (C-12 archivado el 2026-11-25), así que ahora puede avanzar sin poner en riesgo nada.

## What Changes

- **Migración Flyway `V12__historia_clinica.sql`** (no V11: `V11__ausencia_turno.sql` ya es de C-08): tablas `historia_clinica` (única por paciente, con `consultorio_id`), `anamnesis` (única por historia), `pieza_dental_estado` (única por `historia_id + pieza_fdi`, `CHECK` contra las 32 piezas permanentes FDI y el enum de estados) y `evolucion_clinica`. Como `turno` (V9) ya existe, `evolucion_clinica.turno_id` nace **con FK** a `turno` (nullable).
- **API REST privada** bajo `/api/v1/pacientes/{id}/historia` (sesión + CSRF en escrituras, `ProblemDetail` en errores):
  - `GET /historia`: ficha completa (anamnesis, 32 piezas, evoluciones en orden cronológico). Si el paciente aún no tiene historia devuelve una ficha vacía **sin crearla** (creación perezosa en la primera escritura, RN-PA-05).
  - `PUT /historia/anamnesis`: crea o reemplaza la anamnesis; registra autor y fecha.
  - `PUT /historia/odontograma/piezas/{fdi}`: estado + observación de una pieza; FDI fuera de las 32 permanentes → `422`; registra autor y fecha.
  - `POST /historia/evoluciones` y `GET /historia/evoluciones`: alta y listado cronológico; turno asociado opcional (debe ser del mismo paciente, si no `422`).
  - `PUT /historia/evoluciones/{evolucionId}`: edición solo por el autor (`403`) y dentro de una ventana de 24 h desde el alta (`422`).
- **Frontend `historia-clinica`** (TypeScript vanilla): ruta privada `/pacientes/:id/historia` con anamnesis editable, odontograma FDI visual (32 piezas en 4 cuadrantes, editor de estado/observación) y listado + alta de evoluciones (edición si la API la habilita). Mocks de desarrollo en `src/dev/`.
- **Acceso desde la agenda**: el detalle del turno agrega el enlace "Historia clínica" del paciente. Como hoy `/pacientes` es solo un placeholder (no hay ficha de paciente en el frontend), este es el punto de acceso real; C-12 lo dejaba condicionado a C-05.
- **Privacidad**: ningún dato clínico en logs, URLs, emails ni en el `detail` de los errores (RN-HC-06, regla dura 10). Solo datos ficticios (RN-GL-01).

## Capabilities

### New Capabilities
- `historia-clinica`: historia clínica única por paciente con anamnesis, odontograma FDI por pieza y notas de evolución; acceso exclusivo del odontólogo, autoría y fecha de cada modificación, ventana de edición de evoluciones y privacidad de los datos clínicos.
- `web-historia-clinica`: pantalla del odontólogo para ver y editar la historia clínica de un paciente (anamnesis, odontograma visual y evoluciones).

### Modified Capabilities
- `web-agenda`: el detalle del turno ofrece el acceso a la historia clínica del paciente (requisito nuevo; no cambia los existentes).

## Impact

- **Backend**: nuevos paquetes `domain/historia`, `application/historia`, `api/historia`; nueva migración V12; nuevas excepciones de dominio mapeadas por el `GlobalExceptionHandler` existente (403/404/422). Sin cambios en endpoints existentes.
- **Frontend**: nuevo `features/historia-clinica/`, ruta en `bootstrap.ts`, enlace en `features/agenda/pages/turno-detalle.ts`, mocks en `src/dev/`.
- **Configuración**: propiedad opcional `app.historia.ventana-edicion-evolucion` (default `PT24H`).
- **Documentación**: `CHANGES.md` (número de migración V12, estado de C-05) y `knowledge-base/04_modelo_de_datos.md` (decisiones de alcance: solo piezas permanentes, columnas de auditoría).
- **Fuera de alcance**: piezas temporarias y odontograma por cara (PA-09), HC append-only y auditoría de accesos (RN-HC-05, etapa posterior), custodia y copia al paciente (RN-HC-07), pantalla de listado/ficha de pacientes.
- **Equipo**: P4 (backend) + P6 (frontend). Governance **CRITICO**: el plan se aprueba antes de escribir código.
