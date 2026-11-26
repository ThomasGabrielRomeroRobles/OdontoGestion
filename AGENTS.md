# OdontoGestión — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.
> Setup de máquina, reparto P1–P6 y flujo de ramas/PRs: [CONTRIBUTING.md](CONTRIBUTING.md).

OdontoGestión: aplicación web de turnos e historia clínica para un consultorio con **un único odontólogo** (único usuario con login). Los pacientes no tienen cuenta: piden turno por una página pública y actúan por enlaces de email con token de un solo uso. Ataca el ausentismo y los huecos de agenda (lista de espera + recordatorios). Trabajo académico, equipo de 6, entrega 2026-11-25.

---

## Protocolo de inicio: guía de entrada por integrante (OBLIGATORIO)

Al empezar cada sesión de trabajo, **antes de proponer, implementar o revisar nada**, el agente ejecuta este protocolo. El objetivo es que la persona sepa en qué posición está, qué tiene que hacer, por qué y cómo.

### Paso 1 — Identificar a la persona

Si en la sesión todavía no se sabe quién está trabajando, el agente **pregunta** (con `AskUserQuestion` si está disponible): "¿Quién está trabajando ahora?". Las opciones salen de la tabla de integrantes de [CONTRIBUTING.md](CONTRIBUTING.md) §4 (nombre + carril P1–P6). No adivines por el usuario de git ni por el nombre de la rama: preguntá. Si la persona pide algo puntual que no es un change (una duda, un fix chico), igual identificala, pero no hace falta la guía completa.

### Paso 2 — Ubicar su bloque de trabajo

Con el carril de la persona:

1. Leé en [CHANGES.md](CHANGES.md) el "Plan de trabajo para 6 personas" y el **Estado** (`[x]` / `[ ]`) de cada change.
2. Mirá el estado real: `git fetch` y qué hay en `origin/main`, ramas `feat/c-NN-*` existentes, `openspec list --json` (changes abiertos) y `openspec/changes/archive/` (changes cerrados).
3. Determiná **el siguiente bloque que le toca**: el primer change (o parte de change) asignado a su carril que no esté cerrado. Si ya hay un change suyo abierto, ese es el bloque (retomar, no empezar otro).
4. Si comparte el change con otra persona (ej. C-02 = P1 + P2), identificá qué parte le toca a cada uno según el plan y quién es su par.

### Paso 3 — Verificar que todo lo previo esté vigente

Para el bloque detectado, revisá **cada dependencia** listada en su bloque de `CHANGES.md`:

| Qué verificar | Cómo |
|---|---|
| La dependencia está terminada | Marcada `[x]` en `CHANGES.md` **y** archivada en `openspec/changes/archive/` |
| La dependencia está integrada | Su código está en `origin/main` (no solo en la rama de otro) |
| Las specs que necesita existen | Las capabilities correspondientes están en `openspec/specs/` |
| El entorno de la persona está listo | Docker responde, existen las skills del stack y `.atl/skill-registry.md` (ver CONTRIBUTING §1–3) |
| No hay trabajo pisado | Nadie más tiene abierto el mismo change (`openspec list`, ramas remotas) |

**Si falta algo, el agente NO arranca el change.** Avisa, con este formato:

> ⛔ Todavía no podés empezar **C-NN `nombre`**. Falta: **C-MM `nombre`** (a cargo de **Px — Nombre**), estado: _no empezado / en curso en la rama X / terminado pero sin mergear_.
> Mientras tanto podés: _lo que el plan de CHANGES.md indica para tu carril en esta etapa (proponer, preparar fixtures, avanzar contra mocks, ayudar en el bloqueante)_.

Si lo que falta es del entorno (Docker, skills, registry), indicá el paso exacto de CONTRIBUTING.md para resolverlo.

### Paso 4 — Dar la guía de entrada

Si todo lo previo está vigente, el agente da una guía breve y concreta (en español, sin copiar la KB entera; enlazando los archivos):

1. **Dónde estás parado**: etapa (gate) actual del plan, qué está terminado, qué está en curso y por quién, si tu change está en el camino crítico y qué changes dependen de vos.
2. **El sistema en dos párrafos**: qué es OdontoGestión, el problema (ausentismo y huecos) y cómo encaja tu bloque en esa solución.
3. **Tu bloque**: qué hace el change, **por qué existe** (qué historia de usuario, regla de negocio `RN-XX` y decisión `DD-XX` lo motivan) y **para qué sirve** (qué habilita después y para quién).
4. **Cómo hacerlo**: el flujo OPSX (`/opsx:propose c-NN-nombre` → revisión → `/opsx:apply` → tests → `/opsx:archive` → `[x]` en CHANGES.md → PR), la rama a usar (`feat/c-NN-nombre`), el nivel de **Governance** y qué implica (CRITICO/ALTO: plan aprobado por una persona antes de escribir código), y que se trabaja con **TDD estricto** (CONTRIBUTING §3.3).
5. **Qué leer antes**: los archivos de "Leer antes" del bloque en CHANGES.md, con el motivo de cada uno.
6. **Qué revisar / cuidar**: las reglas duras de este archivo que más aplican a ese bloque, contratos que tiene que respetar (endpoints, migraciones con el próximo número Flyway libre, eventos), y con quién coordinar (par del mismo change, quien consume su API).
7. **Cuándo está terminado**: escenarios de la spec cubiertos por tests, todos los tests del repo en verde, change archivado, `[x]` en CHANGES.md y PR revisado por otra persona.

