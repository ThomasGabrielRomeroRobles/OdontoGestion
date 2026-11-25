# CHANGES â€” Secuencia de ImplementaciÃ³n

> Ãndice canÃ³nico de todos los changes del proyecto OdontoGestion.
> Cada change es un OPSX change coherente y verificable por sÃ­ mismo; los que abarcan backend + frontend se reparten entre dos personas del mismo carril.
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

Contexto: equipo de 6 personas, entrega 2026-11-25 (hoy 2026-11-19, ~6 dÃ­as). Stack: Spring Boot + Spring Security (sesiÃ³n + CSRF) + Hibernate/JPA + PostgreSQL + Flyway + Gradle; front TypeScript vanilla (Vite en dev, estÃ¡ticos en Spring); JUnit 5 + Testcontainers con TDD estricto. Un Ãºnico usuario con login (odontÃ³logo); pacientes sin cuenta (pÃ¡gina pÃºblica + tokens de un solo uso por email); sin sillones; entidad `Consultorio` desde el dÃ­a 1; demo local; recordatorios con `@Scheduled`.

## Corte de entrega 25/11 (decisiÃ³n del equipo, 2026-11-19; DD-16)

Corte **por valor**: se comprometen los 11 changes core; la historia clÃ­nica corre en su carril paralelo como "si llega".

| CategorÃ­a | Changes |
|-----------|---------|
| **OBLIGATORIO** (entrega 2026-11-25) | C-01, C-02, C-03, C-04, C-06, C-07, C-08, C-09, C-10, C-11, C-12 |
| **SI LLEGA** (stretch, sin compromiso) | C-05 historia-clinica (Ã©pica 8: ficha, anamnesis, odontograma FDI, evoluciones) |

- Si C-05 no se entrega, se documenta como etapa posterior; no bloquea ningÃºn otro change.
- **C-12 no depende de C-05**: la integraciÃ³n de la historia clÃ­nica en la agenda es condicional a que C-05 estÃ© cerrado.
- Anti-abuso de la pÃ¡gina pÃºblica confirmado (DD-14): `PENDIENTE_CONFIRMACION` con hueco retenido 30 min, confirmaciÃ³n por token de un solo uso, 5 solicitudes/h por IP, mÃ¡x. 2 pendientes por DNI/email (429), honeypot, validaciÃ³n estricta y respuestas sin enumeraciÃ³n. CAPTCHA/OTP (US-113) queda como mejora posterior.

---

Esta versiÃ³n consolida los 26 changes originales en **12 changes** y mueve la **historia clÃ­nica a un carril paralelo (stretch, "si llega")** que arranca apenas estÃ¡n listos auth + pacientes (no depende de la agenda).

Mapa de consolidaciÃ³n (IDs viejos â†’ nuevos): C-01+C-04 â†’ **C-01**; C-02+C-03+C-05 â†’ **C-02**; C-09+C-10+C-21 â†’ **C-03**; C-08 â†’ **C-04**; C-23+C-24+C-25 â†’ **C-05**; C-06+C-07 â†’ **C-06**; C-11+C-12 â†’ **C-07**; C-13+C-22 â†’ **C-08**; C-14+C-15+C-16 â†’ **C-09**; C-17+C-18+C-19 â†’ **C-10**; C-20 â†’ **C-11**; C-26 â†’ **C-12**.

---

## CÃ³mo usar este documento

1. IdentificÃ¡ el prÃ³ximo change cuyo estado sea `[ ]` y cuyas dependencias estÃ©n todas en `[x]`.
2. LeÃ© los archivos de la secciÃ³n **Leer antes** de ese change (KB en `knowledge-base/`).
3. EjecutÃ¡ `/opsx:propose C-NN-nombre` y luego `/opsx:apply` (TDD estricto: RED â†’ GREEN â†’ TRIANGULATE â†’ REFACTOR).
4. Al terminar, `/opsx:archive C-NN-nombre` para sincronizar specs y cerrar el change.
5. MarcÃ¡ el checkbox del change (`[ ]` â†’ `[x]`) en este archivo.

RespetÃ¡ el nivel de **Governance**: CRITICO = analizar y esperar aprobaciÃ³n humana antes de escribir; ALTO = proponer y esperar revisiÃ³n; MEDIO = implementar con checkpoints; BAJO = autonomÃ­a si pasan los tests.

Dentro de un change con backend + frontend, el backend publica primero el contrato (DTOs/endpoints) y el frontend avanza contra mocks hasta integrarse; ambos cierran juntos el change.

---

## Ãrbol de dependencias

