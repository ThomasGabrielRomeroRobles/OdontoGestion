# OdontoGestión

Turnos e historia clínica para un consultorio con un único odontólogo. Los pacientes no tienen cuenta: piden turno
por la página pública y actúan con enlaces de email de un solo uso. Contexto, alcance y reglas: [knowledge-base/](knowledge-base/README.md).
Plan de changes: [CHANGES.md](CHANGES.md). Cómo trabajar en el repo: [CONTRIBUTING.md](CONTRIBUTING.md).

Esta guía explica cómo **levantar la demo local** y cómo **correr los tests**. Detalle de cada parte:
[backend/README.md](backend/README.md) y [frontend/README.md](frontend/README.md).

> Solo datos ficticios: nunca cargar datos reales de pacientes ni versionar contraseñas (reglas duras 11 y 12).

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Java (JDK) | 21 | Backend (Gradle usa el wrapper incluido) |
| Node.js + npm | LTS (20 o 22) | Build del frontend y sus tests |
| PostgreSQL | 15 o superior, con la extensión `btree_gist` | Base de la demo. `btree_gist` y `citext` las crea la migración V1 (son *trusted extensions*: alcanza con ser dueño de la base) |
| Docker | Docker Desktop en Windows | Solo para los tests de integración del backend (Testcontainers con `postgres:16-alpine`) |

Crear la base y el usuario de la demo (ejemplo con `psql` como superusuario; la contraseña es la que pongas en `.env`):

```sql
CREATE USER odonto WITH PASSWORD '<elegí una contraseña>';
CREATE DATABASE odontogestion OWNER odonto;
```

## 2. Variables de entorno

1. Copiar la plantilla: `cp .env.example .env` (en PowerShell: `Copy-Item .env.example .env`). **`.env` no se versiona.**
2. Completar como mínimo:
   - `DB_PASSWORD`: la contraseña del usuario de la base.
   - `BOOTSTRAP_ODONTOLOGO_PASSWORD`: contraseña del odontólogo inicial (8 caracteres o más). Se usa solo en el primer arranque con la base vacía.
   - `SPRING_PROFILES_ACTIVE=demo` para la demo (`dev` para desarrollar con Vite).
   - `MAIL_USER`, `MAIL_PASSWORD` y `MAIL_FROM`: la cuenta SMTP real (para Gmail, una contraseña de aplicación). Sin ellas la app arranca, pero los emails quedan `FALLIDA` en `/notificaciones`.
   - `NOTIFICATION_POLL_INTERVAL=PT1M` para que en la demo los emails salgan en menos de un minuto.
3. Spring **no lee `.env`**: hay que exportar las variables en la shell antes de arrancar.
   - Linux, macOS o Git Bash (desde la raíz): `set -a; . ./.env; set +a`
   - Windows: `backend/run-local.ps1` las carga solo (ver abajo).

Usuario del odontólogo: el email de `BOOTSTRAP_ODONTOLOGO_EMAIL` (por defecto `odontologo@consultorio.example`) con la contraseña de `BOOTSTRAP_ODONTOLOGO_PASSWORD`.

## 3. Levantar la demo (un solo proceso en http://localhost:8080)

Con el perfil `demo`, Spring Boot sirve el build del frontend y la API desde el mismo origen. Recargar cualquier
pantalla (`/agenda`, `/publico`) o abrir un enlace de email (`/acciones/...`, `/avisos/...`) devuelve la aplicación,
no un `404`.

**Linux, macOS o Git Bash** (con las variables ya exportadas):

