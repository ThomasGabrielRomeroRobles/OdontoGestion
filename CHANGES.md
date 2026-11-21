# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto OdontoGestion.
> Cada change es un OPSX change coherente y verificable por sí mismo; los que abarcan backend + frontend se reparten entre dos personas del mismo carril.
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

Contexto: equipo de 6 personas, entrega 2026-11-25 (hoy 2026-11-19, ~6 días). Stack: Spring Boot + Spring Security (sesión + CSRF) + Hibernate/JPA + PostgreSQL + Flyway + Gradle; front TypeScript vanilla (Vite en dev, estáticos en Spring); JUnit 5 + Testcontainers con TDD estricto. Un único usuario con login (odontólogo); pacientes sin cuenta (página pública + tokens de un solo uso por email); sin sillones; entidad `Consultorio` desde el día 1; demo local; recordatorios con `@Scheduled`.

## Corte de entrega 25/11 (decisión del equipo, 2026-11-19; DD-16)

Corte **por valor**: se comprometen los 11 changes core; la historia clínica corre en su carril paralelo como "si llega".

| Categoría | Changes |
|-----------|---------|
| **OBLIGATORIO** (entrega 2026-11-25) | C-01, C-02, C-03, C-04, C-06, C-07, C-08, C-09, C-10, C-11, C-12 |
| **SI LLEGA** (stretch, sin compromiso) | C-05 historia-clinica (épica 8: ficha, anamnesis, odontograma FDI, evoluciones) |

- Si C-05 no se entrega, se documenta como etapa posterior; no bloquea ningún otro change.
- **C-12 no depende de C-05**: la integración de la historia clínica en la agenda es condicional a que C-05 esté cerrado.
- Anti-abuso de la página pública confirmado (DD-14): `PENDIENTE_CONFIRMACION` con hueco retenido 30 min, confirmación por token de un solo uso, 5 solicitudes/h por IP, máx. 2 pendientes por DNI/email (429), honeypot, validación estricta y respuestas sin enumeración. CAPTCHA/OTP (US-113) queda como mejora posterior.

---

Esta versión consolida los 26 changes originales en **12 changes** y mueve la **historia clínica a un carril paralelo (stretch, "si llega")** que arranca apenas están listos auth + pacientes (no depende de la agenda).

Mapa de consolidación (IDs viejos → nuevos): C-01+C-04 → **C-01**; C-02+C-03+C-05 → **C-02**; C-09+C-10+C-21 → **C-03**; C-08 → **C-04**; C-23+C-24+C-25 → **C-05**; C-06+C-07 → **C-06**; C-11+C-12 → **C-07**; C-13+C-22 → **C-08**; C-14+C-15+C-16 → **C-09**; C-17+C-18+C-19 → **C-10**; C-20 → **C-11**; C-26 → **C-12**.

---

## Cómo usar este documento

1. Identificá el próximo change cuyo estado sea `[ ]` y cuyas dependencias estén todas en `[x]`.
2. Leé los archivos de la sección **Leer antes** de ese change (KB en `knowledge-base/`).
3. Ejecutá `/opsx:propose C-NN-nombre` y luego `/opsx:apply` (TDD estricto: RED → GREEN → TRIANGULATE → REFACTOR).
4. Al terminar, `/opsx:archive C-NN-nombre` para sincronizar specs y cerrar el change.
5. Marcá el checkbox del change (`[ ]` → `[x]`) en este archivo.

Respetá el nivel de **Governance**: CRITICO = analizar y esperar aprobación humana antes de escribir; ALTO = proponer y esperar revisión; MEDIO = implementar con checkpoints; BAJO = autonomía si pasan los tests.

Dentro de un change con backend + frontend, el backend publica primero el contrato (DTOs/endpoints) y el frontend avanza contra mocks hasta integrarse; ambos cierran juntos el change.

---

## Árbol de dependencias

```
C-01 foundation-setup
 └── C-02 core-auth-config
       ├── C-03 notificaciones-tokens-registro
       │     ├── (C-07, C-09, C-11 también lo requieren)
       ├── C-04 pacientes-core
       │     ├── C-05 historia-clinica          ← CARRIL PARALELO, STRETCH "si llega" (no depende de agenda; no bloquea nada)
       │     └── C-07 turnos-agenda-core        (también requiere C-03, C-06)
       └── C-06 agenda-configuracion
             └── C-07 turnos-agenda-core
                   ├── C-08 agenda-frontend-ausentismo
                   ├── C-11 recordatorios-scheduler
                   └── C-09 publico-solicitud-acciones   (también requiere C-03)
                         └── C-10 lista-espera
C-12 demo-hardening  ← depende de los changes obligatorios (C-01 a C-04, C-06 a C-11); C-05 es opcional y no lo bloquea
```

