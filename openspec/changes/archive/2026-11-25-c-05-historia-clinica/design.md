# Design

## Context

- Motivación y alcance: ver `proposal.md`. Requisitos: `specs/historia-clinica`, `specs/web-historia-clinica` y `specs/web-agenda`.
- Estado en `main` (2026-11-26): todos los changes obligatorios cerrados. Existen `paciente` (V8, C-04), `turno` (V9, C-07), lista de espera (V10) y `V11__ausencia_turno.sql` (C-08). **El próximo número Flyway libre es V12**; el texto de `CHANGES.md` que habla de "V11" quedó desactualizado. Hay un hueco en V5 que no se reutiliza (Flyway rechaza versiones menores a la aplicada sin `outOfOrder`).
- Patrones ya establecidos que se reutilizan: entidades que extienden `AuditableEntity` (id identity + `creado_en`/`actualizado_en` con el `Clock` de la app), `ConsultorioActual` para filtrar por consultorio, `PacienteService.obtener(id)` que lanza `RecursoNoEncontradoException` (404), `ReglaNegocioException`/`ValidacionDominioException` → `422`, Bean Validation → `400`, `ProblemDetails.crear(...)`, autenticación por sesión con `Authentication#getName()` = email del usuario (`UsuarioRepository.findByEmail`). Tests de logs con `OutputCaptureExtension` (como `AusenciaLogsIT`) y API con `AbstractWebIntegrationTest` + Testcontainers.
- Frontend: router con parámetros (`matchPath`), cliente `apiRequest` con CSRF, `ProblemDetail` tipado, helpers `el()` en `features/agenda/pages/dom.ts`, `formatearInstante` en `features/config/zoned-time.ts`. **`/pacientes` es un placeholder**: no hay ficha de paciente en el frontend; el único lugar donde la UI muestra un paciente es el detalle del turno (`turno-detalle.ts`).

## Goals / Non-Goals

**Goals:**
- Agregado `HistoriaClinica` mínimo, con unicidad y reglas garantizadas por la base además de Java.
- Contrato REST estable para que P6 avance contra mocks desde el día 1.
- Cero datos clínicos en logs, URLs, errores y almacenamiento del navegador.

**Non-Goals:**
- Append-only, auditoría de lecturas, historial de versiones de una pieza (RN-HC-05, etapa posterior). Solo se guarda el último autor/fecha.
- Piezas temporarias, caras dentales, odontograma gráfico tipo SVG anatómico.
- Listado/ficha de pacientes en el frontend (sigue siendo placeholder).
- Control de concurrencia optimista sobre anamnesis/piezas (un único usuario: gana la última escritura).

## Decisions

### D1 — Migración `V12__historia_clinica.sql` con FK a `turno` desde el inicio
Tablas:
- `historia_clinica(id, consultorio_id FK, paciente_id FK UNIQUE, creado_en, actualizado_en)`.
- `anamnesis(id, historia_id FK UNIQUE, alergias, medicacion_actual, enfermedades_previas, antecedentes_relevantes, embarazo boolean NULL, observaciones, actualizado_por_id FK usuario, creado_en, actualizado_en)`; textos `NOT NULL DEFAULT ''` con `CHECK (char_length ≤ 2000)` (mismo criterio que `paciente.direccion`).
- `pieza_dental_estado(id, historia_id FK, pieza_fdi smallint, estado text, observacion text NOT NULL DEFAULT '' CHECK ≤ 500, actualizado_por_id FK usuario, creado_en, actualizado_en, UNIQUE(historia_id, pieza_fdi))` con `CHECK (pieza_fdi / 10 BETWEEN 1 AND 4 AND pieza_fdi % 10 BETWEEN 1 AND 8)` y `CHECK (estado IN (...9 estados...))`.
- `evolucion_clinica(id, historia_id FK, usuario_id FK, turno_id FK turno NULL, fecha date, nota text CHECK 1..4000, editada_en timestamptz NULL, creado_en, actualizado_en)` + índice `(historia_id, fecha, creado_en, id)` para el orden cronológico.
- `consultorio_id` va solo en `historia_clinica` (raíz del agregado, regla dura 6); las tablas hijas cuelgan de ella por `historia_id` con `ON DELETE RESTRICT`.
- `fecha` es `date` (fecha civil de la consulta en el consultorio, igual que `paciente.fecha_nacimiento`); los instantes son `timestamptz` (regla dura 7).