```bash
cd backend
bash gradlew bootJar -PwithFrontend
java -jar build/libs/odontogestion-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

`-PwithFrontend` corre `npm ci` y `npm run build` en `frontend/` y mete el resultado en el jar (requiere Node).
Se usa `bash gradlew` porque el wrapper puede no tener permiso de ejecución después de clonar.

**Windows (PowerShell)**, con `SPRING_PROFILES_ACTIVE=demo` en `.env`:

```powershell
cd backend
.\run-local.ps1 -PwithFrontend
```

`run-local.ps1` carga `.env`, valida las contraseñas obligatorias y corre `gradlew.bat bootRun` con los argumentos que
le pases. Sin `-PwithFrontend` levanta solo la API (útil con el perfil `dev`).

Abrir http://localhost:8080 e iniciar sesión con el usuario del odontólogo. Con el perfil `demo` y la base vacía, el
arranque carga pacientes y turnos ficticios de la semana en curso (seed de demo de C-12, propiedad
`app.seed-demo.enabled`), para mostrar la agenda, los estados y el contador de ausencias sin cargar datos a mano.

### Recrear la base

El seed calcula "hoy" y "esta semana" con el reloj del arranque. Si la demo se presenta otro día, o la base quedó con
datos de pruebas, conviene recrearla:

```sql
-- conectado a la base odontogestion como su dueño
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Al arrancar de nuevo, Flyway vuelve a aplicar las migraciones y se cargan otra vez el odontólogo y los datos de demo.

### Desarrollo (perfil `dev`, con Vite)

Para desarrollar con recarga en caliente: backend con `SPRING_PROFILES_ACTIVE=dev`
(`bash gradlew bootRun` o `.\run-local.ps1`) y, en otra terminal, `npm run dev` en `frontend/`
(http://localhost:5173, con proxy de `/api` a `:8080`). Sin backend: `npm run dev:mocks` (ver [frontend/README.md](frontend/README.md)).

## 4. Checklist de la demo

Usar como paciente un email real de quien presenta (para recibir los enlaces). Revisar el estado de cada email en
**Notificaciones** (`/notificaciones`).

**Flujo 2: solicitud de turno desde la página pública**
- [ ] En `/publico`, elegir prestación, fecha y horario, y completar los datos ficticios con el email de quien presenta.
- [ ] Llega el email de confirmación; el enlace abre `/acciones/...` y muestra el resumen del turno.
- [ ] Al confirmar, el turno aparece **Reservado** en `/agenda` y llega el email de confirmación del turno.
- [ ] El mismo enlace, usado de nuevo, ya no sirve (token de un solo uso).

**Flujo 3: cancelación y recuperación por lista de espera**
- [ ] En `/publico/lista-espera`, anotarse para una prestación (y confirmar el email de alta).
- [ ] Desde `/agenda`, cancelar un turno de esa prestación: llega el email de aviso de hueco.
- [ ] El enlace `/avisos/...` muestra el hueco; al aceptarlo se crea el turno **Reservado**. Un segundo intento sobre el mismo hueco informa que ya no está disponible.

**Flujo 5: recordatorio 24 h antes y confirmación**
- [ ] Depende de C-11 (`recordatorios-scheduler`). Con C-11 en `main`: un turno **Reservado** dentro de las próximas `REMINDER_HOURS_BEFORE` horas recibe un único recordatorio con enlaces para confirmar o cancelar.

**Agenda y ausencias**
- [ ] `/agenda` (día y semana) muestra turnos en distintos estados; `/agenda/hoy` lista los del día.
- [ ] Pasada la tolerancia, "Marcar ausente" suma una ausencia al paciente; "Revertir ausencia" la deshace dentro de las 24 h.

## 5. Tests

**Backend** (desde `backend/`, con Docker corriendo):

```bash
bash gradlew test
```

Los tests de integración (`*IT`) levantan PostgreSQL con Testcontainers; sin Docker fallan a propósito (no hay H2).
Para reutilizar el contenedor entre corridas: `testcontainers.reuse.enable=true` en `~/.testcontainers.properties`.

**Frontend** (desde `frontend/`):

```bash
npm ci
npm test
npm run typecheck
npm run build
```

## 6. Problemas frecuentes

- **`Permission denied` al correr `./gradlew`**: usar `bash gradlew` (o `chmod +x backend/gradlew` en tu copia).
- **El arranque falla nombrando `BOOTSTRAP_ODONTOLOGO_PASSWORD` o `DB_PASSWORD`**: no se exportaron las variables de `.env` en esa shell.
- **Flyway falla en `validate` con una migración *ignored***: la base local quedó de una rama vieja; recrearla (ver arriba).
- **`404` al recargar `/agenda` en el puerto 8080**: el jar se construyó sin `-PwithFrontend`, o se arrancó con un perfil distinto de `demo`.
- **Repo dentro de OneDrive**: la sincronización puede bloquear `build/`; reintentar o clonar fuera de OneDrive.