### Paralelismo por fase

```
GATE 0: C-01 ✓
  → C-02 core-auth-config                    [P1 + P2]

GATE 1: C-02 ✓                               ← FORK (3 ramas independientes)
  → C-03 notificaciones-tokens-registro      [P3 backend + P6 front registro]
  → C-04 pacientes-core                      [P4]
  → C-06 agenda-configuracion                [P1 backend prestaciones/horarios/bloqueos, P2 backend, P5 front]

GATE 2: C-04 ✓                               ← ABRE CARRIL PARALELO DE HISTORIA CLÍNICA (STRETCH)
  → C-05 historia-clinica  (SI LLEGA)        [P4 backend + P6 front; corre hasta el cierre sin bloquear nada]

GATE 3: C-03 ✓, C-04 ✓, C-06 ✓
  → C-07 turnos-agenda-core                  [P1 disponibilidad/POST/máquina de estados, P2 mover/cancelar/atendido/eventos, P3 integración de notificaciones]
  → C-08 (front) puede arrancar contra el contrato [P5]

GATE 4: C-07 ✓                               ← FORK
  → C-09 publico-solicitud-acciones          [P1 solicitud pública, P2 acciones por token, P5 front público; P3 apoya]
  → C-08 agenda-frontend-ausentismo          [P5 front agenda, P3 backend ausentismo]
  → C-11 recordatorios-scheduler             [P3 — tras terminar ausentismo]

GATE 5: C-09 ✓
  → C-10 lista-espera                        [P1 core + avisos, P2 aceptación concurrente, P6/P5 front]

GATE 6: C-01 … C-04, C-06 … C-11 ✓  (C-05 NO es requisito)
  → C-12 demo-hardening                      [Todo el equipo; integra HC en la agenda solo si C-05 está cerrado]
```

### Camino crítico (7 changes — mínimo irreducible; C-05 queda fuera: es stretch)

`C-01 → C-02 → C-06 → C-07 → C-09 → C-10 → C-12*`

\* `C-12` es el único cierre posible. Ramas no críticas: `C-02 → C-03` (se une en C-07), `C-02 → C-04 → C-05` (carril de historia clínica, **stretch "si llega"**: si no termina antes del cierre, queda como etapa posterior y no bloquea C-12), `C-07 → C-08`, `C-07 → C-11`. Si C-04 o C-03 se retrasan, pasan a ser críticos porque C-07 los requiere.

### Plan óptimo con 6 personas

Carriles fijos: **P1** Backend crítico (agenda/público) · **P2** Backend crítico 2 (seguridad, concurrencia, acciones) · **P3** Backend infra (notificaciones, tokens, schedulers) · **P4** Backend pacientes + historia clínica · **P5** Frontend A (shell, config, agenda, público) · **P6** Frontend B (registro de envíos, historia clínica, lista de espera).

Fechas aproximadas: G0 = 19-nov, G1 = 20-nov, G2-G3 = 21-22-nov, G4 = 23-nov, G5 = 24-nov, G6 = 25-nov.