*Alternativa descartada*: `turno_id` sin FK (lo que preveía CHANGES.md cuando C-07 podía llegar después). Ya no aplica: V9 está aplicada, así que la FK se crea acá y se evita un paso diferido.

### D2 — Creación perezosa idempotente y segura ante concurrencia
`GET` nunca escribe: si no hay historia, el servicio arma una vista vacía. Cada escritura llama a `HistoriaClinicaService.obtenerOCrear(pacienteId)` dentro de su transacción: `INSERT ... ON CONFLICT (paciente_id) DO NOTHING` (query nativa del repositorio) y luego `findByPacienteId`. Así dos primeras escrituras simultáneas convergen en la misma fila sin `DataIntegrityViolationException` ni reintentos.
*Alternativa descartada*: crear la HC junto con el paciente (C-04) — obligaría a tocar C-04 y crearía HC vacías para pacientes de la página pública, contra RN-PA-05. *Alternativa descartada*: capturar la violación de unicidad y reintentar — más código y la transacción queda marcada rollback-only.

### D3 — Validación FDI en dominio y en base
Value object `PiezaFdi` (record con `of(int)`) valida cuadrante 1-4 y pieza 1-8 y expone `PERMANENTES` (32, en orden de cuadrante). Inválida → `PiezaFdiInvalidaException extends ReglaNegocioException` (código `pieza-fdi-invalida`, `422`). La validación ocurre **antes** de `obtenerOCrear`, así una pieza inválida no crea historia. Un `fdi` no numérico en la ruta lo rechaza Spring (`400`). El `CHECK` en la base es la red de seguridad. El estado es un enum `EstadoPieza`; un valor desconocido en el JSON falla al deserializar → `400` (`HttpMessageNotReadable`, ya manejado).

### D4 — Autoría desde la sesión
Los controllers reciben `Authentication` (como `PerfilController`) y pasan `authentication.getName()`; el servicio resuelve el `Usuario` con `UsuarioRepository.findByEmail`. Las respuestas exponen `actualizadoPor` / `autor` como `{id, nombre}` (nombre y apellido del usuario), nunca el email. Para el `403` de edición se compara `evolucion.usuario.id` con el usuario de la sesión → `NoEsAutorException` (nueva, mapeada a `403` con código `no-es-autor` en `GlobalExceptionHandler`).

### D5 — Ventana de edición configurable con el `Clock`
Propiedad `app.historia.ventana-edicion-evolucion` (`Duration`, default `PT24H`) en un `@ConfigurationProperties` record. Editable si `ahora ≤ creadoEn + ventana` (límite inclusivo, escenario de borde). Fuera → `EvolucionFueraDeVentanaException extends ReglaNegocioException` (`422`, `evolucion-fuera-de-ventana`). La respuesta incluye `editable: boolean` calculado con el mismo predicado y el mismo usuario, para que el frontend no duplique la regla. La edición conserva `creado_en` y fija `editada_en`.

### D6 — Validaciones de la evolución
- `nota`: `@NotBlank @Size(max=4000)` → `400`.
- `fecha`: opcional; nula → hoy en `America/Argentina/Buenos_Aires` según el `Clock`. Futura → `422` (`fecha-evolucion-futura`).
- `turnoId`: opcional; si no existe en el consultorio o es de otro paciente → `422` (`turno-de-otro-paciente`; mismo código para ambos, evita distinguir existencia).
- Orden: `ORDER BY fecha, creado_en, id` en la query del repositorio.
- Una evolución pedida por la ruta de otro paciente → `404` (se busca por `historia_id` + `id`).

