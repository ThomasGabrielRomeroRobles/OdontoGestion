# Frontend (TypeScript + Vite)

TypeScript estricto "vanilla" (sin framework de UI), Vite y Vitest.

## Comandos (desde `frontend/`)

```bash
npm ci               # instala dependencias según package-lock.json
npm run dev          # servidor de desarrollo (http://localhost:5173); proxy /api -> http://localhost:8080
npm test             # Vitest (jsdom)
npm run typecheck    # tsc --noEmit
npm run build        # typecheck + build de producción en dist/
```

Para servirlo desde Spring Boot (perfil `demo`): `./gradlew bootJar -PwithFrontend` en `backend/`
(requiere Node y npm). El build se copia dentro del jar, no a `src/`.

## Estructura

`src/features/` (por feature), `src/shared/` (http, router, state, layout), `src/pages/`, `src/bootstrap.ts` y `src/main.ts`.
El contrato de autenticación asumido (D11) está solo en `src/features/auth/auth-api.ts`.

## Modo mocks de desarrollo (sin backend)

El mismo flag activa todos los mocks en memoria (datos ficticios): la configuración (`/configuracion`, C-06) y la agenda (`/agenda`, `/agenda/hoy`, C-08) con la búsqueda y el alta de pacientes:

```bash
npm run dev:mocks
```

Funciona igual en Windows (PowerShell/CMD), macOS y Linux: el flag `VITE_CONFIG_MOCKS=true` sale de `.env.mocks`.
Para confirmar que está activo, la consola del navegador muestra `[OdontoGestión] Modo mocks de desarrollo activo`.
En este modo no se hace ninguna solicitud a `/api`: si Vite muestra `http proxy error ... ECONNREFUSED`, el servidor se levantó con `npm run dev` (modo normal) y no con `dev:mocks`.

Login con cualquier email y la contraseña de prueba `demo`. Los datos se pierden al recargar. El código de `src/dev/` se carga solo con `import.meta.env.DEV` y no entra en `npm run build`.
El contrato asumido de la API de configuración está en `src/features/config/config-api.ts` (consultorio/perfil, de C-02) y `agenda-config-api.ts` (prestaciones/horarios/bloqueos, de C-06).

## Agenda (C-08): front contra el contrato provisional v0

`/agenda` (vista día/semana), `/agenda/hoy`, nuevo turno, mover, cancelar, atendido, ausente, revertir ausencia e historial de ausencias
están implementados contra el **contrato provisional v0** de C-07 y del backend de ausentismo
(`openspec/changes/c-08-agenda-frontend-ausentismo/design.md`, D1). Ese contrato vive solo en `src/features/agenda/agenda-api.ts`;
la búsqueda y el alta de pacientes usan el contrato real de C-04 (`pacientes-api.ts`).

**Pendiente de integración real** (design D11, grupo 9 de `tasks.md`): sin mocks, las rutas de agenda llaman a la API real y muestran
el error de conexión o `404` hasta que C-07 y el backend de C-08 estén en `main`.

Para recorrerla con mocks: `npm run dev:mocks`, login con `demo`, y en `/agenda`:

- **Nuevo turno**: buscar el DNI `30111222` (existe) u otro cualquiera (alta rápida); elegir prestación, fecha y hueco.
  Cada pestaña tiene su propia memoria, así que con mocks el `409` con alternativas no se puede provocar desde la UI:
  está cubierto por los tests y se prueba contra el backend real en la integración (grupo 9).
- **Detalle de un turno**: mover, cancelar con motivo, marcar atendido; "Marcar ausente" se habilita pasados inicio + 15 min;
  "Revertir ausencia" dentro de las 24 h. El historial de ausencias aparece en el detalle.
- **Turnos de hoy**: enlace desde la barra de la agenda.

Los mocks reproducen las reglas del contrato: no superposición `[inicio, fin)` entre turnos activos, transiciones válidas (`422` si no),
tolerancia de ausente y ventana de 24 h (con el reloj real) y el contador de ausencias.
