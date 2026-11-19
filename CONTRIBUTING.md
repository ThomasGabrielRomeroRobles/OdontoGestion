# Guía para el equipo — OdontoGestión

Cómo ponerse a trabajar en el repo: qué instalar, cómo dejar listo a Claude Code, cómo se reparte el trabajo y cómo se integra a `main`.

> Leé también [CLAUDE.md](CLAUDE.md) (reglas duras del proyecto) y [CHANGES.md](CHANGES.md) (roadmap y reparto). Esta guía no los repite: explica cómo arrancar.

---

## 1. Instalar en tu máquina

| Herramienta | Versión | Para qué |
|---|---|---|
| Git | cualquiera reciente | Clonar y trabajar en ramas |
| Java (JDK) | 21 | Backend (Gradle se descarga solo con `gradlew`) |
| Node.js | 20 o superior | Frontend (Vite, Vitest) |
| Docker Desktop | reciente, abierto mientras corrés tests | Testcontainers levanta PostgreSQL en los tests |
| PostgreSQL | 15 o superior | Solo para correr la app en local (`dev`/`demo`), no para los tests |
| Claude Code | reciente | Agente que ejecuta el flujo OPSX |
| OpenSpec CLI | 2.x | `npm install -g @fission-ai/openspec` |

Verificá Docker antes de empezar:

```powershell
docker run --rm hello-world
```

## 2. Clonar el repo (fuera de OneDrive)

**No clones dentro de OneDrive, Dropbox ni Google Drive**: la sincronización bloquea archivos y Gradle falla al limpiar `build/` (`Unable to delete directory`).

```powershell
cd C:\dev
git clone https://github.com/ThomasGabrielRomeroRobles/OdontoGestion.git
cd OdontoGestion
```

Comprobá que todo compila y los tests pasan (con Docker abierto):

```powershell
cd backend; .\gradlew.bat test
```

```powershell
cd frontend; npm ci; npm test
```

Para correr la app en local ver [backend/README.md](backend/README.md) y [frontend/README.md](frontend/README.md). Las variables están en [.env.example](.env.example); Spring **no** lee `.env`, hay que exportarlas en la shell o en la run configuration del IDE. Nunca subas un `.env` con valores reales.

## 3. Dejar listo a Claude Code

El repo ya trae las skills de OpenSpec (`.claude/skills/openspec-*`) y las reglas del proyecto (`CLAUDE.md`). Falta lo que vive **fuera** del repo:

### 3.1 Skills del stack (globales, una vez por máquina)

```powershell
npx skills add github/awesome-copilot --skill java-springboot -g
```

```powershell
npx skills add affaan-m/ecc --skill springboot-tdd -g
```

```powershell
npx skills add affaan-m/ecc --skill springboot-security -g
```

```powershell
npx skills add affaan-m/ecc --skill jpa-patterns -g
```

```powershell
npx skills add affaan-m/ecc --skill database-migrations -g
```

```powershell
npx skills add giuseppe-trisciuoglio/developer-kit --skill spring-boot-test-patterns -g
```

```powershell
npx skills add giuseppe-trisciuoglio/developer-kit --skill spring-data-jpa -g
```

```powershell
npx skills add wshobson/agents --skill postgresql-table-design -g
```

```powershell
npx skills add wshobson/agents --skill database-migration -g
```

```powershell
npx skills add wshobson/agents --skill typescript-advanced-types -g
```

### 3.2 Registro de skills (local, no se versiona)

`.atl/skill-registry.md` está en `.gitignore`: cada uno lo genera. Con las skills instaladas, en Claude Code dentro del repo pedí:

> actualizá el skill registry

(usa la skill `skill-registry`). Si no tenés esa skill, instalala con `npx skills add JuanCruzRobledo/skill-registry -g`.

### 3.3 Reglas de trabajo que el `CLAUDE.md` del proyecto da por heredadas

El `CLAUDE.md` del proyecto dice que TDD, governance y orquestación "se heredan del global". Ese global está en la máquina de quien armó la fundación, **no en la tuya**. Hasta que el equipo comparta uno, seguí estas reglas a mano (y pedíselas explícitamente a tu agente):

- **TDD estricto**: para cada tarea con comportamiento, primero el test que falla (ejecutado y visto en rojo), después el código mínimo para que pase, después al menos un segundo caso, después refactor con tests en verde. Nada de asserts triviales.
- **Governance por change** (columna "Governance" en `CHANGES.md`):
  - **CRITICO** (C-02, C-03, C-05, C-07, C-09): el agente solo analiza y propone; **no se escribe código sin que una persona apruebe el plan** (`design.md` + `tasks.md`).
  - **ALTO** (C-10, C-11): proponer y esperar revisión antes de escribir.
  - **MEDIO**: implementar por pasos, mostrando las decisiones no obvias.
  - **BAJO**: autonomía si los tests pasan.
