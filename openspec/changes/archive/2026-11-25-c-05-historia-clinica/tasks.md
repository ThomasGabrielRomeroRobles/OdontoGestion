# Tasks

> TDD estricto en cada tarea (RED → GREEN → TRIANGULATE → REFACTOR): el test se escribe y se ve fallar antes del código. Tests de integración con PostgreSQL en Testcontainers (sin H2). Backend: P4. Frontend: P6 (avanza contra los mocks del grupo 6 desde el inicio, con el contrato de design D7).

## 1. Esquema (migración V12)

- [x] 1.1 Test de integración `FlywayV12MigrationIT` (RED, convención `FlywayVNMigrationIT` del repo; también se actualiza `FlywayDesdeCeroIT`): Flyway aplica V12 y la base rechaza historia duplicada por paciente, pieza duplicada por historia, `pieza_fdi` 19/51/10, estado desconocido, nota vacía o > 4000 y `turno_id` inexistente. Verificar: el test falla sin la migración. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 1.2 Crear `backend/src/main/resources/db/migration/V12__historia_clinica.sql` según design D1 (encabezado con propósito y rollback local) y verificar que 1.1 pasa y que `ddl-auto=validate` arranca la app (`./gradlew test --tests '*SchemaIT'`). _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_

## 2. Dominio

- [x] 2.1 `PiezaFdi` (value object) con `PERMANENTES` en orden de cuadrante; `PiezaFdiTest` cubre bordes 11/18/41/48 válidos y 10/19/20/49/51 inválidos (`PiezaFdiInvalidaException`, código `pieza-fdi-invalida`). Verificar: test unitario verde.
- [x] 2.2 Enum `EstadoPieza` (9 estados) y entidades `HistoriaClinica`, `Anamnesis`, `PiezaDentalEstado`, `EvolucionClinica` (sin setters públicos; métodos de dominio `actualizar(...)` que fijan autor) con sus repositorios; `HistoriaClinicaRepositoryIT` verifica mapeo contra V12, `insertarSiNoExiste` (`ON CONFLICT DO NOTHING`) y orden `fecha, creado_en, id`. Verificar: IT verde. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 2.3 Regla de ventana en `EvolucionClinica.esEditablePor(usuarioId, ahora, ventana)` / `editar(...)` con `EvolucionClinicaTest`: dentro, justo en el límite (inclusivo), un segundo después, otro autor. Verificar: test unitario verde.
- [x] 2.4 Excepciones `NoEsAutorException` (→ `403`, `no-es-autor`), `EvolucionFueraDeVentanaException`, `FechaEvolucionFuturaException`, `TurnoDeOtroPacienteException` (→ `422`) y su mapeo en `GlobalExceptionHandler`; extender `GlobalExceptionHandlerWebMvcTest` con el `403`. Verificar: test verde y `detail` con texto fijo.

## 3. Servicio de aplicación

- [x] 3.1 `HistoriaClinicaService.consultar(pacienteId)`: vista vacía sin escribir para paciente sin HC, `404` para paciente inexistente; `HistoriaClinicaServiceIT` verifica que tras el `GET` no hay filas en `historia_clinica`. Verificar: IT verde. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 3.2 `guardarAnamnesis` y `registrarPieza` con creación perezosa (`obtenerOCrear`), autor resuelto por email de la sesión y fecha del `Clock` fijo; pieza inválida no crea historia. Verificar: casos de la spec "Anamnesis editable con autoría" y "Odontograma FDI" en `HistoriaClinicaServiceIT`. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 3.3 `registrarEvolucion`, `listarEvoluciones` y `editarEvolucion` (fecha por defecto hoy en el consultorio, fecha futura, turno de otro paciente, autor, ventana configurable por `@ConfigurationProperties` con default `PT24H`, `editable` calculado). Verificar: escenarios de evoluciones de la spec en `HistoriaClinicaServiceIT`, con un segundo usuario ficticio insertado por JDBC para el `403`. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 3.4 `HistoriaClinicaConcurrenciaIT`: dos primeras escrituras simultáneas (anamnesis + evolución) del mismo paciente terminan con una sola historia y ambos datos guardados. Verificar: IT verde de forma repetible (3 corridas). _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_

## 4. API REST

