# Tasks

> TDD estricto: en cada tarea "RED → GREEN", primero se escribe el test, se ejecuta y se confirma que falla por la razón esperada. Recién después se implementa lo mínimo para que pase, y se triangula.
>
> Reparto original: grupos 1–4 backend (P1: grupos 1 y 2; P2: grupos 3 y 4), grupos 5–10 frontend (P5). Implementación de los grupos 1–4: Juan Ignacio (P5) junto con Thomas (P1) y Martina (P2).
>
> **Precondición del backend**: `C-02` mergeado en `main`.
>
> **El frontend (5–9) puede avanzar contra dobles y `fetch` mockeado antes de eso**. El grupo 10 requiere backend real.
>
> Comandos:
> - Backend: `./gradlew test` en `backend/` (requiere Docker para Testcontainers).
> - Frontend: `npm test` y `npm run typecheck` en `frontend/`.

## 1. Backend: esquema V4, seed y mapeo JPA (P1 + P2)

- [x] 1.1 Confirmar en `origin/main` el próximo número Flyway libre (`V4`, después de los `V2`/`V3` de `C-02`); verificar con `ls backend/src/main/resources/db/migration`
- [x] 1.2 RED: `FlywayV4MigrationIT`, con datos de prueba en transacciones revertidas. Casos:
  - Las tablas `prestacion`, `horario_trabajo` y `bloqueo_agenda` existen con `consultorio_id`, `creado_en` y `actualizado_en` NOT NULL.
  - `dia_semana` es `int2`, `nombre` es `citext` y `desde`/`hasta` son `timestamptz`.
  - Cada restricción con nombre rechaza su caso: `uq_prestacion_consultorio_nombre` ("Consulta" vs "consulta"), `ck_prestacion_duracion_positiva`, `ck_horario_dia_semana`, `ck_horario_rango`, `ex_horario_trabajo_sin_solapamiento` (lunes 09-13 vs 12-15) y `ck_bloqueo_rango`.
  - Se aceptan la franja contigua (13-14) y el mismo rango en otro día.
  - `motivo` toma `''` por defecto.
  - Seed: 6 prestaciones activas con sus duraciones y 10 horarios L-V.
  - Ampliar `FlywayV1MigrationIT` para que las versiones aplicadas sean `1, 2, 3, 4`.
  - Confirmar que fallan.
- [x] 1.3 GREEN: crear `V4__agenda_configuracion.sql` según D2 y D4: restricciones con nombre, comentario del `tsrange` con fecha ancla, rollback comentado y seed con `INSERT … SELECT` desde `consultorio` al final. Verificar que `FlywayV4MigrationIT` y `FlywayV1MigrationIT` pasan
- [x] 1.4 RED → GREEN: entidades `Prestacion`, `HorarioTrabajo` y `BloqueoAgenda` (extienden `AuditableEntity`, `@ManyToOne` LAZY a `Consultorio`, `citext`, `short` para `dia_semana`) y sus repositorios. Verificar con `AgendaConfigRepositoriesIT`:
  - El contexto arranca con `ddl-auto=validate`.
  - Cada entidad se guarda y se relee con `creadoEn`/`actualizadoEn` completos.
  - Las consultas derivadas filtran por consultorio y ordenan (prestaciones por nombre, horarios por día y hora, bloqueos por intersección de rango).

## 2. Backend: prestaciones (P1)

- [x] 2.1 RED: unit tests de `PrestacionService` (Mockito, sin Spring), con la granularidad leída de `ConsultorioService.parametrosVigentes()`. Casos:
  - 40 con 15 → `422` en `duracionMin`; 45 con 30 → `422`; 0 o negativa → `422`.
  - Nombre vacío o en blanco → `422` en `nombre`; más de 80 caracteres → `422`.
  - Nombre repetido (chequeo previo) → `422` en `nombre`.
  - `DataIntegrityViolationException` de `uq_prestacion_consultorio_nombre` en `saveAndFlush` → `422` en `nombre`; otra restricción → se relanza.
  - Desactivar y activar son idempotentes y **no** validan la duración aunque ya no sea múltiplo.
  - Id inexistente → `RecursoNoEncontradoException`.
  - Confirmar que fallan.
