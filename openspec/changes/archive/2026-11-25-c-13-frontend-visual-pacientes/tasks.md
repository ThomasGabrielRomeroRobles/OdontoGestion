## 1. Diseño visual global

- [x] 1.1 Tokens, tipografías, barra superior, botones, formularios, tablas y reduced-motion en `shared/styles/app.css` (pantallas revisadas en el navegador; la regla de reduced-motion no se probó con la preferencia activada y no tiene test unitario de CSS)

## 2. Landing

- [x] 2.1 Ruta `/inicio` con CTAs y "Ingresar" solo odontólogo — `features/landing/landing-page.test.ts` (2 tests)

## 3. Pacientes

- [x] 3.1 Cliente API de pacientes — `features/pacientes/pacientes-api.test.ts` (4 tests)
- [x] 3.2 Listado, búsqueda, estados vacío/error y ficha con ausencias y placeholder de HC — `features/pacientes/pacientes-page.test.ts` (6 tests)
- [x] 3.3 Mocks de desarrollo — `dev/pacientes-mocks.test.ts` (2 tests)

## 4. Verificación

- [x] 4.1 `npm test` en `frontend/`: 594 tests pasando (594)
