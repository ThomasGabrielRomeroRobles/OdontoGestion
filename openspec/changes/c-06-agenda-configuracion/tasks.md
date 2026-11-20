# Tasks

> TDD estricto: en cada tarea "RED → GREEN", primero se escribe el test, se ejecuta y se confirma que falla por la razón esperada. Recién después se implementa lo mínimo para que pase, y se triangula.
>
> Reparto: grupos 1–4 backend (P1: grupos 1 y 2; P2: grupos 3 y 4), grupos 5–10 frontend (P5).
>
> **Precondición del backend**: `C-02` mergeado en `main`.
>
> **El frontend (5–9) puede avanzar contra dobles y `fetch` mockeado antes de eso**. El grupo 10 requiere backend real.
>
> Comandos:
> - Backend: `./gradlew test` en `backend/`.
> - Frontend: `npm test` y `npm run typecheck` en `frontend/`.

## 1. Backend: esquema V4 y seed (P1, con P2 para horarios y bloqueos)

- [ ] 1.1 Confirmar en `origin/main` el próximo número Flyway libre (esperado `V4`, después de los `V2`/`V3` de `C-02`), y anotarlo en `design.md` D2 si difiere
- [ ] 1.2 RED: `FlywayV4MigrationIT` con los siguientes casos; confirmar que fallan:
  - Las tablas `prestacion`, `horario_trabajo` y `bloqueo_agenda` existen con `consultorio_id` NOT NULL.
  - `UNIQUE (consultorio_id, nombre)` rechaza "Consulta" y "consulta".
  - `CHECK duracion_min > 0`.
  - `CHECK hora_desde < hora_hasta`.
  - `EXCLUDE` rechaza lunes 09-13 contra lunes 12-15 y acepta 13-14 contiguo y martes 09-13.
  - `CHECK desde < hasta` en bloqueos.
- [ ] 1.3 GREEN: crear `V4__agenda_configuracion.sql` según D2 (con un comentario que explique el `tsrange` con fecha ancla y el rollback); verificar que `FlywayV4MigrationIT` pasa
- [ ] 1.4 RED → GREEN: entidades JPA `Prestacion`, `HorarioTrabajo` y `BloqueoAgenda` (con `consultorio_id`, sobre la base de entidad de `C-02`) y sus repositorios; verificar que el contexto arranca con `ddl-auto=validate` y que un test de repositorio guarda y lee cada entidad
- [ ] 1.5 RED → GREEN: `AgendaConfigSeeder`, que corre después del seed de `C-02`; verificar con IT los escenarios "Seed aplicado" (6 prestaciones, 10 horarios) y "Segundo arranque" (no duplica ni reactiva "Control")

## 2. Backend: prestaciones (P1)

- [ ] 2.1 RED: unit tests de `PrestacionService` con estos casos; confirmar que fallan:
  - Duración múltiplo de la granularidad vigente: 40 con 15 → error, y 45 con 30 → error.
  - Duración ≤ 0 → error.
  - Nombre vacío → error.
  - Desactivar y activar son idempotentes.
- [ ] 2.2 GREEN: implementar `PrestacionService` (`@Transactional`, granularidad leída del consultorio, `23505` traducido a error de campo `nombre`); verificar que 2.1 pasa
- [ ] 2.3 RED → GREEN: `PrestacionController` con DTOs/records (sin exponer entidades) para `GET`, `POST` (con `201` + `Location`), `PUT`, `POST /{id}/desactivar` y `POST /{id}/activar`. Verificar con IT (MockMvc + sesión + CSRF) los escenarios de `agenda-configuration`:
  - Alta válida.
  - Duración no múltiplo, cero y con granularidad distinta.
  - Nombre duplicado y vacío.
  - Listado con inactivas.
  - Edición.
  - `404`.
  - Desactivar y reactivar.
  - `DELETE` → `405`.
  - Cambio de granularidad no retroactivo.
- [ ] 2.4 RED → GREEN: IT de acceso: sin sesión → `401`, y escritura sin CSRF → `403`, para `/prestaciones`

## 3. Backend: horarios (P2)

- [ ] 3.1 RED: unit tests de `HorarioService` con estos casos; confirmar que fallan:
  - `horaDesde < horaHasta`.
  - `diaSemana` entre 1 y 7.
  - Horas alineadas a la granularidad ("09:10" con 15 → error).
- [ ] 3.2 GREEN: implementar `HorarioService` (`@Transactional`, `23P01` → `422` con campo `horaDesde`); verificar que 3.1 pasa
- [ ] 3.3 RED → GREEN: `HorarioController` (`GET` ordenado, `POST`, `PUT`, `DELETE` → `204`). Verificar con IT:
  - Alta válida.
  - Rango invertido.
  - Día fuera de rango.
  - Hora no alineada.
  - Solapamiento rechazado.
  - Contiguos permitidos.
  - Otro día permitido.
  - Edición que solapa no modifica.
  - Listado ordenado.
  - Baja.
  - Baja de inexistente → `404`.
  - Escritura sin CSRF → `403`.

## 4. Backend: bloqueos (P2)

- [ ] 4.1 RED: unit tests de `BloqueoService` con estos casos; confirmar que fallan:
  - `desde < hasta`.
  - Motivo de hasta 200 caracteres.
  - Instante sin zona rechazado (por deserialización a `Instant` → `422` con campo).
- [ ] 4.2 GREEN: implementar `BloqueoService` y la consulta por intersección de rango (`desde < :hasta AND hasta > :desde`; sin rango, `hasta > now` usando `Clock`); verificar que 4.1 pasa
- [ ] 4.3 RED → GREEN: `BloqueoController` (`GET ?desde&hasta`, `POST`, `PUT`, `DELETE`). Verificar con IT:
  - Alta válida en UTC.
  - Rango invertido.
  - Sin zona.
  - Filtro por rango.
  - Sin rango excluye pasados (con `Clock` fijo).
  - Baja.
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

- [ ] 10.1 Contrastar los DTOs reales del backend con los tipos del frontend (`agenda-config-api.ts`, `config-api.ts`) y corregir diferencias; verificar con `npm run typecheck` y los tests de API
- [ ] 10.2 Correr todos los tests del repo: `./gradlew test` en `backend/`, y `npm test` y `npm run typecheck` en `frontend/`, todo en verde
- [ ] 10.3 Prueba manual del Flujo 8 contra el backend real (`bootRun` + Vite):
  - Login.
  - Crear una prestación de 40 min (rechazada), crear una de 60 min.
  - Desactivar una prestación.
  - Agregar un horario solapado (rechazado) y un sábado 09-12.
  - Crear un bloqueo y verlo en hora local.
  - Cambiar la granularidad.
  - Anotar el resultado en el PR.

## Workflow follow-up

- Revisión del contrato D1 por P1 y P2 antes de cerrar el frontend contra mocks.
- `/opsx:archive c-06-agenda-configuracion`, marcar `[x]` en `CHANGES.md` y abrir un PR desde `feat/c-06-agenda-configuracion`, revisado por otra persona.
- Avisar a P1 (`C-07`) de que las prestaciones pueden no ser múltiplo tras bajar la granularidad (riesgo de `design.md`).
