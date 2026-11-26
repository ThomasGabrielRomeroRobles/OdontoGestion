## Context

TypeScript vanilla + Vite, sin librerías de UI (regla dura 14). El backend de pacientes ya existe (C-04, C-08).

## Decisions

- **CSS único con tokens**: `shared/styles/app.css` con variables CSS; el estado de turnos/pacientes se colorea por atributo `data-valor`, sin clases por componente.
- **Accesibilidad**: enlace activo con `aria-current`; animaciones envueltas en `prefers-reduced-motion`.
- **Búsqueda de pacientes**: la entrada de 7–8 dígitos se envía como `dni`; cualquier otro texto como `q`; vacío sin filtro. La API devuelve como máximo 20 resultados.
- **Landing sin cuentas de paciente**: los pacientes actúan por la página pública y enlaces por token; "Ingresar" es solo para el odontólogo.
- **Historia clínica**: tarjeta placeholder hasta C-05 (stretch).
- **Mocks**: `dev/pacientes-mocks.ts` permite avanzar sin backend, con datos ficticios.