- [x] 4.1 `HistoriaClinicaController` + DTOs record (request/response de D7, sin entidades ni email del autor) y `HistoriaClinicaApiIT`: feliz de cada endpoint, `201` + `Location` en el alta, `401` sin sesión, `403` sin CSRF, `404` paciente inexistente y evolución de otro paciente, `400` (texto largo, estado `ROTA`, nota en blanco, `fdi` no numérico) y `422` (FDI, fecha futura, turno ajeno, fuera de ventana). Verificar: IT verde. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 4.2 `HistoriaClinicaLogsIT` con `OutputCaptureExtension`: los marcadores `AlergiaFicticiaXYZ`, `ObsFicticiaXYZ` y `NotaFicticiaXYZ` no aparecen en logs ni en el cuerpo del `400` por nota larga. Verificar: IT verde. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
- [x] 4.3 Suite backend completa en verde (`./gradlew test`) sin tests salteados. _(Implementado y compilado; pendiente de correr con Docker/Testcontainers.)_
  - Resultado 2026-11-25 (P1, rama con main integrado): `./gradlew test` con Docker → 1264 tests, 0 fallos, 0 salteados; `npm test` 621, typecheck y build OK. Los 2 fallos de `HistoriaClinicaApiIT` eran del test (regex que tomaba el id del autor), corregido en 63faf5c.

## 5. Frontend: contrato, modelo y mocks

- [x] 5.1 `features/historia-clinica/historia-api.ts` con tipos estrictos del contrato D7 (sin `any`) y llamadas vía `apiRequest`; `historia-api.test.ts` verifica método, ruta y cuerpo de cada llamada. Verificar: Vitest verde.
- [x] 5.2 `modelo.ts` (orden de cuadrantes 18→11, 21→28, 48→41, 31→38; etiquetas de los 9 estados) con `modelo.test.ts`, y `src/dev/historia-mocks.ts` + test, con datos ficticios, registrado como los demás mocks de desarrollo. Verificar: Vitest verde y `npm run dev` sirve la ficha mockeada.

## 6. Frontend: pantalla

- [x] 6.1 `odontograma.ts` + test: 32 piezas en orden, estado visible por texto y clase, editor de pieza que guarda solo esa pieza y conserva el estado ante `422`. Verificar: escenarios "Odontograma visual" y "Edición de una pieza" en Vitest.
- [x] 6.2 `anamnesis-form.ts` + test: guarda, muestra "Actualizada por … el dd/mm/aaaa hh:mm" en hora del consultorio y errores `400` por campo conservando lo cargado. Verificar: escenarios de "Edición de la anamnesis" en Vitest.
- [x] 6.3 `evoluciones.ts` + test: listado en el orden de la API, "Sin evoluciones registradas", alta (nota obligatoria sin llamar a la API, fecha por defecto hoy) y edición solo si `editable`, con recarga ante `422`. Verificar: escenarios de evoluciones en Vitest.
- [x] 6.4 `historia-page.ts` + `index.ts` (`createHistoriaRoute`) y registro de `/pacientes/:id/historia` en `bootstrap.ts` antes del placeholder; tests de carga, encabezado, redirección sin sesión, id inválido/`404` y que no se usa `localStorage`/`sessionStorage` ni cambia la URL. Verificar: Vitest verde y `bootstrap.test.ts` actualizado.
- [x] 6.5 Enlace "Historia clínica" en `features/agenda/pages/turno-detalle.ts` con `data-link`; extender `turno-detalle.test.ts` (destino `/pacientes/{id}/historia`). Verificar: escenarios del delta `web-agenda` en Vitest.
- [x] 6.6 `npm run typecheck`, `npm run lint` (si existe) y `npm test` del frontend en verde; build de Vite sin errores.

## 7. Integración y documentación

- [x] 7.1 Prueba manual local (Docker + `docker compose`/`bootRun` con el seed de demo): desde un turno de la agenda abrir la HC, guardar anamnesis, una pieza y una evolución, recargar y ver los datos persistidos; revisar que los logs no muestran contenido clínico. Verificar: checklist anotado en el PR.
  - Resultado 2026-11-25 (P1, PostgreSQL descartable + seed de demo): HC abierta desde la ficha del paciente (`/pacientes/1` → "Ver historia clínica"), no desde un turno de la agenda; la pantalla muestra anamnesis, odontograma de 32 piezas y evoluciones. Escrituras probadas por la API con la sesión del navegador: anamnesis 200, pieza 16 200, pieza 19 → 422, evolución 201; el GET devuelve los datos persistidos. Logs sin el contenido clínico cargado. No se probó el guardado desde los formularios de la pantalla.
- [x] 7.2 Actualizar `CHANGES.md` (C-05 usa V12 y la FK a `turno` nace en V12) y `knowledge-base/04_modelo_de_datos.md` (solo piezas permanentes en el MVP, columnas `actualizado_por_id`/`editada_en`, ventana de 24 h configurable). Verificar: `openspec validate c-05-historia-clinica --strict` sin errores.

## Workflow follow-up

- Revisión del plan por una persona antes de `/opsx:apply` (governance CRITICO).
- `/opsx:archive c-05-historia-clinica`, marcar `[x]` en `CHANGES.md` y abrir PR revisado por otra persona (P4 ↔ P6 cruzados).
