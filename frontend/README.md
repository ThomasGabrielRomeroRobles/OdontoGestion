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