| Gate | P1 | P2 | P3 | P4 | P5 | P6 |
|------|----|----|----|----|----|----|
| G0 (19-nov) | C-01 backend (Gradle, Flyway V1, Testcontainers) | C-01 backend (capas, `ProblemDetail`, `Clock`) | Prepara puerto `NotificationChannel`, plantillas, GreenMail (sin DB) | Prepara fixtures de pacientes y `/opsx:propose` de C-04/C-05 | C-01 frontend (Vite, `shared/http`, router, login) | `/opsx:propose` de C-03 registro y C-05 front |
| G1 (20-nov) | C-02 modelos + seed + migración V2 | C-02 auth-session, CSRF, limitación de intentos, config/perfil API | Espera C-02 modelos; avanza tests de outbox | Espera C-02; propose C-04 | Cierra C-01; empieza mocks de C-06 front | Cierra C-01; mocks del registro de envíos |
| G2 (20-21-nov) | C-06 backend: prestaciones | C-06 backend: horarios y bloqueos | C-03: outbox + tokens (migraciones V6/V7) | C-04 pacientes-core | C-06 frontend (config, prestaciones, horarios) | C-03 frontend registro de envíos |
| G3 (21-22-nov) | C-07 disponibilidad + `POST /turnos` + EXCLUDE gist | C-07 mover/cancelar/atendido + eventos de dominio | C-07 integración notificaciones (CONFIRMACION/CANCELACION); cierra C-03 | C-05 backend: HC + anamnesis + odontograma + evoluciones | Cierra C-06; empieza C-08 front contra contrato | C-05 front: ficha + odontograma |
| G4 (22-23-nov) | C-09 solicitud pública (rate limit, honeypot) | C-09 acciones por token (confirmar/cancelar) | C-08 ausentismo backend → C-11 recordatorios | C-05 evoluciones/cierre backend | C-08 front agenda; luego C-09 front público | C-05 front cierre; luego C-09 front acciones-token |
| G5 (23-24-nov) | C-10 lista-espera core + avisos de hueco | C-10 aceptación concurrente de avisos | C-11 recordatorios (idempotencia) y apoyo a C-10 | Tests de integración HC; apoyo a C-10/C-11 | C-10 front público (lista de espera) | C-10 front odontólogo + página `/avisos/{token}` |
| G6 (25-nov) | C-12 demo + verificación flujos 2, 3, 5 | C-12 revisión de seguridad (secretos, logs sin datos clínicos) | C-12 SMTP real y límites | C-12 seed demo + guía de ejecución | C-12 build Vite a `static/` y SPA fallback | C-12 smoke E2E y pulido de UI |

Notas: (1) En G0/G1 los carriles sin tarea ejecutable adelantan artefactos OPSX (`/opsx:propose`) y fixtures de tests, no código de producción. (2) La columna P4/P6 mantiene la historia clínica (C-05, stretch "si llega") corriendo en paralelo de G2 a G5 sin tocar el camino crítico; si el equipo se retrasa, P4/P6 se reasignan a apoyar C-07/C-09/C-10/C-11. (3) El vínculo `turno_id` de las evoluciones se guarda como columna nullable sin FK en C-05; C-07 agrega la FK en su migración solo si C-05 ya está aplicada (si no, la FK se difiere a la etapa posterior).

---

## FASE 1 — Fundación, acceso y configuración (Épicas 1 y 2)

> C-01 y C-02 son la cadena inicial indispensable. C-02 abre tres ramas simultáneas (C-03, C-04, C-06).

### [C-01] `foundation-setup`
- **Estado**: `[x]` completado (archivado 2026-11-19 en `openspec/changes/archive/2026-11-19-c-01-foundation-setup/`)
- **Scope**:
  - Proyecto Gradle `backend/` (Spring Boot, Web, Security, Data JPA, Validation, Mail, Flyway, PostgreSQL) con capas `api / application / domain / infrastructure` y `OdontoGestionApplication`
  - Proyecto `frontend/` (Vite + TypeScript vanilla, Vitest + jsdom, proxy `/api` → `http://localhost:8080`) con `features/`, `shared/`, `pages/`, `main.ts`; tarea Gradle que copia el build de Vite a `backend/src/main/resources/static/`
  - Perfiles `dev` / `demo`, `application.yml` con variables de entorno de la KB, `.env.example` sin valores reales, `.gitignore`
  - Migración V1 Flyway: extensiones `btree_gist` y `citext`
  - Base de tests: JUnit 5 + Testcontainers PostgreSQL (con `btree_gist`), `Clock` inyectable, manejador global de errores (`ProblemDetail`)
  - Frontend shell: `shared/http` (lectura de `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN` en escrituras, `401` → `/login`, errores `ProblemDetail`), `shared/router` (rutas privadas/públicas), estado de sesión, layout base, página `/login` y logout contra el contrato de Flujo 1 (mocks en tests)
  - Tests: arranque de contexto con Testcontainers, migración V1, cliente HTTP (CSRF, 401), guardia de rutas, render de login
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios, §Capas del backend, §Variables de entorno, §Seguridad (CSRF)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-02, §DD-04, §DD-12, §DD-15
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/01_vision_y_objetivos.md`

### [C-02] `core-auth-config`
- **Estado**: `[x]` completado (archivado 2026-11-20 en `openspec/changes/archive/2026-11-20-c-02-core-auth-config/`)
- **Scope**:
  - Migración V2: tablas `consultorio` y `usuario` (email `citext` único, `password_hash`, `consultorio_id`, timestamps); modelos `Consultorio`, `Usuario`, base `AuditableEntity` (id BIGINT), repositorios; provider de `consultorio_id` único (sin multi-tenant)
  - Seed de arranque (US-003): "Consultorio Demo" y odontólogo ficticio con contraseña desde `BOOTSTRAP_ODONTOLOGO_PASSWORD` (BCrypt); sin endpoint de registro
  - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` con sesión de servidor (`JSESSIONID` HttpOnly, SameSite=Lax), rotación de id al login, expiración por inactividad (`SESSION_TIMEOUT_MIN`), limitación de intentos (RN-AU-09), `401` genérico
  - CSRF con `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`), `403` sin token; `SecurityFilterChain`: `/api/v1/**` autenticado salvo `publico/**`, `acciones/**`, `avisos/**`; sin CORS
  - Migración V3: parámetros en `consultorio` (`granularidad_min`, `anticipacion_min_reserva_min`, `limite_cancelacion_horas`, `recordatorio_horas_antes`, `vencimiento_aviso_min`, `vencimiento_pendiente_min`, tolerancia de ausente) con defaults
  - `GET/PUT /api/v1/consultorio`, `GET/PUT /api/v1/perfil`, `POST /api/v1/perfil/password`; parámetros positivos (`422`), cambios aplican solo a nuevas operaciones
  - Tests: seed idempotente, email único, BCrypt, login ok/inválido, CSRF, expiración, rutas públicas vs privadas (MockMvc + Spring Security Test), limitación de intentos, defaults de parámetros, cambio de contraseña
