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

Mientras `C-02` y el backend de `C-06` no estén en `main`, la configuración (`/configuracion`) se puede usar en el navegador con APIs en memoria y datos ficticios:

```bash
npm run dev:mocks
```

Funciona igual en Windows (PowerShell/CMD), macOS y Linux: el flag `VITE_CONFIG_MOCKS=true` sale de `.env.mocks`.
Para confirmar que está activo, la consola del navegador muestra `[OdontoGestión] Modo mocks de desarrollo activo`.
En este modo no se hace ninguna solicitud a `/api`: si Vite muestra `http proxy error ... ECONNREFUSED`, el servidor se levantó con `npm run dev` (modo normal) y no con `dev:mocks`.

Login con cualquier email y la contraseña de prueba `demo`. Los datos se pierden al recargar. El código de `src/dev/` se carga solo con `import.meta.env.DEV` y no entra en `npm run build`.
El contrato asumido de la API de configuración está en `src/features/config/config-api.ts` (consultorio/perfil, de C-02) y `agenda-config-api.ts` (prestaciones/horarios/bloqueos, de C-06).