```
C-01 foundation-setup
 â””â”€â”€ C-02 core-auth-config
       â”œâ”€â”€ C-03 notificaciones-tokens-registro
       â”‚     â”œâ”€â”€ (C-07, C-09, C-11 tambiÃ©n lo requieren)
       â”œâ”€â”€ C-04 pacientes-core
       â”‚     â”œâ”€â”€ C-05 historia-clinica          â† CARRIL PARALELO, STRETCH "si llega" (no depende de agenda; no bloquea nada)
       â”‚     â””â”€â”€ C-07 turnos-agenda-core        (tambiÃ©n requiere C-03, C-06)
       â””â”€â”€ C-06 agenda-configuracion
             â””â”€â”€ C-07 turnos-agenda-core
                   â”œâ”€â”€ C-08 agenda-frontend-ausentismo
                   â”œâ”€â”€ C-11 recordatorios-scheduler
                   â””â”€â”€ C-09 publico-solicitud-acciones   (tambiÃ©n requiere C-03)
                         â””â”€â”€ C-10 lista-espera
C-12 demo-hardening  â† depende de los changes obligatorios (C-01 a C-04, C-06 a C-11); C-05 es opcional y no lo bloquea
```

### Paralelismo por fase

```
GATE 0: C-01 âœ“
  â†’ C-02 core-auth-config                    [P1 + P2]

GATE 1: C-02 âœ“                               â† FORK (3 ramas independientes)
  â†’ C-03 notificaciones-tokens-registro      [P3 backend + P6 front registro]
  â†’ C-04 pacientes-core                      [P4]
  â†’ C-06 agenda-configuracion                [P1 backend prestaciones/horarios/bloqueos, P2 backend, P5 front]

GATE 2: C-04 âœ“                               â† ABRE CARRIL PARALELO DE HISTORIA CLÃNICA (STRETCH)
  â†’ C-05 historia-clinica  (SI LLEGA)        [P4 backend + P6 front; corre hasta el cierre sin bloquear nada]

GATE 3: C-03 âœ“, C-04 âœ“, C-06 âœ“
  â†’ C-07 turnos-agenda-core                  [P1 disponibilidad/POST/mÃ¡quina de estados, P2 mover/cancelar/atendido/eventos, P3 integraciÃ³n de notificaciones]
  â†’ C-08 (front) puede arrancar contra el contrato [P5]

GATE 4: C-07 âœ“                               â† FORK
  â†’ C-09 publico-solicitud-acciones          [P1 solicitud pÃºblica, P2 acciones por token, P5 front pÃºblico; P3 apoya]
  â†’ C-08 agenda-frontend-ausentismo          [P5 front agenda, P3 backend ausentismo]
  â†’ C-11 recordatorios-scheduler             [P3 â€” tras terminar ausentismo]

GATE 5: C-09 âœ“
  â†’ C-10 lista-espera                        [P1 core + avisos, P2 aceptaciÃ³n concurrente, P6/P5 front]

GATE 6: C-01 â€¦ C-04, C-06 â€¦ C-11 âœ“  (C-05 NO es requisito)
  â†’ C-12 demo-hardening                      [Todo el equipo; integra HC en la agenda solo si C-05 estÃ¡ cerrado]
```

### Camino crÃ­tico (7 changes â€” mÃ­nimo irreducible; C-05 queda fuera: es stretch)

`C-01 â†’ C-02 â†’ C-06 â†’ C-07 â†’ C-09 â†’ C-10 â†’ C-12*`

\* `C-12` es el Ãºnico cierre posible. Ramas no crÃ­ticas: `C-02 â†’ C-03` (se une en C-07), `C-02 â†’ C-04 â†’ C-05` (carril de historia clÃ­nica, **stretch "si llega"**: si no termina antes del cierre, queda como etapa posterior y no bloquea C-12), `C-07 â†’ C-08`, `C-07 â†’ C-11`. Si C-04 o C-03 se retrasan, pasan a ser crÃ­ticos porque C-07 los requiere.

### Plan Ã³ptimo con 6 personas

Carriles fijos: **P1** Backend crÃ­tico (agenda/pÃºblico) Â· **P2** Backend crÃ­tico 2 (seguridad, concurrencia, acciones) Â· **P3** Backend infra (notificaciones, tokens, schedulers) Â· **P4** Backend pacientes + historia clÃ­nica Â· **P5** Frontend A (shell, config, agenda, pÃºblico) Â· **P6** Frontend B (registro de envÃ­os, historia clÃ­nica, lista de espera).

Fechas aproximadas: G0 = 19-nov, G1 = 20-nov, G2-G3 = 21-22-nov, G4 = 23-nov, G5 = 24-nov, G6 = 25-nov.