- **No commitear ni pushear sin que lo pidas vos** (regla dura 16).

## 4. Quién hace qué

El reparto completo por persona y fecha está en [CHANGES.md](CHANGES.md) §"Plan de trabajo para 6 personas". Resumen de carriles:

| Persona | Carril |
|---|---|
| **P1** | Backend crítico: modelos, agenda, página pública, lista de espera |
| **P2** | Backend crítico 2: seguridad (sesión, CSRF), concurrencia, acciones por token |
| **P3** | Backend infra: notificaciones (outbox), tokens, recordatorios (`@Scheduled`) |
| **P4** | Backend pacientes + historia clínica (C-05 es "si llega") |
| **P5** | Frontend A: shell, configuración, agenda, página pública |
| **P6** | Frontend B: registro de envíos, historia clínica, lista de espera |

**Asignen quién es P1…P6 y anótenlo acá:**

| Persona | Integrante |
|---|---|
| P1 | _Thomas Romero_ |
| P2 | _Martina Caparroz_ |
| P3 | _Jeremias Gutierrez_ |
| P4 | _Mauro Mercado_ |
| P5 | _Juan Ignacio Previtera_ |
| P6 | _Martin Quiroga_ |

### Ahora mismo (C-01 ya está en `main`)

- **C-02 `core-auth-config` es el cuello de botella**: C-03, C-04 y C-06 dependen de él. Lo hacen **P1 (modelos, seed, migración V2) + P2 (auth por sesión, CSRF, límite de intentos, API de configuración)**. Es CRITICO: proponen juntos, revisan el plan juntos, después implementan.
- Mientras C-02 no esté mergeado, **P3–P6 solo proponen y avanzan contra mocks**, sin aplicar contra la base:
  - P3: puerto `NotificationChannel`, plantillas de email, GreenMail (sin DB).
  - P4: `/opsx:propose` de C-04 (y C-05), fixtures ficticios de pacientes.
  - P5: frontend de C-06 contra mocks.
  - P6: `/opsx:propose` de C-03 y frontend del registro de envíos contra mocks.

## 5. Flujo de un change (OPSX)

```
1. Leer en CHANGES.md el bloque del change: scope, dependencias, governance, "Leer antes"
2. Leer los archivos de knowledge-base/ indicados en "Leer antes"
3. /opsx:propose c-NN-nombre      → proposal + design + specs + tasks (nombre en minúsculas)
4. Si es CRITICO/ALTO: revisión humana del plan antes de seguir
5. /opsx:apply c-NN-nombre        → implementación con TDD estricto
6. Correr TODOS los tests (backend y frontend), no solo los del change
7. /opsx:archive c-NN-nombre      → sincroniza specs y archiva
8. Marcar [x] el change en CHANGES.md
9. PR a main
```

Antes de proponer, revisá con `openspec list` que nadie tenga abierto el mismo change.

### Al abrir Claude Code: la guía de entrada

No hace falta que sepas de memoria en qué bloque estás. Al empezar la sesión, el agente sigue el **Protocolo de inicio** de [CLAUDE.md](CLAUDE.md):

1. Te pregunta **quién sos** (tu nombre de la tabla de arriba).
2. Busca **el bloque que te toca** según tu carril y el estado real del repo (CHANGES.md, `origin/main`, changes abiertos y archivados).
3. Verifica que **todo lo previo esté listo**. Si falta algo, te dice qué bloque falta, quién lo tiene y qué podés hacer mientras tanto.
4. Si está todo, te da la **guía de entrada**: dónde estás parado en el plan, para qué sirve tu bloque y por qué existe, qué leer, cómo hacerlo, qué cuidar y cuándo está terminado.

Respondé con tu nombre y confirmá antes de que arranque.

## 6. Git: ramas y PRs

### La idea

`main` es la versión "buena" que comparte todo el equipo. **Nadie trabaja directo en `main`**: cada change se hace en su propia rama, y cuando está listo se abre un **Pull Request (PR)** en GitHub. Otra persona lo revisa y recién ahí se mergea a `main`. Si alguien rompe algo, lo rompe en su rama, no en el trabajo de los otros cinco.

```
main  ──●────────────────●────────────●──
         \              ↗              ↗
          feat/c-02 ──●──●  (PR)       │
           \                           │
            feat/c-04 ──●──●──●  (PR) ─┘
```