- **Dependencias**: `C-01`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU-01…09), §Configuración y agenda (RN-AG)
  - `knowledge-base/04_modelo_de_datos.md` §consultorio, §usuario, §Seed data inicial
  - `knowledge-base/03_actores_y_roles.md` §Modelo de acceso, §Rutas públicas
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/06_funcionalidades.md` §Épica 1 (US-001, US-002, US-003), §Épica 2 (US-010, US-013)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-01, §DD-03

### [C-06] `agenda-configuracion`
- **Estado**: `[x]` completado (archivado 2026-11-21 en `openspec/changes/archive/2026-11-21-c-06-agenda-configuracion/`)
- **Scope**:
  - Migración V4: tablas `prestacion`, `horario_trabajo` (exclusión de solapamiento por consultorio y día), `bloqueo_agenda`; seed de las 6 prestaciones y horarios L-V 09-13 / 14-18
  - Modelos `Prestacion`, `HorarioTrabajo`, `BloqueoAgenda`; `/api/v1/prestaciones` (CRUD, desactivar sin perder historial), `/api/v1/horarios`, `/api/v1/bloqueos`
  - Reglas: duración múltiplo de la granularidad (`422`), `hora_desde < hora_hasta`; el listado de turnos afectados por un bloqueo se completa en C-07
  - Frontend `config`: pantallas de consultorio y perfil (parámetros, cambio de contraseña), prestaciones, horarios semanales y bloqueos; validación de cliente y manejo de `422`
  - Tests: múltiplo de granularidad, solapamiento de horarios, desactivación, bloqueos; Vitest de formularios, errores y llamadas HTTP con CSRF
- **Dependencias**: `C-02`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §prestacion, §horario_trabajo, §bloqueo_agenda, §Seed data inicial
  - `knowledge-base/05_reglas_de_negocio.md` §Configuración y agenda (RN-AG-02, RN-AG-03, RN-AG-07, RN-AG-08)
  - `knowledge-base/06_funcionalidades.md` §Épica 2 (US-010…US-013)
  - `knowledge-base/07_flujos_principales.md` §Flujo 8
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend)

---

## FASE 2 — Pacientes, infraestructura de notificaciones/tokens e historia clínica [stretch] (Épicas 6-parcial y 8)

> C-03, C-04 y C-06 corren en paralelo tras C-02. C-05 (historia clínica, **stretch "si llega"**) arranca al cerrar C-04 y sigue en carril paralelo hasta el cierre.

### [C-03] `notificaciones-tokens-registro`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración V6: tabla `notificacion` (outbox) con índices únicos (`turno_id`, `tipo`) y (`aviso_id`) para idempotencia; puerto `NotificationChannel`, `EmailChannel` (Spring Mail, `MAIL_*`), adaptador falso en memoria para tests
  - `NotificationService.encolar(...)` + `procesarPendientes()` con 3 reintentos y espera creciente; el fallo de envío no revierte la operación origen; `@Scheduled` cada 5 min; plantillas por tipo (`CONFIRMAR_SOLICITUD`, `CONFIRMAR_LISTA_ESPERA`, `CONFIRMACION_TURNO`, `RECORDATORIO`, `AVISO_HUECO`, `CANCELACION`, `AUSENTE_REPROGRAMAR`) sin datos clínicos
  - Migración V7: tabla `token_acceso` (propósito, FK exclusiva a turno/aviso/entrada, `token_hash` SHA-256, `vence_en`, `usado_en`, constraint de exactamente una FK no nula); servicio de generación (128+ bits), hash y consumo atómico (`UPDATE … WHERE usado_en IS NULL AND vence_en > now()`), propósitos `CONFIRMAR_SOLICITUD | CONFIRMAR_TURNO | CANCELAR_TURNO | ACEPTAR_AVISO | CONFIRMAR_LISTA_ESPERA`; `GET` nunca consume, `POST` ejecuta; `410 Gone` sin enumeración
  - `GET /api/v1/notificaciones?estado=&tipo=` (estado, destinatario, error, intentos) y reintento acotado; feature de front `notificaciones` con estados `PENDIENTE | ENVIADA | FALLIDA`
  - Tests: contrato común del canal, reintentos, idempotencia, fallo sin rollback, GreenMail; unicidad/vencimiento/un solo uso de tokens (dos consumos concurrentes con Testcontainers), propósito incorrecto; listado filtrable y acceso solo con sesión; Vitest del listado
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/12_notificaciones.md`
  - `knowledge-base/05_reglas_de_negocio.md` §Notificaciones (RN-NO), §Tokens de acceso del paciente (RN-TK-01…05)
  - `knowledge-base/04_modelo_de_datos.md` §notificacion, §token_acceso
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (Tokens de paciente), §Estrategia de concurrencia
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-07, §DD-08, §DD-13, §SU-10