- [x] 2.2 GREEN: implementar `PrestacionService` (`@Transactional`, todos los errores juntos en una `ValidacionDominioException`, `saveAndFlush` en `try`) y el traductor de restricciones por nombre (D3); verificar que 2.1 pasa
- [x] 2.3 RED → GREEN: `PrestacionController` con records de request (solo `@NotNull`) y response (sin entidades), para `GET`, `POST` (`201` + `Location`), `PUT`, `POST /{id}/desactivar` y `POST /{id}/activar`. Verificar con `PrestacionApiIT` (MockMvc, sesión y CSRF reales, cada test limpia lo que crea):
  - Alta válida.
  - Duración no múltiplo, cero y con granularidad distinta.
  - Nombre duplicado (`422` real desde la restricción, incluido "consulta") y vacío.
  - Campo ausente → `400`.
  - Listado con inactivas ordenado por nombre.
  - Edición.
  - `404`.
  - Desactivar y reactivar, también con duración "no múltiplo" tras subir la granularidad.
  - `DELETE` → `405`.
  - Cambio de granularidad no retroactivo.
  - Seed visible por API.
- [x] 2.4 RED → GREEN: IT de acceso: sin sesión → `401`, y escritura sin CSRF → `403`, para `/prestaciones`

## 3. Backend: horarios (P2)

- [x] 3.1 RED: unit tests de `HorarioService`. Casos:
  - `horaDesde`/`horaHasta` mal formadas ("9", "25:00", "ab") → `422` en su campo.
  - `horaDesde >= horaHasta` → `422` en `horaHasta`.
  - `diaSemana` fuera de 1–7 → `422` en `diaSemana`.
  - Hora no alineada a la granularidad ("09:10" con 15) → `422`.
  - Solapamiento previo en Java (sin contar la propia franja al editar) → `422` en `horaDesde`.
  - `DataIntegrityViolationException` de `ex_horario_trabajo_sin_solapamiento` → `422` en `horaDesde`.
  - Confirmar que fallan.
- [x] 3.2 GREEN: implementar `HorarioService` (`@Transactional`, `String` → `LocalTime`, `saveAndFlush` en `try`); verificar que 3.1 pasa
- [x] 3.3 RED → GREEN: `HorarioController` (`GET` ordenado, `POST`, `PUT`, `DELETE` → `204`), con requests de solo `@NotNull` y horas como `String`. Verificar con `HorarioApiIT`:
  - Alta válida (sábado 09-12).
  - Rango invertido.
  - Día fuera de rango.
  - Hora no alineada.
  - Hora mal formada → `422` (no `400`).
  - Solapamiento rechazado contra el seed.
  - Contigua permitida.
  - Otro día permitido.
  - Edición que solapa no modifica.
  - Listado ordenado.
  - Baja.
  - Baja de inexistente → `404`.
  - Escritura sin CSRF → `403`.

## 4. Backend: bloqueos (P2)

- [x] 4.1 RED: unit tests de `BloqueoService` con `Clock` fijo. Casos:
  - `desde`/`hasta` sin zona o mal formados → `422` en su campo.
  - `desde >= hasta` → `422` en `hasta`.
  - Motivo de más de 200 caracteres → `422`.
  - Motivo `null`, ausente o en blanco → se guarda `""`; con texto → recortado.
  - Filtros de listado según la tabla de D12: ninguno, solo `desde`, solo `hasta`, ambos; filtro mal formado → `422`; `hasta <= desde` → `422` en `hasta`.
  - Confirmar que fallan.
- [x] 4.2 GREEN: implementar `BloqueoService` (parseo con `OffsetDateTime`, consulta por intersección `hasta > :inicio [AND desde < :fin]` usando `Clock`); verificar que 4.1 pasa
- [x] 4.3 RED → GREEN: `BloqueoController` (`GET ?desde&hasta` como `String` opcionales, `POST`, `PUT`, `DELETE`). Verificar con `BloqueoApiIT`:
  - Alta válida en UTC, con `motivo` y sin él (`""`).
  - Rango invertido.
  - Sin zona.
  - Fecha mal formada → `422` en `desde` (no `400`).
  - Campo ausente → `400`.
  - Filtro por rango, solo `desde`, solo `hasta` y sin rango (con el `Clock` fijo de los ITs).
  - Filtro mal formado → `422`.
  - Edición.
  - Baja.
  - `404`.
  - `401` sin sesión.

