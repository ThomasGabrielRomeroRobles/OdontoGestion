# Backend (Spring Boot)

Java 21, Spring Boot 3.5, Gradle (wrapper incluido), PostgreSQL + Flyway.

## Comandos (desde `backend/`)

```bash
./gradlew test                       # tests unitarios + integración (requiere Docker, ver abajo)
./gradlew bootRun --args='--spring.profiles.active=dev'   # arranque local
./gradlew bootJar                    # jar sin frontend (no requiere Node)
./gradlew bootJar -PwithFrontend     # jar con el build de Vite en static/ (requiere Node y npm)
```

En Windows usar `gradlew.bat` si no se trabaja con Git Bash.

## Docker para los tests de integración

Los tests de integración usan **Testcontainers con PostgreSQL real** (`postgres:16-alpine`); no hay H2.
Docker (Docker Desktop en Windows) debe estar corriendo: sin Docker los `*IT` **fallan** a propósito.
Para reutilizar el contenedor entre ejecuciones, agregar `testcontainers.reuse.enable=true` en `~/.testcontainers.properties`.

## Configuración

Spring no lee `.env`: exportar las variables de `.env.example` en la shell (o en la run configuration del IDE).
`DB_PASSWORD` es obligatoria (no tiene valor por defecto). Las máquinas de demo necesitan PostgreSQL 15 o superior.


## Contraseña de bootstrap (C-02)

El consultorio demo lo inserta la migración Flyway V2. Si **no existe ningún usuario**, el arranque crea al
odontólogo inicial. La contraseña de ese usuario sale de `BOOTSTRAP_ODONTOLOGO_PASSWORD` y es **obligatoria** (entre 8 caracteres y 72 bytes): si falta,
el arranque falla con un mensaje que nombra la variable. Si ya existe algún usuario, la variable (y el email) se
ignoran: el seed no crea otro usuario ni cambia contraseñas ni parámetros editados (numeración de migraciones: ver "Migraciones"). El email sale de `BOOTSTRAP_ODONTOLOGO_EMAIL`
(por defecto `odontologo@consultorio.example`). Usar solo datos y contraseñas ficticias.

Variables de sesión: `SESSION_TIMEOUT_MIN` (por defecto 30) y `SESSION_COOKIE_SECURE` (por defecto `false`;
poner `true` solo si se sirve por HTTPS).

## Migraciones

En `main`: V1–V3 (C-01/C-02), **V4 = C-06**, **V6/V7 = C-03**. **V8 = C-04** (`paciente`). Siguen según
`CHANGES.md`: **V9 = C-07** (`turno`, con FK a `paciente`), **V10 = C-10** (lista de espera) y **V11 = C-05** (historia clínica, stretch). V5 queda
sin usar: C-04 la tenía asignada, pero se renumeró a V8 (2026-11-22) porque C-03 llegó antes a `main` con V6/V7.

**Regla**: una migración nueva siempre lleva un número **mayor que el último aplicado en `main`** y que el de las
ramas abiertas (`git branch -r`). Una migración de número menor que llega después de una mayor ya aplicada hace
fallar `validate` en las bases locales existentes (Flyway la marca como *ignored*); los tests no lo detectan porque
arrancan con base vacía. Si pasa, en desarrollo se recrea la base local (`DROP SCHEMA public CASCADE; CREATE SCHEMA
public;` y arrancar).

## Pacientes (C-04)

Endpoints con sesión del odontólogo (escrituras con `X-XSRF-TOKEN`). Ejemplos con datos ficticios:

| Método y ruta | Uso | Errores |
|---|---|---|
| `GET /api/v1/pacientes?dni=30.123.456` | Búsqueda exacta por DNI (lista de 0 o 1) | `400` DNI inválido |
| `GET /api/v1/pacientes?q=pér` | Texto en apellido, nombre o email, o prefijo de DNI (máx. 20, por apellido y nombre) | `400` `q` > 100 |
| `GET /api/v1/pacientes/{id}` | Ficha administrativa | `404` |
| `POST /api/v1/pacientes` | Alta → `201` + `Location` | `400` por campo, `409` DNI duplicado |
| `PUT /api/v1/pacientes/{id}` | Edición (no cambia `cantidadAusencias` ni `activo`) | `400`, `404`, `409` |

```json
POST /api/v1/pacientes
{ "nombre": "Ana", "apellido": "Pérez", "dni": "30.123.456", "telefono": "+54 11 5555-0001",
  "email": "ana.perez@ejemplo.test", "fechaNacimiento": "1990-05-01", "direccion": "Calle Ficticia 1" }
```

El DNI se guarda sin puntos (7 u 8 dígitos) y el email en minúsculas. Para otros changes, `PacienteService
.buscarOCrearPorDni(DatosPaciente)` reutiliza al paciente con ese DNI **sin pisar sus datos** (RN-PU-04) o lo crea;
es seguro ante pedidos concurrentes y se suma a la transacción de quien lo llama. Nunca se loguean DNI, email ni
teléfono.

## Frontend empaquetado

`-PwithFrontend` ejecuta `npm ci` y `npm run build` en `../frontend` y copia `dist/` a `static/` dentro del jar
(`BOOT-INF/classes/static/`). Sin la propiedad el build no toca Node.

> Si el repo vive en OneDrive, la sincronización puede bloquear `build/` y producir errores transitorios de
> "Unable to delete directory"; reintentar o clonar fuera de OneDrive.
