# Proposal

## Why

Para calcular disponibilidad y crear turnos (`C-07`), el sistema tiene que saber qué prestaciones se atienden y cuánto dura cada una, en qué horarios trabaja el odontólogo y qué franjas están bloqueadas. Hoy nada de eso existe. Tampoco hay pantallas para que el odontólogo ajuste los datos y parámetros del consultorio que publica `C-02`. Sin esta configuración no hay agenda, ni página pública, ni lista de espera. Por eso `C-06` está en el camino crítico (`C-02 → C-06 → C-07`) y cubre el Flujo 8 y las historias US-010 a US-013.

## What Changes

- **Backend (P1 prestaciones, P2 horarios y bloqueos)**:
  - Migración Flyway `V4`: tablas `prestacion`, `horario_trabajo` y `bloqueo_agenda`, todas con `consultorio_id`.
    - `horario_trabajo` lleva restricción de exclusión para que no haya solapamiento por consultorio y día, y `hora_desde < hora_hasta`.
    - `bloqueo_agenda` lleva `desde < hasta`.
  - Seed ficticio: las 6 prestaciones y los horarios L-V 09-13 / 14-18.
  - Entidades, repositorios y servicios de `Prestacion`, `HorarioTrabajo` y `BloqueoAgenda`.
  - API autenticada:
    - `/api/v1/prestaciones`: alta, listado, edición y desactivación/reactivación, sin borrado físico.
    - `/api/v1/horarios`: alta, listado, edición y baja.
    - `/api/v1/bloqueos`: alta, listado por rango, edición y baja.
  - Reglas de validación, con `422` como `ProblemDetail` y errores por campo:
    - La duración de una prestación es positiva y múltiplo de la granularidad del consultorio (RN-AG-02).
    - El nombre de una prestación es único por consultorio.
    - En horarios, `hora_desde < hora_hasta` y sin solapamiento en el mismo día.
    - En bloqueos, `desde < hasta`.
  - Si se baja la granularidad, una prestación existente que deje de ser múltiplo no se modifica: la regla aplica a altas y ediciones (los cambios valen para nuevas operaciones, US-013).
  - El listado de turnos afectados por un bloqueo queda **fuera** de este change: lo completa `C-07`, que es donde existen los turnos.
- **Frontend (P5)**: nueva feature `features/config`, con la ruta privada `/configuracion` y sus secciones:
  - Datos del consultorio y parámetros (consume `GET/PUT /api/v1/consultorio` de `C-02`).
  - Perfil y cambio de contraseña (consume `GET/PUT /api/v1/perfil` y `POST /api/v1/perfil/password` de `C-02`).
  - Prestaciones: listar, crear, editar, desactivar y reactivar.
  - Horarios semanales.
  - Bloqueos.
  - Comportamiento común de las pantallas:
    - Validación de cliente equivalente a las reglas del servidor, sin reemplazarlas.
    - Los `422` se muestran por campo.
    - Las escrituras llevan CSRF, a través del cliente HTTP existente.
    - Las fechas se muestran en `America/Argentina/Buenos_Aires` y se envían en UTC.
  - Enlace "Configuración" en la navegación del layout autenticado.
- Hasta que `C-02` y el backend de `C-06` estén en `main`, el frontend se desarrolla y prueba contra mocks del contrato que fija este change.

## Capabilities

### New Capabilities
- `agenda-configuration`: definición de prestaciones (duración, activación), horarios semanales de trabajo y bloqueos de agenda del consultorio, con sus reglas de validación y su API REST.
- `web-configuration`: pantallas del odontólogo para configurar consultorio, parámetros, perfil y contraseña, prestaciones, horarios y bloqueos, con validación de cliente y presentación de errores de la API.

### Modified Capabilities
<!-- Ninguna. `web-app-shell` no cambia sus requisitos: agregar una ruta privada y un enlace de navegación usa los mecanismos existentes. Los requisitos de la API de consultorio y perfil son de C-02. -->

## Impact

- **Backend**: migración `V4__agenda_configuracion.sql` (próximo número libre si `C-02` usa `V2` y `V3`), paquetes de dominio, aplicación, API e infraestructura para prestaciones, horarios y bloqueos, más tests de integración con Testcontainers.
- **Frontend**:
  - Nuevo `frontend/src/features/config/` (APIs tipadas, validadores y vistas).
  - Ruta `/configuracion` registrada en `bootstrap.ts`.
  - Enlace en `shared/layout/app-layout.ts`.
  - Tests con Vitest.
- **API publicada**: `/api/v1/prestaciones`, `/api/v1/horarios` y `/api/v1/bloqueos` (contratos en `design.md`).
- **API consumida**: los endpoints de consultorio y perfil de `C-02`. Si `C-02` fija un contrato distinto al asumido en `design.md`, el que manda es el de `C-02` y se ajustan los adaptadores del frontend.
- **Dependencias**: `C-02` mergeado en `main` (tablas `consultorio`/`usuario`, sesión, CSRF y `granularidad_min`). No se agregan librerías nuevas.
- **Habilita**: `C-07` (disponibilidad y turnos dentro de horarios y fuera de bloqueos, RN-AG-07) y, a través de él, `C-08`, `C-09` y `C-10`.
- **Coordinación**: P1 y P2 tienen que validar los contratos de la API antes de que P5 cierre el frontend contra mocks.
