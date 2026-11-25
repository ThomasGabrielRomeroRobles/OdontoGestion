# demo-environment Specification

## Purpose

Dejar la demo local lista para mostrarse: datos ficticios suficientes para recorrer la agenda sin cargarlos a mano y una verificación automática del recorrido principal de la página pública.

## Requirements

### Requirement: Datos de demostración ficticios
Con `app.seed-demo.enabled=true` (activado en el perfil `demo`), la aplicación SHALL crear al arrancar, en el consultorio actual, 10 pacientes ficticios y turnos de ejemplo de la semana en curso en la zona del consultorio, dentro de los horarios de trabajo, sin superponerse y usando las prestaciones activas. Los datos MUST ser ficticios (regla dura 12): nombres inventados y emails en el dominio reservado `.example`.

#### Scenario: Primer arranque de la demo
- **GIVEN** una base recién migrada, sin pacientes, y `app.seed-demo.enabled=true`
- **WHEN** la aplicación arranca
- **THEN** existen 10 pacientes y turnos de la semana en curso en los estados `RESERVADO`, `CONFIRMADO`, `ATENDIDO` y `AUSENTE`

#### Scenario: Contador de ausencias coherente
- **GIVEN** el seed de demo se ejecutó
- **WHEN** se consulta `GET /api/v1/pacientes/{id}/ausencias` de cualquier paciente sembrado
- **THEN** `cantidad` es igual a la cantidad de turnos `AUSENTE` de ese paciente

#### Scenario: Turnos dentro del horario y sin superposición
- **GIVEN** el seed de demo se ejecutó
- **WHEN** se consulta la agenda de la semana en curso
- **THEN** cada turno sembrado cae dentro de un horario de trabajo, usa una prestación activa y no se superpone con otro turno activo

#### Scenario: Seed idempotente
- **GIVEN** el consultorio ya tiene al menos un paciente
- **WHEN** la aplicación arranca de nuevo con `app.seed-demo.enabled=true`
- **THEN** no se crea ningún paciente ni turno nuevo

#### Scenario: Seed desactivado fuera de la demo
- **GIVEN** `app.seed-demo.enabled` no está en `true` (por ejemplo, perfil `dev`)
- **WHEN** la aplicación arranca
- **THEN** no se crean pacientes ni turnos de ejemplo

#### Scenario: El seed no envía emails
- **GIVEN** el seed de demo se ejecutó
- **WHEN** se consultan las notificaciones registradas
- **THEN** no hay ninguna notificación de los turnos sembrados

#### Scenario: Datos ficticios
- **GIVEN** el seed de demo se ejecutó
- **WHEN** se revisan los pacientes creados
- **THEN** todos los emails terminan en `.example`

### Requirement: Recorrido principal verificado de punta a punta
El proyecto SHALL tener una prueba automatizada que, con la aplicación en el perfil `demo` contra PostgreSQL real (Testcontainers) y un servidor SMTP de pruebas, recorra la solicitud pública de un turno hasta su confirmación por el enlace del email. Cuando C-11 esté en `main`, la prueba MUST continuar hasta el envío del recordatorio.

#### Scenario: Solicitud y confirmación por email
- **GIVEN** la aplicación corre con el perfil `demo` y hay un hueco disponible
- **WHEN** se envía `POST /api/v1/publico/solicitudes-turno` con datos ficticios, se procesa el outbox y se envía `POST /api/v1/acciones/{token}` con la acción de confirmar, usando el token del enlace recibido en el email
- **THEN** el servidor SMTP de pruebas recibió un email de confirmación con el enlace, y el turno queda `RESERVADO`

#### Scenario: Recordatorio del turno confirmado
- **GIVEN** el turno del escenario anterior está `RESERVADO` y C-11 está en `main`
- **WHEN** el reloj avanza hasta la ventana del recordatorio y corre el scheduler de recordatorios
- **THEN** el servidor SMTP de pruebas recibe un único email de recordatorio para ese turno, sin datos clínicos