| Gate | P1 | P2 | P3 | P4 | P5 | P6 |
|------|----|----|----|----|----|----|
| G0 (19-nov) | C-01 backend (Gradle, Flyway V1, Testcontainers) | C-01 backend (capas, `ProblemDetail`, `Clock`) | Prepara puerto `NotificationChannel`, plantillas, GreenMail (sin DB) | Prepara fixtures de pacientes y `/opsx:propose` de C-04/C-05 | C-01 frontend (Vite, `shared/http`, router, login) | `/opsx:propose` de C-03 registro y C-05 front |
| G1 (20-nov) | C-02 modelos + seed + migraciÃ³n V2 | C-02 auth-session, CSRF, limitaciÃ³n de intentos, config/perfil API | Espera C-02 modelos; avanza tests de outbox | Espera C-02; propose C-04 | Cierra C-01; empieza mocks de C-06 front | Cierra C-01; mocks del registro de envÃ­os |
| G2 (20-21-nov) | C-06 backend: prestaciones | C-06 backend: horarios y bloqueos | C-03: outbox + tokens (migraciones V6/V7) | C-04 pacientes-core | C-06 frontend (config, prestaciones, horarios) | C-03 frontend registro de envÃ­os |
| G3 (21-22-nov) | C-07 disponibilidad + `POST /turnos` + EXCLUDE gist | C-07 mover/cancelar/atendido + eventos de dominio | C-07 integraciÃ³n notificaciones (CONFIRMACION/CANCELACION); cierra C-03 | C-05 backend: HC + anamnesis + odontograma + evoluciones | Cierra C-06; empieza C-08 front contra contrato | C-05 front: ficha + odontograma |
| G4 (22-23-nov) | C-09 solicitud pÃºblica (rate limit, honeypot) | C-09 acciones por token (confirmar/cancelar) | C-08 ausentismo backend â†’ C-11 recordatorios | C-05 evoluciones/cierre backend | C-08 front agenda; luego C-09 front pÃºblico | C-05 front cierre; luego C-09 front acciones-token |
| G5 (23-24-nov) | C-10 lista-espera core + avisos de hueco | C-10 aceptaciÃ³n concurrente de avisos | C-11 recordatorios (idempotencia) y apoyo a C-10 | Tests de integraciÃ³n HC; apoyo a C-10/C-11 | C-10 front pÃºblico (lista de espera) | C-10 front odontÃ³logo + pÃ¡gina `/avisos/{token}` |
| G6 (25-nov) | C-12 demo + verificaciÃ³n flujos 2, 3, 5 | C-12 revisiÃ³n de seguridad (secretos, logs sin datos clÃ­nicos) | C-12 SMTP real y lÃ­mites | C-12 seed demo + guÃ­a de ejecuciÃ³n | C-12 build Vite a `static/` y SPA fallback | C-12 smoke E2E y pulido de UI |

Notas: (1) En G0/G1 los carriles sin tarea ejecutable adelantan artefactos OPSX (`/opsx:propose`) y fixtures de tests, no cÃ³digo de producciÃ³n. (2) La columna P4/P6 mantiene la historia clÃ­nica (C-05, stretch "si llega") corriendo en paralelo de G2 a G5 sin tocar el camino crÃ­tico; si el equipo se retrasa, P4/P6 se reasignan a apoyar C-07/C-09/C-10/C-11. (3) El vÃ­nculo `turno_id` de las evoluciones se guarda como columna nullable sin FK en C-05; C-07 agrega la FK en su migraciÃ³n solo si C-05 ya estÃ¡ aplicada (si no, la FK se difiere a la etapa posterior).

---

## FASE 1 â€” FundaciÃ³n, acceso y configuraciÃ³n (Ã‰picas 1 y 2)

> C-01 y C-02 son la cadena inicial indispensable. C-02 abre tres ramas simultÃ¡neas (C-03, C-04, C-06).

### [C-01] `foundation-setup`
- **Estado**: `[x]` completado (archivado 2026-11-19 en `openspec/changes/archive/2026-11-19-c-01-foundation-setup/`)
- **Scope**:
  - Proyecto Gradle `backend/` (Spring Boot, Web, Security, Data JPA, Validation, Mail, Flyway, PostgreSQL) con capas `api / application / domain / infrastructure` y `OdontoGestionApplication`
  - Proyecto `frontend/` (Vite + TypeScript vanilla, Vitest + jsdom, proxy `/api` â†’ `http://localhost:8080`) con `features/`, `shared/`, `pages/`, `main.ts`; tarea Gradle que copia el build de Vite a `backend/src/main/resources/static/`
  - Perfiles `dev` / `demo`, `application.yml` con variables de entorno de la KB, `.env.example` sin valores reales, `.gitignore`
  - MigraciÃ³n V1 Flyway: extensiones `btree_gist` y `citext`
  - Base de tests: JUnit 5 + Testcontainers PostgreSQL (con `btree_gist`), `Clock` inyectable, manejador global de errores (`ProblemDetail`)
  - Frontend shell: `shared/http` (lectura de `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN` en escrituras, `401` â†’ `/login`, errores `ProblemDetail`), `shared/router` (rutas privadas/pÃºblicas), estado de sesiÃ³n, layout base, pÃ¡gina `/login` y logout contra el contrato de Flujo 1 (mocks en tests)
  - Tests: arranque de contexto con Testcontainers, migraciÃ³n V1, cliente HTTP (CSRF, 401), guardia de rutas, render de login
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Estructura de directorios, Â§Capas del backend, Â§Variables de entorno, Â§Seguridad (CSRF)
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-02, Â§DD-04, Â§DD-12, Â§DD-15
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 1
  - `knowledge-base/01_vision_y_objetivos.md`