## 5. Frontend: APIs tipadas de configuración (P5, contra `fetch` mockeado)

- [x] 5.1 RED: `features/config/agenda-config-api.test.ts`, que fija método, URL, cuerpo JSON y cabecera CSRF de cada operación de D1 (listar, crear, editar, desactivar y activar prestaciones; CRUD de horarios; bloqueos con `desde`/`hasta` en la query); confirmar que falla
- [x] 5.2 GREEN: implementar `agenda-config-api.ts` con los tipos `Prestacion`, `Horario`, `Bloqueo` y sus inputs, sobre `apiRequest` y sin `any`; verificar que 5.1 pasa y que `npm run typecheck` está limpio
- [x] 5.3 RED → GREEN: `config-api.ts` (`GET/PUT /consultorio`, `GET/PUT /perfil`, `POST /perfil/password`), con el contrato asumido de `C-02` en D1; verificar con su test. Si `C-02` ya publicó su design, contrastar y ajustar antes
- [x] 5.4 RED → GREEN: `form-errors.ts` (`ApiError` → `{porCampo, general}`): `422` con campos, `409` sin campos usa el título, y `network` da el mensaje de conexión; verificar con su test

## 6. Frontend: validadores y zona horaria (P5, funciones puras)

- [x] 6.1 RED → GREEN: `validators.ts`, validación de duración. Casos:
  - Vacía.
  - No entera.
  - ≤ 0.
  - No múltiplo con granularidad 15 (40) y 30 (45).
  - Válida.
  - Granularidad desconocida, que solo valida entero positivo (D8).
  - Verificar con test.
- [x] 6.2 RED → GREEN: `validators.ts`, rango horario (`"14:00"`–`"13:00"` inválido) y solapamiento semiabierto contra las franjas del mismo día. Casos:
  - 09-13 contra 12-15 se solapa.
  - 09-13 contra 13-14 no se solapa.
  - En edición se excluye la propia franja.
  - El mensaje nombra la franja en conflicto.
  - Verificar con test.
- [x] 6.3 RED → GREEN: `validators.ts`, parámetros del consultorio enteros > 0, nombre de prestación obligatorio, y nueva contraseña igual a su repetición; verificar con test
- [x] 6.4 RED → GREEN: `zoned-time.ts`, conversión local ↔ UTC (D7). Casos:
  - 24/12/2026 00:00 en Buenos Aires ↔ `2026-12-24T03:00:00Z`.
  - 23:30 local que cruza de día en UTC.
  - Formato de presentación `dd/MM/yyyy HH:mm`.
  - El test fuerza un `TZ` de proceso distinto (p. ej. `Asia/Tokyo`) para probar que no depende de la zona del navegador.
  - Verificar que pasa.

## 7. Frontend: página de configuración, rutas y navegación (P5)

- [x] 7.1 RED: `config-page.test.ts`. Casos:
  - `/configuracion` muestra 5 pestañas con Consultorio activa.
  - `/configuracion/horarios` activa Horarios.
  - Las pestañas son enlaces `data-link`.
  - Confirmar que falla.
- [x] 7.2 GREEN: implementar `config-page.ts` con el despacho por sección y las secciones inyectadas; verificar que 7.1 pasa
- [x] 7.3 RED → GREEN: registrar en `bootstrap.ts` las 6 rutas privadas (D6), y agregar el enlace "Configuración" en `app-layout.ts`. Verificar con tests:
  - El layout incluye el enlace.
  - Sin sesión, `/configuracion/prestaciones` → `/login?next=/configuracion/prestaciones`.
  - Los tests existentes de `web-app-shell` siguen en verde.

## 8. Frontend: secciones Consultorio y Perfil (P5, con dobles inyectados)

