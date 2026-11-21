# Proposal

## Why

Sin pacientes no hay turnos: `C-07` (turnos y agenda) necesita una tabla `paciente` a la que referir cada turno, y `C-09` (página pública) necesita identificar a quien pide turno por su DNI sin darle una cuenta (RN-PA-02, RN-PU-04). Hoy el sistema solo tiene consultorio y odontólogo. `C-04` abre el GATE 2, del que sale el carril de historia clínica (`C-05`), y es requisito del GATE 3. Si se atrasa, pasa a estar en el camino crítico.

## What Changes

- Migración Flyway **V5**: tabla `paciente` con `consultorio_id` obligatorio, DNI único por consultorio (RN-PA-01), email y teléfono obligatorios (RN-PA-03), `fecha_nacimiento` y `direccion` opcionales, `cantidad_ausencias` (contador que usa `C-08`, arranca en 0) y `activo` para la baja lógica (RN-PA-04). Índices por DNI, por email y por apellido + nombre, siempre con prefijo `consultorio_id`.
- Entidad `Paciente` sin cuenta ni contraseña, y su repositorio. El DNI se normaliza (sin puntos ni espacios) y el email también (`trim` + minúsculas) antes de guardar y antes de buscar.
- Servicio `PacienteService` con el contrato **`buscarOCrearPorDni`** para `C-07` y `C-09`:
  - si el DNI ya existe en el consultorio, devuelve ese paciente **sin sobrescribir sus datos de contacto** (RN-PU-04);
  - si no existe, lo crea;
  - es seguro ante dos pedidos concurrentes con el mismo DNI (termina en un solo paciente) y no rompe la transacción de quien lo llama.
- API REST, toda bajo la sesión del odontólogo:
  - `GET /api/v1/pacientes?dni=&q=`: búsqueda exacta por DNI, o búsqueda por texto en apellido, nombre, email y prefijo de DNI. Devuelve una lista acotada y ordenada por apellido y nombre.
  - `GET /api/v1/pacientes/{id}`: ficha administrativa.
  - `POST /api/v1/pacientes`: alta en el momento (US-021). Un DNI duplicado responde `409`.
  - `PUT /api/v1/pacientes/{id}`: edición de los datos administrativos. Si el DNI nuevo choca con otro paciente, responde `409`.
- Formato de errores: datos con formato inválido → `400` con el campo; paciente inexistente o de otro consultorio → `404`; sin sesión → `401`; sin CSRF → `403`. Todo en `ProblemDetail`.
- Ningún dato personal del paciente (DNI, email, teléfono) se escribe en los logs.

Fuera de alcance:
- **Frontend**: la pantalla de pacientes y el buscador "DNI o crear" en el alta de turno se hacen en `C-08` (P5); el acceso desde la ficha a la historia clínica, en `C-05` (P6).
- **Seed de demo** de 10 pacientes ficticios: va en `C-12`.
- **Baja lógica**: el endpoint para desactivar un paciente queda para después (el MVP no lo pide). Solo se crea la columna `activo`.
- **Ausencias**: el incremento y la reversión de `cantidad_ausencias` y el historial de ausencias (US-061) son de `C-08`.
- **Historia clínica**: es `C-05`.
- **Búsqueda difusa** (`pg_trgm`) y paginación con cursor.

## Capabilities

### New Capabilities
- `paciente-registry`: registro administrativo de pacientes sin cuenta del consultorio. Cubre alta, edición, consulta y búsqueda por DNI o texto, unicidad del DNI por consultorio, contacto obligatorio y la operación `buscarOCrearPorDni`, que reutiliza al paciente sin pisar sus datos.

### Modified Capabilities
- `database-schema-management`: el requisito "Tablas de dominio con consultorio y tiempos UTC" suma escenarios para la tabla `paciente` (referencia obligatoria al consultorio, timestamps `timestamptz`) y la migración `5`.

## Impact

- **Base de datos**: nueva migración `V5__paciente.sql`. El plan reserva V4 para `C-06`, que todavía no se mergeó; el orden de merge se coordina con P1 (ver design, "Open Questions").
- **Backend**: paquetes nuevos `domain.paciente`, `application.paciente` y `api.paciente`. Se reutilizan `AuditableEntity`, `ConsultorioActual`, `Emails`, las excepciones de `domain.error` y `GlobalExceptionHandler`, sin cambiar su comportamiento.
- **Seguridad**: no cambia nada. `/api/v1/pacientes/**` ya queda protegido por la regla `/api/**` autenticada y por CSRF en las escrituras.
- **Contratos que consumen otros**:
  - `C-07` (P1): la entidad `Paciente`, para la FK `turno.paciente_id`, y `buscarOCrearPorDni` en el alta de turnos.
  - `C-09` (P1): `buscarOCrearPorDni` en la solicitud pública.
  - `C-08` (P5, P3): la API REST y el campo `cantidadAusencias`.
  - `C-05` (P4, P6): el `id` del paciente.
- **Tests**: tests de migración, de persistencia, unitarios del servicio y de API sobre PostgreSQL con Testcontainers, más un test de concurrencia de `buscarOCrearPorDni`.