Cerrá preguntando si arranca con el `/opsx:propose` (o con lo que corresponda) y **esperá la confirmación** antes de hacer cambios.

---

## Stack Tecnológico

| Capa | Tecnología | Versión mínima |
|------|------------|----------------|
| Frontend | TypeScript "vanilla" (sin framework de UI), HTML + CSS | TypeScript 5.x (suposición) |
| Bundler / dev server | Vite (solo desarrollo, proxy a la API) | Vite 5.x (suposición) |
| Servido del frontend | Producción/demo: estáticos servidos por Spring Boot (build de Vite copiado a `static/`) | DD-12 |
| Backend | Spring Boot, API REST | Spring Boot 3.x, Java 21 (suposición) |
| Build | Gradle | Gradle 8.x (suposición) |
| Persistencia | Hibernate / JPA (Spring Data JPA) | — |
| Base de datos | PostgreSQL local | 15+ (extensión `btree_gist`) |
| Migraciones | Flyway | DD-04 |
| Seguridad | Spring Security, autenticación por sesión (cookie) + CSRF | DD-03 |
| Email | Spring Mail (SMTP) detrás de un puerto; cuenta SMTP real | KB 12 |
| Scheduler | Spring `@Scheduled` en el mismo proceso, sin colas | DD-13 |
| Tests backend | JUnit 5, Mockito, Testcontainers (PostgreSQL), TDD estricto | DD-15 |
| Tests frontend | Vitest | Compatible con Vite (suposición) |
| Hosting | Ninguno en el MVP: demo local | DD-11 |

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/` (índice y resumen ejecutivo: [README.md](knowledge-base/README.md)). **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Propósito, alcance del MVP, fuera de alcance |
| [02_descripcion_general.md](knowledge-base/02_descripcion_general.md) | Stack, arquitectura, integraciones, API REST |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, permisos, rutas públicas y por token |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, constraint de agenda, seed ficticio |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas (RN-XX) |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario por épica |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos E2E |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Patrones, estructura, concurrencia, env vars |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones DD-XX y supuestos SU-XX |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | Inconsistencias y preguntas abiertas (IN-01, PA-01 y PA-02 resueltas el 2026-11-19) |
| [11_contexto_de_mercado.md](knowledge-base/11_contexto_de_mercado.md) | Contexto de producto y marco legal |
| [12_notificaciones.md](knowledge-base/12_notificaciones.md) | Email en MVP, puerto `NotificationChannel` |

Insumo de discovery: [discovery/reports/Sistemas odontológicos de turnos.md](discovery/reports/Sistemas%20odontol%C3%B3gicos%20de%20turnos.md) (informe de mercado) y `discovery/research_notes/`.

> ✅ Decisiones del equipo (2026-11-19) que resuelven las preguntas de prioridad **Alta** de `10_preguntas_abiertas.md`:
> - **PA-01 / IN-01 (resuelto, DD-16)**: **corte por valor**. Obligatorio para el 25/11: los 11 changes core (C-01, C-02, C-03, C-04, C-06 a C-12). **`C-05` historia clínica es stretch ("si llega")**: si no se entrega queda como etapa posterior; C-12 no depende de C-05.
> - **PA-02 (resuelto, DD-14)**: **anti-abuso confirmado**: `PENDIENTE_CONFIRMACION` con hueco retenido 30 min, confirmación por token de un solo uso enviado por email, 5 solicitudes/h por IP y máx. 2 pendientes por DNI/email (429), honeypot, validación estricta y respuestas sin enumeración. CAPTCHA/OTP queda como mejora posterior (US-113).
> - Inconsistencias a tener presentes: IN-02 (HC editable vs. Ley 26.529) e IN-03 (identidad sin login vs. Ley 25.326) se mitigan usando **solo datos ficticios**.

---

## Skills Disponibles

Fuente: `.atl/skill-registry.md`. Cargá la skill correspondiente al contexto ANTES de escribir código.

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend Core** | Spring Boot / JPA / agenda / tokens / migraciones | `java-springboot`, `jpa-patterns`, `spring-data-jpa`, `postgresql-table-design`, `database-migrations`, `database-migration` |
| **Backend Seguridad** | Spring Security, sesión + CSRF, rate limiting, secretos | `springboot-security` |
| **Testing** (todo el backend) | TDD, slices, Testcontainers | `springboot-tdd`, `spring-boot-test-patterns` |
| **Frontend** | TypeScript vanilla + Vite | `typescript-advanced-types` |
| **Orquestación** | OPSX / SDD / documentación | `openspec-*` (explore, propose, apply, archive), `kb-creator`, `roadmap-generator`, `agent-instruction`, `skill-registry` |

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry`; no versionado — no está en el repo).

