# Proposal

## Why

Al revisar el frontend público de la lista de espera de C-10 (archivado el 2026-11-23) contra el backend real aparecieron tres defectos y dos huecos de validación que afectan la demo del 25/11:

- Si la prestación elegida está desactivada, la API responde `422` con el error en `prestacionId`. La página solo marca errores de los campos de texto, así que se ve "Revisá los datos marcados" sin ningún campo marcado.
- Si la prestación ya no existe (`404`), la página muestra el mensaje genérico de falla y el paciente no sabe que tiene que elegir otra.
- La ruta `/publico/lista-espera` carga las prestaciones con el cliente real aunque `bootstrap` reciba otro: en `npm run dev:mocks` la pantalla no carga prestaciones.
- El cliente no impide una fecha "hasta" anterior a hoy, que la API rechaza (RN de C-10, `waiting-list`).
- El mock de desarrollo de la inscripción acepta todo y no refleja los rechazos del backend.

## What Changes

- La página de inscripción muestra el error de `prestacionId` junto al selector de prestación.
- Ante un `404` de la prestación, informa que la prestación ya no está disponible y vuelve a cargar la lista.
- La validación de cliente rechaza una fecha "hasta" anterior a hoy (calendario del consultorio, `America/Argentina/Buenos_Aires`) y los campos de fecha ofrecen hoy como mínimo.
- `createListaEsperaRoutes` recibe el cliente de prestaciones públicas inyectado por `bootstrap` (el mismo de `/publico`).
- El mock de desarrollo de la inscripción rechaza rango invertido, fecha pasada y prestación inexistente igual que el backend.

Fuera de alcance: franja horaria, cancelación de la inscripción por el paciente, vencimiento por fecha y pulido visual (siguen como etapa posterior según el proposal de C-10). Sin cambios de backend.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-waiting-list`: el requisito "Formulario público de inscripción" suma la validación de fecha pasada y el manejo de los rechazos de la prestación (`422` en `prestacionId` y `404`).

## Impact

- **Frontend**: `features/lista-espera` (`lista-espera-api.ts`, `inscripcion-page.ts`, `index.ts`), `bootstrap.ts` y `dev/lista-espera-mocks.ts`, con sus tests.
- **Backend / base de datos / API**: sin cambios; se consume el contrato real de C-10.
- **Governance**: ALTO (hereda de C-10); el plan fue aprobado por P5 antes de implementar.