- [x] 8.1 RED → GREEN: `consultorio-section.ts`. Casos:
  - Carga inicial.
  - Guardado con `PUT` y confirmación.
  - Parámetro 0 no envía.
  - Aviso de que los cambios no son retroactivos.
  - Error de campo del servidor junto al campo, conservando los valores.
  - Botón deshabilitado durante el envío.
  - Verificar con test.
- [x] 8.2 RED → GREEN: `perfil-section.ts`. Casos:
  - Edición del perfil.
  - Contraseñas que no coinciden no envían.
  - `204` limpia los campos y confirma.
  - `422` en `passwordActual` muestra el error y limpia los campos.
  - Los campos de contraseña son de tipo `password`, con `autocomplete` adecuado.
  - Verificar con test.

## 9. Frontend: secciones Prestaciones, Horarios y Bloqueos (P5, con dobles inyectados)

- [x] 9.1 RED → GREEN: `prestaciones-section.ts`. Casos:
  - Listado con estado y acción Activar/Desactivar.
  - Lista vacía.
  - Alta que se refleja sin recargar.
  - Desactivar con confirmación.
  - Validación de duración con la granularidad de `GET /consultorio`.
  - `422` del servidor por campo.
  - Falla de red.
  - Verificar con test.
- [x] 9.2 RED → GREEN: `horarios-section.ts`. Casos:
  - Agrupado de lunes a domingo, con "Sin atención" en los días vacíos.
  - Agregar.
  - Editar.
  - Eliminar con confirmación.
  - Rango invertido y solapamiento detectados sin enviar.
  - `422` del servidor por campo.
  - Verificar con test.
- [x] 9.3 RED → GREEN: `bloqueos-section.ts`. Casos:
  - Listado en hora local.
  - Alta que envía instantes UTC.
  - Rango invertido sin enviar.
  - Editar.
  - Eliminar con confirmación.
  - Motivo opcional.
  - Verificar con test.
- [x] 9.4 (Opcional, D9) Modo de mocks en memoria para desarrollo detrás de `import.meta.env.DEV && VITE_CONFIG_MOCKS`; verificar con `npm run build` y una búsqueda en `dist/` de que el código de mocks no está presente

## 10. Verificación integral (requiere C-02 y grupos 1–4 en la rama)

- [x] 10.1 Contrastar los DTOs reales del backend con los tipos del frontend (`agenda-config-api.ts`, `config-api.ts`) y corregir diferencias; verificar con `npm run typecheck` y los tests de API
- [x] 10.2 Correr todos los tests del repo: `./gradlew test` en `backend/`, y `npm test` y `npm run typecheck` en `frontend/`, todo en verde
- [x] 10.3 Prueba manual del Flujo 8 contra el backend real (`bootRun` + Vite):
  - Login.
  - Crear una prestación de 40 min (rechazada), crear una de 60 min.
  - Desactivar una prestación.
  - Agregar un horario solapado (rechazado) y un sábado 09-12.
  - Crear un bloqueo y verlo en hora local.
  - Cambiar la granularidad.
  - Anotar el resultado en el PR.
  - Resultado 2026-11-21 (P1, commit `255dd65`, PostgreSQL 16 descartable): `gradlew clean test` 369/369, `npm test` 206/206, `typecheck` limpio. En vivo: `/configuracion` carga los 7 parámetros y guardar da 200 "Cambios guardados."; prestación de 40 min → 422 (múltiplo de 15), de 60 min → 201; desactivar y reactivar "Control" → 200; horario solapado → 422, sábado 09-12 → 201; bloqueo 12:00Z se muestra 09:00 (Buenos Aires), fechas invertidas o sin zona → 422; granularidad 15 → 10 aceptada y luego 40 min → 201. No se probó editar/eliminar desde la UI ni la sección Perfil.

## Workflow follow-up

- Revisión del contrato D1 por P1 y P2 antes de cerrar el frontend contra mocks.
- `/opsx:archive c-06-agenda-configuracion`, marcar `[x]` en `CHANGES.md` y abrir un PR desde `feat/c-06-agenda-configuracion`, revisado por otra persona.
- Avisar a P1 (`C-07`) de que las prestaciones pueden no ser múltiplo tras bajar la granularidad (riesgo de `design.md`).