---

## Roadmap de Changes

El plan completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 12 changes en 6 fases (F1 fundación/acceso/config · F2 pacientes, notificaciones/tokens, historia clínica · F3 agenda y turnos · F4 página pública y acciones por token · F5 lista de espera y recordatorios · F6 cierre y demo local).
- **Camino crítico** (7): `C-01 → C-02 → C-06 → C-07 → C-09 → C-10 → C-12`.
- **Corte por valor (DD-16)**: obligatorios 11 changes (todos salvo C-05).
- **Carril paralelo (stretch, "si llega")**: historia clínica (`C-05`) arranca al cerrar `C-04` y no bloquea la agenda ni C-12; si no se entrega, queda como etapa posterior.
- **Post-entrega**: `C-13` (frontend visual + pantalla de pacientes).
- **Primer change**: `C-01` (`foundation-setup`, governance BAJO).
- **Equipo de 6 por carriles**: P1 backend crítico (agenda/público) · P2 backend crítico 2 (seguridad, concurrencia, acciones) · P3 backend infra (notificaciones, tokens, schedulers) · P4 backend pacientes + HC · P5 frontend A (shell, config, agenda, público) · P6 frontend B (registro de envíos, HC, lista de espera).
- Cada change backend + frontend: el backend publica primero el contrato y el frontend avanza contra mocks; ambos cierran juntos.

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), identificá dependencias del change y los archivos de "Leer antes". Respetá el nivel de Governance indicado en cada change.

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en `~/.claude/CLAUDE.md` (orquestador, governance, TDD, engram): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** + las universales que el global no cubra.

Son contrato; romperlas es un defecto.

### Backend
1. NUNCA `@Autowired` en campos → inyección por constructor (campos `final`).
2. NUNCA exponer entidades JPA en la API REST → DTOs/records de request y response.
3. Capas `controller → service → repository`: la lógica de negocio y `@Transactional` viven solo en services.
4. Errores con `ProblemDetail` y códigos coherentes (400/403/404/409/422); **409** en conflicto de agenda.

### Datos
5. NUNCA `ddl-auto=update/create` → todo cambio de esquema por migración Flyway; `ddl-auto=validate`.
6. Toda entidad de negocio referencia a `Consultorio` (`consultorio_id`) desde el día 1.
7. Fechas en UTC (`timestamptz`); se muestran en `America/Argentina/Buenos_Aires`.
8. La no superposición de turnos y el "primero gana" se garantizan con restricción en la base (`EXCLUDE USING gist`), NUNCA solo con un chequeo en Java.

### Seguridad y tests
9. NUNCA H2 ni mocks de la base en tests de integración → PostgreSQL con Testcontainers.
10. NUNCA loguear contraseñas, tokens ni datos clínicos; tampoco incluirlos en URLs (salvo el token opaco del link) ni en emails.
11. NUNCA commitear credenciales (SMTP, DB) → variables de entorno; `.env` en `.gitignore`.
12. NUNCA cargar datos reales de pacientes en seed, tests o demo → solo datos ficticios.

### Frontend
13. `tsconfig` strict; prohibido `any` → tipos explícitos o `unknown` con narrowing.
14. NUNCA agregar React/Vue/etc. ni librerías de UI → TypeScript vanilla + módulos propios.

### Git
15. Conventional commits (`feat:`/`fix:`/`test:`/`docs:`/`chore:`) con referencia al change, ej. `feat(C-07): ...`.
16. NUNCA commitear ni pushear sin que el usuario lo pida explícitamente.

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)        → entender el dominio
2. Identificar el change en CHANGES.md           → respetar dependencias y governance
3. /opsx:propose C-NN-nombre                     → proposal + design + specs + tasks
4. /opsx:apply (cargando skills del rol)         → respetando las reglas duras
5. /opsx:archive C-NN-nombre + marcar [x]        → cerrar el change y tildarlo en CHANGES.md
```

El TDD estricto (RED → GREEN → TRIANGULATE → REFACTOR) se **hereda del `~/.claude/CLAUDE.md` global**; no se redefine acá. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.