### [C-04] `pacientes-core`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración V5: tabla `paciente` (único `consultorio_id + dni`, índices por dni, email, apellido+nombre, `cantidad_ausencias`)
  - Modelo `Paciente` (sin cuenta); servicio `buscarOCrearPorDni`
  - `GET /api/v1/pacientes?dni=&q=`, `POST /api/v1/pacientes`, `PUT /api/v1/pacientes/{id}`
  - Tests: unicidad por DNI, búsqueda, creación en el momento, filtrado por `consultorio_id`, `401` sin sesión
- **Dependencias**: `C-02`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §paciente
  - `knowledge-base/05_reglas_de_negocio.md` §Pacientes (RN-PA)
  - `knowledge-base/03_actores_y_roles.md`
  - `knowledge-base/10_preguntas_abiertas.md` §PA-03

### [C-05] `historia-clinica`  — STRETCH ("si llega")
- **Estado**: `[ ]` pendiente (no comprometido para el 25/11; si no se entrega se documenta como etapa posterior, DD-16)
- **Scope**:
  - STRETCH: no es obligatorio para la entrega y no bloquea ningún otro change (ni C-12)
  - CARRIL PARALELO: no depende de la agenda; arranca al cerrar C-04
  - Migración V8: tablas `historia_clinica` (única por paciente, creación perezosa), `anamnesis`, `pieza_dental_estado` (único `historia_id + pieza_fdi`) y `evolucion_clinica` (`turno_id` nullable sin FK; la FK la agrega C-07)
  - `GET /api/v1/pacientes/{id}/historia`, `PUT /api/v1/pacientes/{id}/historia/anamnesis`
  - `PUT /api/v1/pacientes/{id}/historia/odontograma/piezas/{fdi}` (estado + observación, autor y fecha), validación FDI (`422`), 32 piezas permanentes, enum de estados MVP
  - `POST/GET /api/v1/pacientes/{id}/historia/evoluciones` (orden cronológico, edición por el autor dentro de ventana)
  - Frontend `historia-clinica`: ficha con anamnesis, odontograma FDI visual (32 piezas, estado/observación) y listado + alta de evoluciones; acceso desde la ficha del paciente (el acceso desde un turno de la agenda se integra en C-12, solo si C-05 está hecho)
  - Sin datos clínicos en logs, URLs ni emails; acceso exclusivo con sesión del odontólogo
  - Tests: una HC por paciente, `401` sin sesión, sin datos clínicos en logs, validación FDI, unicidad por pieza, autoría, orden cronológico; Vitest de odontograma, edición de pieza y alta de evolución
