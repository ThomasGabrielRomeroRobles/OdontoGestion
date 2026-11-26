## Why

La SPA tenía solo un shell funcional sin identidad visual, no había una puerta de entrada pública y `/pacientes` seguía siendo un placeholder: C-04 fue solo backend y delegó la pantalla a C-08, cuya propuesta la dejó pendiente aunque el scope de C-08 mencionaba el detalle del paciente con ausencias (US-061).

## What Changes

- Diseño visual global (`shared/styles/app.css`): tokens verde/menta, tipografías Inter + Outfit, barra superior con enlace activo (`aria-current`), botones pill, formularios, tablas con estado coloreado, tarjetas de agenda y páginas públicas en tarjeta centrada. Animaciones desactivadas con `prefers-reduced-motion`. Sigue siendo TypeScript vanilla + CSS propio, sin librerías de UI.
- Landing pública en `/inicio` con CTAs "Pedir turno" y "Anotarme en lista de espera"; "Ingresar" solo para el odontólogo.
- Pantalla de pacientes: listado con búsqueda en `/pacientes` y ficha en `/pacientes/:id` con ausencias y tarjeta "Historia clínica — próximamente (C-05)". Mocks de desarrollo en `src/dev/pacientes-mocks.ts`.

## Capabilities

### New Capabilities
- `web-landing`: página pública de presentación en `/inicio`.
- `web-patients`: listado, búsqueda y ficha de pacientes con ausencias.

### Modified Capabilities
- Ninguna: `web-app-shell` no tiene requisitos que describan `/pacientes` como placeholder.

## Impact

Solo frontend (`frontend/src/features/landing`, `features/pacientes`, `shared/styles`, `dev`). Consume `GET /api/v1/pacientes` y `GET /api/v1/pacientes/{id}`, `GET /api/v1/pacientes/{id}/ausencias` (C-04/C-08). Sin cambios de backend ni de esquema.