### Paso a paso (ejemplo: P4 con el C-04)

1. **Partir de `main` actualizado**:

   ```powershell
   git checkout main; git pull
   ```

2. **Crear la rama del change** (`feat/c-NN-nombre`, igual que el change en CHANGES.md):

   ```powershell
   git checkout -b feat/c-04-pacientes-core
   ```

3. **Trabajar en la rama** (`/opsx:propose`, `/opsx:apply`, tests) y commitear cuando quieras:

   ```powershell
   git add -A; git commit -m "feat(C-04): alta de pacientes"
   ```

4. **Subir la rama** (no `main`):

   ```powershell
   git push -u origin feat/c-04-pacientes-core
   ```

   Las siguientes veces alcanza con `git push`.

5. **Antes de abrir el PR, traer lo último de `main`** a tu rama, correr **todos** los tests y resolver conflictos si los hay:

   ```powershell
   git pull origin main
   ```

6. **Abrir el PR en GitHub**: al pushear aparece el botón **"Compare & pull request"** (o pestaña *Pull requests → New pull request*, base `main` ← tu rama). En el PR:
   - Título con el change: `feat(C-04): pacientes-core`.
   - Descripción: qué escenarios de la spec cubre y la salida de los tests.
   - **Reviewer**: al menos otra persona (en CRITICO, idealmente alguien del mismo carril o tu par).
7. **El revisor** lee los cambios en la pestaña *Files changed*, comenta si algo no está bien y, cuando está ok, toca **Approve**. Si pidió cambios, los hacés en la misma rama, pusheás y el PR se actualiza solo.
8. **Merge**: con la aprobación, cualquiera de los dos toca **"Merge pull request"**. Recién ahí el change entra a `main`. Después se puede borrar la rama desde el mismo PR.
9. **El resto del equipo actualiza**: en `main`, `git pull`; si siguen trabajando en su rama, `git pull origin main` para traer lo nuevo.

### Si dos personas comparten un change (ej. P1 + P2 en C-02)

- Usan **una sola rama**, `feat/c-02-core-auth-config`. Uno la crea y la pushea; el otro la baja:

  ```powershell
  git fetch; git checkout feat/c-02-core-auth-config
  ```

- **Siempre `git pull` en la rama antes de empezar a trabajar** y `git push` al terminar cada tarea, para no divergir.
- Se reparten tareas del `tasks.md` que toquen **archivos distintos**.
- Al final abren **un solo PR** del change.

### Reglas

- **Nunca pushear directo a `main`.** Todo entra por PR (la rama está protegida en GitHub: ver más abajo).
- **Una rama por change**: `feat/c-02-core-auth-config`, `feat/c-06-agenda-configuracion`, etc.
- **Commits**: conventional commits con el change, ej. `feat(C-02): auth por sesión con CSRF`, `test(C-07): conflicto de superposición devuelve 409`.
- **Migraciones Flyway**: cada change toma el siguiente número libre (`V2`, `V3`, …). Si dos ramas crean el mismo número, el segundo en mergear renumera la suya. Nunca edites una migración ya mergeada.

### Protección de `main` en GitHub

`main` tiene activada la regla **"Require a pull request before merging"** con **1 aprobación**. Significa:

- `git push origin main` **es rechazado** por GitHub: no se puede subir código a `main` sin pasar por un PR, ni siquiera por error.
- Un PR **no se puede mergear** hasta que otra persona lo apruebe. Quien abre el PR no puede aprobarse a sí mismo.
- Si te aparece *"protected branch"* o *"push declined"* al pushear, estás en `main`: creá tu rama (`git checkout -b feat/c-NN-nombre`), pusheá esa y abrí el PR.

Para pushear ramas tenés que ser **colaborador** del repo (te invita el dueño desde *Settings → Collaborators*; aceptá la invitación que llega por email).

## 7. Problemas conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| `Unable to delete directory` / `Failed to clean up stale outputs` en Gradle | Repo dentro de OneDrive | Clonar fuera de OneDrive; como parche, borrar `backend/build` y reintentar |
| Tests de integración fallan con error de Docker | Docker Desktop cerrado | Abrir Docker Desktop y reintentar |
| La app no arranca: falta `DB_PASSWORD` | No tiene valor por defecto | Exportar las variables de `.env.example` |
| `401` en `/api/v1/auth/me` al abrir el front | Esperado hasta que C-02 agregue los endpoints de auth | — |
| `gradlew` falla en Linux/macOS con `bad interpreter` | Saltos de línea CRLF | `git config core.autocrlf input` y volver a clonar |