- **Dependencias**: `C-04` (y `C-02` transitivo)
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §historia_clinica, §anamnesis, §pieza_dental_estado, §evolucion_clinica
  - `knowledge-base/05_reglas_de_negocio.md` §Historia clínica (RN-HC-01…06), §Excepciones globales (RN-GL)
  - `knowledge-base/06_funcionalidades.md` §Épica 8 (US-070, US-071, US-072)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-09, §SU-05
  - `knowledge-base/10_preguntas_abiertas.md` §IN-02, §IN-03, §PA-09

---

## FASE 3 — Agenda y turnos (Épicas 3 y 7)

### [C-07] `turnos-agenda-core`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración V9: tabla `turno` con `EXCLUDE USING gist` (`consultorio_id`, `tstzrange(inicio, fin, '[)')`) para estados activos, `CHECK (inicio < fin)`, `@Version`, índices; agrega FK de `evolucion_clinica.turno_id`
  - Modelo `Turno` y máquina de estados (`PENDIENTE_CONFIRMACION, RESERVADO, CONFIRMADO, CANCELADO, AUSENTE, ATENDIDO, EXPIRADO`); resolver PA-06 sobre `ATENDIDO/AUSENTE` en la restricción
  - Strategy `DisponibilidadService` (horarios − bloqueos − turnos activos, duración, granularidad, anticipación mínima), `GET /api/v1/agenda/disponibilidad`, `GET /api/v1/agenda?desde&hasta`, `POST /api/v1/turnos` (US-021) con `HuecoNoDisponible` → `409` + alternativas; consulta de turnos afectados por un bloqueo (cierra US-012)
  - Expiración de `PENDIENTE_CONFIRMACION` vencidas (invocable antes de cada reserva y por scheduler)
  - `PUT /api/v1/turnos/{id}/mover`, `POST /api/v1/turnos/{id}/cancelar` (motivo, `cancelado_por`), `POST /api/v1/turnos/{id}/atendido`, `GET /api/v1/turnos/hoy` (US-022, US-023, US-024)
  - Eventos `TurnoCreado`, `HuecoLiberado`, `TurnoMovido` (`@TransactionalEventListener`); notificaciones `CONFIRMACION_TURNO` y `CANCELACION` vía `NotificationService`
  - Tests: no superposición bajo concurrencia (Testcontainers), disponibilidad, transiciones inválidas `422`, `409`, expiración con `Clock`, mover libera hueco y publica `HuecoLiberado`, notificación encolada, `@Version` concurrente
- **Dependencias**: `C-03, C-04, C-06`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §turno
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU), §Configuración y agenda (RN-AG)
  - `knowledge-base/06_funcionalidades.md` §Épica 3 (US-021…US-024)
  - `knowledge-base/07_flujos_principales.md` §Flujo 3 (pasos 1-2), §Flujo 7
  - `knowledge-base/08_arquitectura_propuesta.md` §Estrategia de concurrencia y agenda, §Estrategia de pruebas
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-05, §DD-06; `knowledge-base/10_preguntas_abiertas.md` §PA-06

### [C-08] `agenda-frontend-ausentismo`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `agenda`: vista día/semana con colores por estado (incluye pendientes), creación de turno con buscador de paciente por DNI y alta rápida, propuesta de horarios libres, manejo de `409`; acciones mover, cancelar con motivo, marcar atendido; lista "turnos de hoy"
  - `POST /api/v1/turnos/{id}/ausente`: solo tras inicio + tolerancia (`422` antes), estado `AUSENTE`, incrementa `paciente.cantidad_ausencias`, evento `TurnoAusente`, notificación `AUSENTE_REPROGRAMAR` con enlace a la página pública
  - Reversión de la marca dentro de 24 h (RN-AS-06) que ajusta el contador; `GET /api/v1/pacientes/{id}/ausencias` (US-061) y su visualización en agenda y detalle del paciente
  - Tests: tolerancia con `Clock`, contador, reversión, notificación encolada; Vitest de render de agenda, conflicto `409`, flujo de cancelación, marcar ausente
- **Dependencias**: `C-07` (el front puede arrancar contra el contrato)
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3, §Épica 7 (US-060, US-061)
  - `knowledge-base/07_flujos_principales.md` §Flujo 3, §Flujo 6, §Flujo 7
  - `knowledge-base/05_reglas_de_negocio.md` §Ausentismo (RN-AS)
  - `knowledge-base/10_preguntas_abiertas.md` §PA-05, §PA-08
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend)

