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
ignoran: el seed no crea otro usuario ni cambia contraseñas ni parámetros editados. La próxima migración libre es V4. El email sale de `BOOTSTRAP_ODONTOLOGO_EMAIL`
(por defecto `odontologo@consultorio.example`). Usar solo datos y contraseñas ficticias.

Variables de sesión: `SESSION_TIMEOUT_MIN` (por defecto 30) y `SESSION_COOKIE_SECURE` (por defecto `false`;
poner `true` solo si se sirve por HTTPS).

## Frontend empaquetado

`-PwithFrontend` ejecuta `npm ci` y `npm run build` en `../frontend` y copia `dist/` a `static/` dentro del jar
(`BOOT-INF/classes/static/`). Sin la propiedad el build no toca Node.

> Si el repo vive en OneDrive, la sincronización puede bloquear `build/` y producir errores transitorios de
> "Unable to delete directory"; reintentar o clonar fuera de OneDrive.