### [C-02] `core-auth-config`
- **Estado**: `[x]` completado (archivado 2026-11-20 en `openspec/changes/archive/2026-11-20-c-02-core-auth-config/`)
- **Scope**:
  - MigraciÃ³n V2: tablas `consultorio` y `usuario` (email `citext` Ãºnico, `password_hash`, `consultorio_id`, timestamps); modelos `Consultorio`, `Usuario`, base `AuditableEntity` (id BIGINT), repositorios; provider de `consultorio_id` Ãºnico (sin multi-tenant)
  - Seed de arranque (US-003): "Consultorio Demo" y odontÃ³logo ficticio con contraseÃ±a desde `BOOTSTRAP_ODONTOLOGO_PASSWORD` (BCrypt); sin endpoint de registro
  - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` con sesiÃ³n de servidor (`JSESSIONID` HttpOnly, SameSite=Lax), rotaciÃ³n de id al login, expiraciÃ³n por inactividad (`SESSION_TIMEOUT_MIN`), limitaciÃ³n de intentos (RN-AU-09), `401` genÃ©rico
  - CSRF con `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`), `403` sin token; `SecurityFilterChain`: `/api/v1/**` autenticado salvo `publico/**`, `acciones/**`, `avisos/**`; sin CORS
  - MigraciÃ³n V3: parÃ¡metros en `consultorio` (`granularidad_min`, `anticipacion_min_reserva_min`, `limite_cancelacion_horas`, `recordatorio_horas_antes`, `vencimiento_aviso_min`, `vencimiento_pendiente_min`, tolerancia de ausente) con defaults
  - `GET/PUT /api/v1/consultorio`, `GET/PUT /api/v1/perfil`, `POST /api/v1/perfil/password`; parÃ¡metros positivos (`422`), cambios aplican solo a nuevas operaciones
  - Tests: seed idempotente, email Ãºnico, BCrypt, login ok/invÃ¡lido, CSRF, expiraciÃ³n, rutas pÃºblicas vs privadas (MockMvc + Spring Security Test), limitaciÃ³n de intentos, defaults de parÃ¡metros, cambio de contraseÃ±a
- **Dependencias**: `C-01`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` Â§AutenticaciÃ³n (RN-AU-01â€¦09), Â§ConfiguraciÃ³n y agenda (RN-AG)
  - `knowledge-base/04_modelo_de_datos.md` Â§consultorio, Â§usuario, Â§Seed data inicial
  - `knowledge-base/03_actores_y_roles.md` Â§Modelo de acceso, Â§Rutas pÃºblicas
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 1
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 1 (US-001, US-002, US-003), Â§Ã‰pica 2 (US-010, US-013)
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-01, Â§DD-03

### [C-06] `agenda-configuracion`
- **Estado**: `[x]` completado (archivado 2026-11-21 en `openspec/changes/archive/2026-11-21-c-06-agenda-configuracion/`)
- **Scope**:
  - MigraciÃ³n V4: tablas `prestacion`, `horario_trabajo` (exclusiÃ³n de solapamiento por consultorio y dÃ­a), `bloqueo_agenda`; seed de las 6 prestaciones y horarios L-V 09-13 / 14-18
  - Modelos `Prestacion`, `HorarioTrabajo`, `BloqueoAgenda`; `/api/v1/prestaciones` (CRUD, desactivar sin perder historial), `/api/v1/horarios`, `/api/v1/bloqueos`
  - Reglas: duraciÃ³n mÃºltiplo de la granularidad (`422`), `hora_desde < hora_hasta`; el listado de turnos afectados por un bloqueo se completa en C-07
  - Frontend `config`: pantallas de consultorio y perfil (parÃ¡metros, cambio de contraseÃ±a), prestaciones, horarios semanales y bloqueos; validaciÃ³n de cliente y manejo de `422`
  - Tests: mÃºltiplo de granularidad, solapamiento de horarios, desactivaciÃ³n, bloqueos; Vitest de formularios, errores y llamadas HTTP con CSRF
- **Dependencias**: `C-02`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` Â§prestacion, Â§horario_trabajo, Â§bloqueo_agenda, Â§Seed data inicial
  - `knowledge-base/05_reglas_de_negocio.md` Â§ConfiguraciÃ³n y agenda (RN-AG-02, RN-AG-03, RN-AG-07, RN-AG-08)
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 2 (US-010â€¦US-013)
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 8
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Estructura de directorios (frontend)

---

## FASE 2 â€” Pacientes, infraestructura de notificaciones/tokens e historia clÃ­nica [stretch] (Ã‰picas 6-parcial y 8)

> C-03, C-04 y C-06 corren en paralelo tras C-02. C-05 (historia clÃ­nica, **stretch "si llega"**) arranca al cerrar C-04 y sigue en carril paralelo hasta el cierre.

### [C-03] `notificaciones-tokens-registro`
- **Estado**: `[x]` completado (archivado 2026-11-22 en `openspec/changes/archive/2026-11-22-c-03-notificaciones-tokens-registro/`)
- **Scope**:
  - MigraciÃ³n V6: tabla `notificacion` (outbox) con Ã­ndices Ãºnicos (`turno_id`, `tipo`) y (`aviso_id`) para idempotencia; puerto `NotificationChannel`, `EmailChannel` (Spring Mail, `MAIL_*`), adaptador falso en memoria para tests
  - `NotificationService.encolar(...)` + `procesarPendientes()` con 3 reintentos y espera creciente; el fallo de envÃ­o no revierte la operaciÃ³n origen; `@Scheduled` cada 5 min; plantillas por tipo (`CONFIRMAR_SOLICITUD`, `CONFIRMAR_LISTA_ESPERA`, `CONFIRMACION_TURNO`, `RECORDATORIO`, `AVISO_HUECO`, `CANCELACION`, `AUSENTE_REPROGRAMAR`) sin datos clÃ­nicos
  - MigraciÃ³n V7: tabla `token_acceso` (propÃ³sito, FK exclusiva a turno/aviso/entrada, `token_hash` SHA-256, `vence_en`, `usado_en`, constraint de exactamente una FK no nula); servicio de generaciÃ³n (128+ bits), hash y consumo atÃ³mico (`UPDATE â€¦ WHERE usado_en IS NULL AND vence_en > now()`), propÃ³sitos `CONFIRMAR_SOLICITUD | CONFIRMAR_TURNO | CANCELAR_TURNO | ACEPTAR_AVISO | CONFIRMAR_LISTA_ESPERA`; `GET` nunca consume, `POST` ejecuta; `410 Gone` sin enumeraciÃ³n
  - `GET /api/v1/notificaciones?estado=&tipo=` (estado, destinatario, error, intentos) y reintento acotado; feature de front `notificaciones` con estados `PENDIENTE | ENVIADA | FALLIDA`
  - Tests: contrato comÃºn del canal, reintentos, idempotencia, fallo sin rollback, GreenMail; unicidad/vencimiento/un solo uso de tokens (dos consumos concurrentes con Testcontainers), propÃ³sito incorrecto; listado filtrable y acceso solo con sesiÃ³n; Vitest del listado
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/12_notificaciones.md`
  - `knowledge-base/05_reglas_de_negocio.md` Â§Notificaciones (RN-NO), Â§Tokens de acceso del paciente (RN-TK-01â€¦05)
  - `knowledge-base/04_modelo_de_datos.md` Â§notificacion, Â§token_acceso
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Seguridad (Tokens de paciente), Â§Estrategia de concurrencia
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-07, Â§DD-08, Â§DD-13, Â§SU-10

### [C-04] `pacientes-core`
- **Estado**: `[x]` completado (archivado 2026-11-21 en `openspec/changes/archive/2026-11-21-c-04-pacientes-core/`)
- **Scope**:
  - MigraciÃ³n V8 (era V5; renumerada el 2026-11-22 porque C-03 entrÃ³ antes con V6/V7): tabla `paciente` (Ãºnico `consultorio_id + dni`, Ã­ndices por dni, email, apellido+nombre, `cantidad_ausencias`)
  - Modelo `Paciente` (sin cuenta); servicio `buscarOCrearPorDni`
  - `GET /api/v1/pacientes?dni=&q=`, `POST /api/v1/pacientes`, `PUT /api/v1/pacientes/{id}`
  - Tests: unicidad por DNI, bÃºsqueda, creaciÃ³n en el momento, filtrado por `consultorio_id`, `401` sin sesiÃ³n
- **Dependencias**: `C-02`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` Â§paciente
  - `knowledge-base/05_reglas_de_negocio.md` Â§Pacientes (RN-PA)
  - `knowledge-base/03_actores_y_roles.md`
  - `knowledge-base/10_preguntas_abiertas.md` Â§PA-03

### [C-05] `historia-clinica`  â€” STRETCH ("si llega")
- **Estado**: `[ ]` pendiente (no comprometido para el 25/11; si no se entrega se documenta como etapa posterior, DD-16)
- **Scope**:
  - STRETCH: no es obligatorio para la entrega y no bloquea ningÃºn otro change (ni C-12)
  - CARRIL PARALELO: no depende de la agenda; arranca al cerrar C-04
  - MigraciÃ³n V11 (era V8, que pasÃ³ a C-04; V10 es de C-10; si C-07/V9 ya estÃ¡ aplicada, C-05 crea tambiÃ©n la FK de `evolucion_clinica.turno_id`): tablas `historia_clinica` (Ãºnica por paciente, creaciÃ³n perezosa), `anamnesis`, `pieza_dental_estado` (Ãºnico `historia_id + pieza_fdi`) y `evolucion_clinica` (`turno_id` nullable sin FK; la FK la agrega C-07)
  - `GET /api/v1/pacientes/{id}/historia`, `PUT /api/v1/pacientes/{id}/historia/anamnesis`
  - `PUT /api/v1/pacientes/{id}/historia/odontograma/piezas/{fdi}` (estado + observaciÃ³n, autor y fecha), validaciÃ³n FDI (`422`), 32 piezas permanentes, enum de estados MVP
  - `POST/GET /api/v1/pacientes/{id}/historia/evoluciones` (orden cronolÃ³gico, ediciÃ³n por el autor dentro de ventana)
  - Frontend `historia-clinica`: ficha con anamnesis, odontograma FDI visual (32 piezas, estado/observaciÃ³n) y listado + alta de evoluciones; acceso desde la ficha del paciente (el acceso desde un turno de la agenda se integra en C-12, solo si C-05 estÃ¡ hecho)
  - Sin datos clÃ­nicos en logs, URLs ni emails; acceso exclusivo con sesiÃ³n del odontÃ³logo
  - Tests: una HC por paciente, `401` sin sesiÃ³n, sin datos clÃ­nicos en logs, validaciÃ³n FDI, unicidad por pieza, autorÃ­a, orden cronolÃ³gico; Vitest de odontograma, ediciÃ³n de pieza y alta de evoluciÃ³n
- **Dependencias**: `C-04` (y `C-02` transitivo)
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` Â§historia_clinica, Â§anamnesis, Â§pieza_dental_estado, Â§evolucion_clinica
  - `knowledge-base/05_reglas_de_negocio.md` Â§Historia clÃ­nica (RN-HC-01â€¦06), Â§Excepciones globales (RN-GL)
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 8 (US-070, US-071, US-072)
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-09, Â§SU-05
  - `knowledge-base/10_preguntas_abiertas.md` Â§IN-02, Â§IN-03, Â§PA-09

---

## FASE 3 â€” Agenda y turnos (Ã‰picas 3 y 7)

### [C-07] `turnos-agenda-core`
- **Estado**: `[x]` completado (archivado 2026-11-22 en `openspec/changes/archive/2026-11-22-c-07-turnos-agenda-core/`)
- **Scope**:
  - MigraciÃ³n V9: tabla `turno` con `EXCLUDE USING gist` (`consultorio_id`, `tstzrange(inicio, fin, '[)')`) para estados activos, `CHECK (inicio < fin)`, `@Version`, Ã­ndices; agrega FK de `evolucion_clinica.turno_id`
  - Modelo `Turno` y mÃ¡quina de estados (`PENDIENTE_CONFIRMACION, RESERVADO, CONFIRMADO, CANCELADO, AUSENTE, ATENDIDO, EXPIRADO`); resolver PA-06 sobre `ATENDIDO/AUSENTE` en la restricciÃ³n
  - Strategy `DisponibilidadService` (horarios âˆ’ bloqueos âˆ’ turnos activos, duraciÃ³n, granularidad, anticipaciÃ³n mÃ­nima), `GET /api/v1/agenda/disponibilidad`, `GET /api/v1/agenda?desde&hasta`, `POST /api/v1/turnos` (US-021) con `HuecoNoDisponible` â†’ `409` + alternativas; consulta de turnos afectados por un bloqueo (cierra US-012)
  - ExpiraciÃ³n de `PENDIENTE_CONFIRMACION` vencidas (invocable antes de cada reserva y por scheduler)
  - `PUT /api/v1/turnos/{id}/mover`, `POST /api/v1/turnos/{id}/cancelar` (motivo, `cancelado_por`), `POST /api/v1/turnos/{id}/atendido`, `GET /api/v1/turnos/hoy` (US-022, US-023, US-024)
  - Eventos `TurnoCreado`, `HuecoLiberado`, `TurnoMovido` (`@TransactionalEventListener`); notificaciones `CONFIRMACION_TURNO` y `CANCELACION` vÃ­a `NotificationService`
  - Tests: no superposiciÃ³n bajo concurrencia (Testcontainers), disponibilidad, transiciones invÃ¡lidas `422`, `409`, expiraciÃ³n con `Clock`, mover libera hueco y publica `HuecoLiberado`, notificaciÃ³n encolada, `@Version` concurrente
  - V9 agrega `fk_token_acceso_turno` (CASCADE) y `fk_notificacion_turno` (RESTRICT), diferidas desde C-03
- **Dependencias**: `C-03, C-04, C-06`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` Â§turno
  - `knowledge-base/05_reglas_de_negocio.md` Â§Turnos (RN-TU), Â§ConfiguraciÃ³n y agenda (RN-AG)
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 3 (US-021â€¦US-024)
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 3 (pasos 1-2), Â§Flujo 7
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Estrategia de concurrencia y agenda, Â§Estrategia de pruebas
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-05, Â§DD-06; `knowledge-base/10_preguntas_abiertas.md` Â§PA-06

### [C-08] `agenda-frontend-ausentismo`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `agenda`: vista dÃ­a/semana con colores por estado (incluye pendientes), creaciÃ³n de turno con buscador de paciente por DNI y alta rÃ¡pida, propuesta de horarios libres, manejo de `409`; acciones mover, cancelar con motivo, marcar atendido; lista "turnos de hoy"
  - `POST /api/v1/turnos/{id}/ausente`: solo tras inicio + tolerancia (`422` antes), estado `AUSENTE`, incrementa `paciente.cantidad_ausencias`, evento `TurnoAusente`, notificaciÃ³n `AUSENTE_REPROGRAMAR` con enlace a la pÃ¡gina pÃºblica
  - ReversiÃ³n de la marca dentro de 24 h (RN-AS-06) que ajusta el contador; `GET /api/v1/pacientes/{id}/ausencias` (US-061) y su visualizaciÃ³n en agenda y detalle del paciente
  - Tests: tolerancia con `Clock`, contador, reversiÃ³n, notificaciÃ³n encolada; Vitest de render de agenda, conflicto `409`, flujo de cancelaciÃ³n, marcar ausente
- **Dependencias**: `C-07` (el front puede arrancar contra el contrato)
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 3, Â§Ã‰pica 7 (US-060, US-061)
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 3, Â§Flujo 6, Â§Flujo 7
  - `knowledge-base/05_reglas_de_negocio.md` Â§Ausentismo (RN-AS)
  - `knowledge-base/10_preguntas_abiertas.md` Â§PA-05, Â§PA-08
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Estructura de directorios (frontend)

---

## FASE 4 â€” PÃ¡gina pÃºblica y acciones del paciente (Ã‰pica 4)

### [C-09] `publico-solicitud-acciones`
- **Estado**: `[x]` completado (archivado 2026-11-22 en `openspec/changes/archive/2026-11-22-c-09-publico-solicitud-acciones/`)
- **Scope**:
  - `GET /api/v1/publico/disponibilidad` (solo huecos libres) y `POST /api/v1/publico/solicitudes-turno` â†’ `202` genÃ©rico (sin enumerar DNI/email); transacciÃ³n: expirar pendientes, `buscarOCrearPorDni`, turno `PENDIENTE_CONFIRMACION` con `pendiente_hasta` (TTL 30 min), token `CONFIRMAR_SOLICITUD`, notificaciÃ³n
  - Anti-abuso: limitaciÃ³n de tasa por IP y por DNI/email (`PUBLIC_RATE_LIMIT_PER_HOUR`, `429`), campo seÃ±uelo, `409` con alternativas, `422` fuera de horario/bloqueo; scheduler de expiraciÃ³n a `EXPIRADO`
  - `GET /acciones/{token}` (resumen sin consumir) y `POST /acciones/{token}` (ejecuta): `CONFIRMAR_SOLICITUD` â†’ `RESERVADO` + `CONFIRMACION_TURNO` (US-031); `CONFIRMAR_TURNO` â†’ `CONFIRMADO`; `CANCELAR_TURNO` â†’ `CANCELADO` + `HuecoLiberado` respetando el lÃ­mite de horas (US-032); respuesta informativa para token vencido/usado (`410`)
  - Frontend: features `publico` (prestaciÃ³n, horario libre, formulario con campo seÃ±uelo, manejo de `409/422/429`) y `acciones-token` (pÃ¡ginas `/acciones/{token}` con confirmar/cancelar por POST e informativas), sin sesiÃ³n
  - Tests: solicitud feliz, hueco tomado `409`, rate limit, honeypot, no enumeraciÃ³n, expiraciÃ³n con `Clock`, solicitudes simultÃ¡neas del mismo horario, confirmar/cancelar dentro/fuera del lÃ­mite, token usado/vencido/propÃ³sito incorrecto, doble POST concurrente; Vitest del flujo pÃºblico y pÃ¡gina de token
- **Dependencias**: `C-07, C-03`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 2, Â§Flujo 5 (pasos 4-6)
  - `knowledge-base/05_reglas_de_negocio.md` Â§PÃ¡gina pÃºblica (RN-PU-01â€¦07), Â§Tokens (RN-TK), Â§Turnos (RN-TU-03)
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 4 (US-030, US-031, US-032)
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-14, Â§SU-09
  - `knowledge-base/10_preguntas_abiertas.md` Â§PA-02, Â§PA-03; `knowledge-base/03_actores_y_roles.md` Â§Rutas pÃºblicas

---

## FASE 5 â€” Lista de espera y recordatorios (Ã‰picas 5 y 6)

> C-10 y C-11 son independientes entre sÃ­; C-11 puede hacerse mientras C-09/C-10 avanzan.

### [C-10] `lista-espera`
- **Estado**: `[x]` completado (archivado 2026-11-23 en `openspec/changes/archive/2026-11-23-c-10-lista-espera/`; alcance mÃ­nimo, ver proposal: franja horaria, filtros, alta manual y vencimiento de entradas quedan como etapa posterior)
- **Scope**:
  - MigraciÃ³n V10: tabla `entrada_lista_espera` (estados `PENDIENTE_CONFIRMACION | ACTIVA | CUBIERTA | CANCELADA | VENCIDA`, Ãºnico activo/pendiente por paciente+prestaciÃ³n, `@Version`) y `aviso_hueco` (`ENVIADO | ACEPTADO | EXPIRADO | CERRADO`, `vence_en`, `@Version`)
  - `POST /api/v1/publico/lista-espera` (nace pendiente, token `CONFIRMAR_LISTA_ESPERA`) y confirmaciÃ³n vÃ­a `/acciones/{token}` â†’ `ACTIVA` (US-040); `GET/POST/DELETE /api/v1/lista-espera` para el odontÃ³logo (US-042); Strategy de compatibilidad (prestaciÃ³n, rango de fechas, franja) y vencimiento
  - Manejador de `HuecoLiberado`: busca entradas `ACTIVA` compatibles, crea aviso + token `ACEPTAR_AVISO` por paciente y encola `AVISO_HUECO` a todos a la vez
  - `GET /avisos/{token}` y `POST /avisos/{token}/aceptar`: el primero inserta el turno `RESERVADO` (origen `LISTA_ESPERA`), entrada a `CUBIERTA`, demÃ¡s avisos `CERRADO`, confirmaciÃ³n al ganador, el resto `409`; vencimiento de avisos por scheduler; `410 Gone`
  - Frontend: formulario pÃºblico de lista de espera, pÃ¡gina `/avisos/{token}` (aceptar, "ya no disponible", vencido) y pantalla del odontÃ³logo (listado filtrable, alta y baja)
  - Tests: compatibilidad, duplicado `409`, N avisos para N compatibles, dos aceptaciones simultÃ¡neas (solo una gana), vencimiento, hueco ocupado por reserva normal; Vitest de formulario, aceptaciÃ³n y conflicto
  - agrega las FK de `aviso_id` y `entrada_lista_espera_id` en `token_acceso` y `notificacion`, diferidas desde C-03
- **Dependencias**: `C-09`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 3, Â§Flujo 4
  - `knowledge-base/05_reglas_de_negocio.md` Â§Lista de espera (RN-LE-01â€¦11), Â§PÃ¡gina pÃºblica (RN-PU-05)
  - `knowledge-base/04_modelo_de_datos.md` Â§entrada_lista_espera, Â§aviso_hueco
  - `knowledge-base/06_funcionalidades.md` Â§Ã‰pica 5 (US-040, US-042)
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-06; `knowledge-base/12_notificaciones.md` Â§AVISO_HUECO

### [C-11] `recordatorios-scheduler`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Job `@Scheduled` (cada 5 min) que busca turnos `RESERVADO` en la ventana "ahora + `recordatorio_horas_antes`" sin recordatorio previo
  - Por turno: tokens `CONFIRMAR_TURNO` y `CANCELAR_TURNO` Ãºnicos, notificaciÃ³n `RECORDATORIO` con enlaces, sin datos clÃ­nicos
  - Idempotencia por Ã­ndice Ãºnico (`turno_id`, `tipo`); retoma pendientes tras reinicio
  - Tests: una sola ejecuciÃ³n efectiva ante dos corridas, ventana con `Clock` inyectable, reinicio, email sin datos clÃ­nicos
- **Dependencias**: `C-07, C-03`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 5
  - `knowledge-base/05_reglas_de_negocio.md` Â§Notificaciones (RN-NO-02, RN-NO-03, RN-NO-04, RN-NO-05, RN-NO-09)
  - `knowledge-base/12_notificaciones.md` Â§ProgramaciÃ³n
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-13

---

## FASE 6 â€” Cierre y demo local

### [C-12] `demo-hardening`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Build de Vite copiado a `static/` y arranque desde un solo proceso (`http://localhost:8080`) con perfil `demo`; SPA fallback
  - Seed de demo: 10 pacientes ficticios y datos de ejemplo; SMTP real por variables de entorno (verificaciÃ³n de envÃ­o y lÃ­mite diario)
  - IntegraciÃ³n final: ausencias en la ficha del paciente; **condicional a C-05** (stretch): acceso a la historia clÃ­nica desde un turno de la agenda solo si C-05 estÃ¡ cerrado, sin bloquear el change
  - VerificaciÃ³n de los flujos 2, 3 y 5 de punta a punta (Playwright opcional); revisiÃ³n de que no hay secretos versionados ni datos clÃ­nicos en logs/emails
  - GuÃ­a de ejecuciÃ³n local y de pruebas (Docker para Testcontainers, PostgreSQL con `btree_gist`)
  - Tests: arranque con perfil demo, smoke E2E de solicitud â†’ confirmaciÃ³n â†’ recordatorio
- **Dependencias**: `C-01, C-02, C-03, C-04, C-06, C-07, C-08, C-09, C-10, C-11` (C-05 es dependencia blanda/opcional: no bloquea)
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` Â§Despliegue, Â§Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-11, Â§SU-10, Â§SU-11
  - `knowledge-base/07_flujos_principales.md` Â§Flujo 2, Â§Flujo 3, Â§Flujo 5
  - `knowledge-base/10_preguntas_abiertas.md` Â§PA-07; `knowledge-base/09_decisiones_y_supuestos.md` Â§DD-16 (corte por valor)

---

## Tabla resumen

| ID | Change | Fase | Dependencias | Governance | Carril principal |
|----|--------|------|--------------|------------|------------------|
| C-01 | foundation-setup | 1 | â€” | BAJO | P1, P2, P5 |
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
| C-12 | demo-hardening | 6 | C-01 â€¦ C-04, C-06 â€¦ C-11 (C-05 opcional) | MEDIO | Todos |

**Primer change recomendado**: `C-01` (foundation-setup). Para arrancar: `/opsx:propose C-01-foundation-setup`