---

## FASE 4 — Página pública y acciones del paciente (Épica 4)

### [C-09] `publico-solicitud-acciones`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/publico/disponibilidad` (solo huecos libres) y `POST /api/v1/publico/solicitudes-turno` → `202` genérico (sin enumerar DNI/email); transacción: expirar pendientes, `buscarOCrearPorDni`, turno `PENDIENTE_CONFIRMACION` con `pendiente_hasta` (TTL 30 min), token `CONFIRMAR_SOLICITUD`, notificación
  - Anti-abuso: limitación de tasa por IP y por DNI/email (`PUBLIC_RATE_LIMIT_PER_HOUR`, `429`), campo señuelo, `409` con alternativas, `422` fuera de horario/bloqueo; scheduler de expiración a `EXPIRADO`
  - `GET /acciones/{token}` (resumen sin consumir) y `POST /acciones/{token}` (ejecuta): `CONFIRMAR_SOLICITUD` → `RESERVADO` + `CONFIRMACION_TURNO` (US-031); `CONFIRMAR_TURNO` → `CONFIRMADO`; `CANCELAR_TURNO` → `CANCELADO` + `HuecoLiberado` respetando el límite de horas (US-032); respuesta informativa para token vencido/usado (`410`)
  - Frontend: features `publico` (prestación, horario libre, formulario con campo señuelo, manejo de `409/422/429`) y `acciones-token` (páginas `/acciones/{token}` con confirmar/cancelar por POST e informativas), sin sesión
  - Tests: solicitud feliz, hueco tomado `409`, rate limit, honeypot, no enumeración, expiración con `Clock`, solicitudes simultáneas del mismo horario, confirmar/cancelar dentro/fuera del límite, token usado/vencido/propósito incorrecto, doble POST concurrente; Vitest del flujo público y página de token
- **Dependencias**: `C-07, C-03`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 2, §Flujo 5 (pasos 4-6)
  - `knowledge-base/05_reglas_de_negocio.md` §Página pública (RN-PU-01…07), §Tokens (RN-TK), §Turnos (RN-TU-03)
  - `knowledge-base/06_funcionalidades.md` §Épica 4 (US-030, US-031, US-032)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-14, §SU-09
  - `knowledge-base/10_preguntas_abiertas.md` §PA-02, §PA-03; `knowledge-base/03_actores_y_roles.md` §Rutas públicas

---

## FASE 5 — Lista de espera y recordatorios (Épicas 5 y 6)

> C-10 y C-11 son independientes entre sí; C-11 puede hacerse mientras C-09/C-10 avanzan.

### [C-10] `lista-espera`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración V10: tabla `entrada_lista_espera` (estados `PENDIENTE_CONFIRMACION | ACTIVA | CUBIERTA | CANCELADA | VENCIDA`, único activo/pendiente por paciente+prestación, `@Version`) y `aviso_hueco` (`ENVIADO | ACEPTADO | EXPIRADO | CERRADO`, `vence_en`, `@Version`)
  - `POST /api/v1/publico/lista-espera` (nace pendiente, token `CONFIRMAR_LISTA_ESPERA`) y confirmación vía `/acciones/{token}` → `ACTIVA` (US-040); `GET/POST/DELETE /api/v1/lista-espera` para el odontólogo (US-042); Strategy de compatibilidad (prestación, rango de fechas, franja) y vencimiento
  - Manejador de `HuecoLiberado`: busca entradas `ACTIVA` compatibles, crea aviso + token `ACEPTAR_AVISO` por paciente y encola `AVISO_HUECO` a todos a la vez
  - `GET /avisos/{token}` y `POST /avisos/{token}/aceptar`: el primero inserta el turno `RESERVADO` (origen `LISTA_ESPERA`), entrada a `CUBIERTA`, demás avisos `CERRADO`, confirmación al ganador, el resto `409`; vencimiento de avisos por scheduler; `410 Gone`
  - Frontend: formulario público de lista de espera, página `/avisos/{token}` (aceptar, "ya no disponible", vencido) y pantalla del odontólogo (listado filtrable, alta y baja)
  - Tests: compatibilidad, duplicado `409`, N avisos para N compatibles, dos aceptaciones simultáneas (solo una gana), vencimiento, hueco ocupado por reserva normal; Vitest de formulario, aceptación y conflicto
