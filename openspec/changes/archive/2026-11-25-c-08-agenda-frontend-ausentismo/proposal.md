# Proposal

## Why

Hoy `/agenda` es una pantalla de relleno. El odontólogo no puede ver su ocupación, cargar los turnos que le piden por teléfono, mover o cancelar un turno, ni registrar una ausencia. Sin ese registro no se ataca el ausentismo, que es uno de los dos problemas que justifican OdontoGestión. Este change es la parte de **frontend (P5)** de `C-08`: cubre US-020 a US-024 (agenda y turnos) y US-060/US-061 (ausentismo).

Se adelanta **contra mocks** porque `C-07` (`turnos-agenda-core`) todavía no empezó y el backend de ausentismo de `C-08` (P3, Jeremías) depende de él. `CHANGES.md` habilita explícitamente que "el front puede arrancar contra el contrato". Para que el backend implemente lo mismo que consume el front, este change fija por escrito el **contrato provisional v0** de los endpoints de turnos y ausencias.

## What Changes

- **Nueva feature `features/agenda`** (TypeScript vanilla), dentro del layout autenticado:
  - `/agenda`: vista **día** y **semana** en hora del consultorio, navegable por fechas. Cada turno muestra un color y una etiqueta según su estado (los 7 estados de RN-TU-01, incluidos los pendientes de confirmación).
  - `/agenda/hoy`: lista de los turnos del día con paciente, prestación y hora, y las acciones que permite el estado de cada turno.
  - **Crear turno** (US-021):
    - Busca al paciente por DNI y, si no existe, permite darlo de alta rápida.
    - Ofrece las prestaciones activas.
    - Propone los horarios libres de la fecha elegida.
    - Si la API responde `409`, muestra el conflicto y las alternativas que devuelve.
  - **Mover** (US-022), **cancelar con motivo** (US-023) y **marcar atendido** (US-024), cada acción habilitada según la máquina de estados (RN-TU-02).
  - **Marcar ausente** (US-060): se habilita recién pasada la hora de inicio más la tolerancia del consultorio. La API sigue siendo la que decide: un `422` se informa al usuario.
  - **Revertir ausencia** dentro de las 24 h (RN-AS-06).
  - **Historial de ausencias** del paciente (US-061): cantidad y fechas, visible en el detalle del turno.
- **Contrato provisional v0** de C-07 y del backend de C-08 (DTOs, endpoints y errores), documentado en `design.md`. El front lo consume detrás de un puerto (`AgendaApi`), así que cuando el backend real difiera el ajuste queda en un solo módulo.
- **Mocks de desarrollo en memoria** (`src/dev/agenda-mocks.ts`), con el mismo enfoque de C-06: se cargan solo en `DEV`, con datos 100 % ficticios. Reproducen la no superposición, las transiciones, la tolerancia de ausente, la reversión en 24 h y el `409` con alternativas.
- **Cliente HTTP**: el error tipado conserva los miembros de extensión del `ProblemDetail`. La agenda los necesita para leer `alternativas` en un `409`.
- **Pendiente de integración real** hasta que cierren C-07 y el backend de Jeremías: el contrato vs. la implementación real, y la verificación manual contra la API. La tabla de qué queda hecho ahora y qué queda pendiente está en `design.md`. El change **no se archiva** hasta completar el grupo de integración de `tasks.md`.

Fuera de alcance:
- Todo el backend: C-07 (P1/P2/P3) y el ausentismo de C-08 (P3).
- La pantalla de pacientes (`/pacientes` sigue siendo un relleno).
- El acceso a la historia clínica desde el turno, porque C-05 es stretch.
- La página pública (C-09).
- Una política por reincidencia de ausencias (PA-08, etapa posterior).

## Capabilities

### New Capabilities
- `web-agenda`: pantallas del odontólogo para ver la agenda (día, semana y hoy) y operar turnos: crear (con búsqueda o alta rápida de paciente y horarios libres), mover, cancelar, marcar atendido, marcar ausente, revertir la ausencia y ver el historial de ausencias del paciente, con presentación de los errores `409`/`422` de la API.

### Modified Capabilities
- `web-http-client`: el requisito "Errores tipados desde ProblemDetail" pasa a exigir que el error entregado conserve los miembros de extensión del cuerpo (por ejemplo `alternativas`), además de `status`, `title`, `detail` y los errores por campo.

## Impact

- **Código frontend**:
  - Nuevo `frontend/src/features/agenda/` (API, modelo puro y vistas).
  - Nuevo `frontend/src/dev/agenda-mocks.ts`.
  - Cambios en `bootstrap.ts` (rutas `/agenda` y `/agenda/hoy` reemplazan el relleno), `main.ts` (cableado de mocks) y `shared/http/client.ts` y `problem.ts` (miembros de extensión).
  - Reutiliza `features/config/zoned-time.ts` y la API de prestaciones y consultorio de C-06.
- **APIs consumidas**:
  - Reales y ya en `main`: `GET /api/v1/prestaciones`, `GET /api/v1/consultorio`.
  - Real, pero todavía sin mergear: `GET/POST /api/v1/pacientes`, en la rama de C-04.
  - Provisionales v0: `GET /api/v1/agenda`, `GET /api/v1/agenda/disponibilidad`, `GET /api/v1/turnos/hoy`, `POST /api/v1/turnos`, `PUT /api/v1/turnos/{id}/mover`, `POST /api/v1/turnos/{id}/{cancelar|atendido|ausente|revertir-ausencia}` y `GET /api/v1/pacientes/{id}/ausencias`.
- **Coordinación**:
  - Thomas (P1): disponibilidad, `POST /turnos` y el `409` con alternativas.
  - Martina (P2): mover, cancelar, atendido y `GET /turnos/hoy`.
  - Jeremías (P3): ausente, reversión y ausencias.
  - Mauro (P4): la búsqueda y el alta de pacientes, que dependen del merge de C-04.
- **Sin cambios** en backend, base de datos, dependencias npm ni build.