### D7 — Contrato REST (para mocks del frontend)
```
GET  /api/v1/pacientes/{id}/historia
  200 {paciente:{id,nombre,apellido}, anamnesis:{alergias,medicacionActual,enfermedadesPrevias,
       antecedentesRelevantes,embarazo|null,observaciones,actualizadoPor|null,actualizadoEn|null},
       piezas:[{fdi,estado|null,observacion,actualizadoPor|null,actualizadoEn|null} x32],
       evoluciones:[EvolucionResponse]}
PUT  .../historia/anamnesis            body = campos de anamnesis       → 200 AnamnesisResponse
PUT  .../historia/odontograma/piezas/{fdi}  {estado, observacion?}       → 200 PiezaResponse
GET  .../historia/evoluciones                                            → 200 [EvolucionResponse]
POST .../historia/evoluciones          {nota, fecha?, turnoId?}         → 201 + Location
PUT  .../historia/evoluciones/{evId}   {nota, fecha?}                   → 200 EvolucionResponse
EvolucionResponse = {id, fecha, nota, turnoId|null, autor:{id,nombre}, creadaEn, editadaEn|null, editable}
```
Un único `HistoriaClinicaController` delgado (regla dura 3), DTOs record (regla dura 2). Las vistas de aplicación (`HistoriaVista`, etc.) se mapean a responses en el controller, como en pacientes.

### D8 — Privacidad de los datos clínicos
- Ningún `log.*` en el paquete `historia` incluye contenido; solo ids si hace falta.
- Los mensajes de las excepciones de dominio son textos fijos sin datos de entrada. Los errores de Bean Validation ya devuelven solo nombre de campo y mensaje genérico (verificar en el test con un texto marcador).
- `toString()` de las entidades no se sobrescribe con contenido (default de `Object`).
- Test `HistoriaClinicaLogsIT` con `OutputCaptureExtension` y marcadores "…FicticiaXYZ".
- Frontend: sin `console.*`, sin storage, sin datos en query string; el id del paciente es el único dato en la URL.

### D9 — Frontend `features/historia-clinica/`
Estructura igual a las features existentes: `historia-api.ts` (tipos + llamadas con `apiRequest`), `modelo.ts` (orden FDI de cuadrantes, etiquetas de estados), `odontograma.ts`, `anamnesis-form.ts`, `evoluciones.ts`, `historia-page.ts`, `index.ts` (`createHistoriaRoute(api)`), `test-fixtures.ts`, `historia-clinica.css`; mocks en `src/dev/historia-mocks.ts` con datos ficticios. La ruta `/pacientes/:id/historia` se registra en `bootstrap.ts` **antes** del placeholder `/pacientes`. Cada componente recibe la API inyectada (testeable con Vitest + jsdom, sin `fetch` real). Errores con el `ProblemDetail` compartido; los `400` con `errors` se muestran por campo (reutilizando el criterio de `features/config/form-errors.ts`).

### D10 — Acceso desde la agenda
`turno-detalle.ts` agrega un `<a href="/pacientes/{id}/historia" data-link>` "Historia clínica" (el router ya intercepta `data-link`). Es la única entrada a la HC hasta que exista una ficha de paciente. *Alternativa descartada*: construir ahora la pantalla `/pacientes` (listado + ficha) — es alcance de otro change y no está en C-05.

## Risks / Trade-offs

- [HC editable no cumple Ley 26.529 art. 13 (IN-02)] → solo datos ficticios (RN-GL-01), autor y fecha en cada modificación y columnas preparadas para pasar a append-only; se declara en la KB.
- [Solo se guarda el último autor/fecha de una pieza o anamnesis, no el historial] → aceptado por DD-09; la etapa posterior agrega una tabla de asientos.
- [Estados de pieza no validados con un odontólogo (PA-09)] → enum cerrado en Java y `CHECK` en base; ampliar es una migración chica.
- [Última escritura gana en anamnesis/piezas] → un único usuario con login; si aparecen más odontólogos se agrega `@Version`.
- [Stretch fuera de fecha de entrega (25/11 ya pasó)] → no bloquea nada en `main`; se trabaja en rama propia y se integra solo con la suite completa en verde.
- [Choque de número de migración si otro change abre V12 en paralelo] → `openspec list` hoy solo muestra `c-08-ausentismo-backend` (ya integrado); avisar al equipo al abrir el PR.

## Migration Plan

1. `V12__historia_clinica.sql` aditiva (solo `CREATE TABLE`/índices); no toca datos existentes.
2. Rollback local documentado en el encabezado del archivo: `DROP TABLE evolucion_clinica, pieza_dental_estado, anamnesis, historia_clinica;` y borrar la fila `12` de `flyway_schema_history`.
3. El seed de demo (`DemoSeeder`) no se modifica: las HC se crean al usarlas. Opcional: no se cargan datos clínicos de ejemplo.
