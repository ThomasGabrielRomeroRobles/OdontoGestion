# Design

## Context

Ver `proposal.md`. Contrato real (C-10, backend en `main`): `POST /api/v1/publico/lista-espera` responde `202` genérico; `400` por Bean Validation; `422` `ValidacionDominioException` con `errors` por campo (`fechaHasta` "No puede ser anterior a la fecha desde." o "No puede ser anterior a hoy."; `prestacionId` "La prestación está desactivada."); `404` si la prestación no existe; `429` con `Retry-After`. La página ya resuelve `202`, `429` y los errores de los 7 campos de texto.

## Goals / Non-Goals

**Goals:**
- Que todo rechazo de la inscripción sea visible y accionable para el paciente.
- Que `npm run dev:mocks` recorra la pantalla y los mocks reflejen los rechazos reales.

**Non-Goals:**
- Cambios de backend, de la página de aviso o del listado del odontólogo.
- Funcionalidades diferidas de C-10 (franja, cancelación por enlace, vencimiento por fecha).

## Decisions

### D1. Error de `prestacionId` en el selector
El selector de prestación suma su propio contenedor de error (`data-error="prestacionId"`) y `marcarError` reconoce `prestacionId` además de los campos de texto. La validación de cliente "Elegí una prestación." pasa a mostrarse también ahí (y en la alerta, como hoy).

### D2. `404` como resultado tipado
`inscribir` devuelve un nuevo resultado `{ tipo: 'prestacionNoDisponible' }` ante `404`, en lugar de propagar el error. La página muestra "La prestación elegida ya no está disponible. Elegí otra.", vacía la elección y vuelve a pedir `listarPrestaciones`. Alternativa descartada: tratarlo en el `catch` genérico (mezcla un caso esperado con fallas de red).

### D3. "Hoy" con reloj inyectable y en la zona del consultorio
`createInscripcionPage` recibe un tercer parámetro opcional `ahora: () => Date` (por defecto `new Date()`). "Hoy" se calcula con `partesEnZona` de `config/zoned-time.ts` (`America/Argentina/Buenos_Aires`), igual que el backend con `app.timezone`, para que no dependa de la zona del navegador. Ambos inputs de fecha llevan `min = hoy`; la validación de cliente agrega "No puede ser anterior a hoy." en `fechaHasta` (mismo texto que el backend). `fechaDesde` en el pasado se permite, como en el backend.

### D4. Cliente de prestaciones inyectado
`createListaEsperaRoutes(api, listarPrestaciones)` recibe la función de prestaciones; `bootstrap` le pasa la de `deps.publicoApi` (por defecto el cliente real). Así `dev:mocks` y los tests de `bootstrap` usan el mismo doble que `/publico`.

### D5. Mock alineado con el backend
`crearListaEsperaApiMock` valida rango invertido y fecha pasada (`invalida` con el error en `fechaHasta`) y una prestación fuera de las del mock público (`prestacionNoDisponible`), con el mismo reloj inyectable.

## Risks / Trade-offs

- [Desfase de un día entre la zona del navegador y la del consultorio] → se calcula siempre en la zona del consultorio (D3); el servidor sigue siendo la autoridad.
- [Tests existentes con fechas fijas que se vuelven pasadas con el tiempo] → los tests de la página fijan el reloj.