- **Dependencias**: `C-09`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 3, §Flujo 4
  - `knowledge-base/05_reglas_de_negocio.md` §Lista de espera (RN-LE-01…11), §Página pública (RN-PU-05)
  - `knowledge-base/04_modelo_de_datos.md` §entrada_lista_espera, §aviso_hueco
  - `knowledge-base/06_funcionalidades.md` §Épica 5 (US-040, US-042)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-06; `knowledge-base/12_notificaciones.md` §AVISO_HUECO

### [C-11] `recordatorios-scheduler`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Job `@Scheduled` (cada 5 min) que busca turnos `RESERVADO` en la ventana "ahora + `recordatorio_horas_antes`" sin recordatorio previo
  - Por turno: tokens `CONFIRMAR_TURNO` y `CANCELAR_TURNO` únicos, notificación `RECORDATORIO` con enlaces, sin datos clínicos
  - Idempotencia por índice único (`turno_id`, `tipo`); retoma pendientes tras reinicio
  - Tests: una sola ejecución efectiva ante dos corridas, ventana con `Clock` inyectable, reinicio, email sin datos clínicos
- **Dependencias**: `C-07, C-03`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 5
  - `knowledge-base/05_reglas_de_negocio.md` §Notificaciones (RN-NO-02, RN-NO-03, RN-NO-04, RN-NO-05, RN-NO-09)
  - `knowledge-base/12_notificaciones.md` §Programación
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-13

---

## FASE 6 — Cierre y demo local

### [C-12] `demo-hardening`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Build de Vite copiado a `static/` y arranque desde un solo proceso (`http://localhost:8080`) con perfil `demo`; SPA fallback
  - Seed de demo: 10 pacientes ficticios y datos de ejemplo; SMTP real por variables de entorno (verificación de envío y límite diario)
  - Integración final: ausencias en la ficha del paciente; **condicional a C-05** (stretch): acceso a la historia clínica desde un turno de la agenda solo si C-05 está cerrado, sin bloquear el change
  - Verificación de los flujos 2, 3 y 5 de punta a punta (Playwright opcional); revisión de que no hay secretos versionados ni datos clínicos en logs/emails
  - Guía de ejecución local y de pruebas (Docker para Testcontainers, PostgreSQL con `btree_gist`)
  - Tests: arranque con perfil demo, smoke E2E de solicitud → confirmación → recordatorio
- **Dependencias**: `C-01, C-02, C-03, C-04, C-06, C-07, C-08, C-09, C-10, C-11` (C-05 es dependencia blanda/opcional: no bloquea)
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Despliegue, §Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-11, §SU-10, §SU-11
  - `knowledge-base/07_flujos_principales.md` §Flujo 2, §Flujo 3, §Flujo 5
  - `knowledge-base/10_preguntas_abiertas.md` §PA-07; `knowledge-base/09_decisiones_y_supuestos.md` §DD-16 (corte por valor)

---

## Tabla resumen

| ID | Change | Fase | Dependencias | Governance | Carril principal |
|----|--------|------|--------------|------------|------------------|
| C-01 | foundation-setup | 1 | — | BAJO | P1, P2, P5 |
| C-02 | core-auth-config | 1 | C-01 | CRITICO | P1, P2 |
| C-03 | notificaciones-tokens-registro | 2 | C-02 | CRITICO | P3, P6 |
| C-04 | pacientes-core | 2 | C-02 | MEDIO | P4 |
| C-05 (stretch) | historia-clinica | 2 | C-04 | CRITICO | P4, P6 (paralelo, "si llega") |
| C-06 | agenda-configuracion | 1 | C-02 | MEDIO | P1, P2, P5 |
| C-07 | turnos-agenda-core | 3 | C-03, C-04, C-06 | CRITICO | P1, P2, P3 |
| C-08 | agenda-frontend-ausentismo | 3 | C-07 | MEDIO | P5, P3 |
| C-09 | publico-solicitud-acciones | 4 | C-07, C-03 | CRITICO | P1, P2, P5 |
| C-10 | lista-espera | 5 | C-09 | ALTO | P1, P2, P5, P6 |
| C-11 | recordatorios-scheduler | 5 | C-07, C-03 | ALTO | P3 |
| C-12 | demo-hardening | 6 | C-01 … C-04, C-06 … C-11 (C-05 opcional) | MEDIO | Todos |

**Primer change recomendado**: `C-01` (foundation-setup). Para arrancar: `/opsx:propose C-01-foundation-setup`
