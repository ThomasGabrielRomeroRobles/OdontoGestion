# Tasks

> P5 (Juan Ignacio). Governance ALTO (hereda de C-10): plan aprobado por P5 antes de implementar. TDD estricto: RED → GREEN → TRIANGULATE → REFACTOR. `tsconfig` strict, sin `any`. Solo datos ficticios.

## 1. Cliente de prestaciones inyectado (D4)

- [x] 1.1 RED: en `bootstrap.test.ts`, con un `publicoApi` doble, `/publico/lista-espera` lista sus prestaciones (no las del cliente real). GREEN: `createListaEsperaRoutes(api, listarPrestaciones)` y `bootstrap.ts` le pasa `deps.publicoApi`. Verificar con `npm test` en verde

## 2. Rechazos de la prestación (D1, D2)

- [x] 2.1 RED: en `inscripcion-page.test.ts`, escenario "Prestación desactivada" (`422` con `prestacionId` → mensaje junto al selector y `aria-invalid`). GREEN: contenedor de error del selector y `marcarError` reconoce `prestacionId`
- [x] 2.2 RED: en `lista-espera-api.test.ts`, `404` → `{ tipo: 'prestacionNoDisponible' }`; en `inscripcion-page.test.ts`, escenario "Prestación inexistente" (mensaje, recarga de prestaciones, selector vacío). GREEN: resultado nuevo y su manejo en la página

## 3. Fecha hasta anterior a hoy (D3)

- [x] 3.1 RED: escenario "Fecha hasta anterior a hoy" con reloj fijo (incluido el borde de medianoche: 03:00Z del 25/11 todavía es 24/11 en el consultorio) y `min` de los inputs de fecha. GREEN: parámetro `ahora`, cálculo con `partesEnZona` y regla en `validarFechas`. Los tests existentes fijan el reloj

## 4. Mock de desarrollo (D5)

- [x] 4.1 RED → GREEN: en `lista-espera-mocks.test.ts`, el mock rechaza rango invertido y fecha pasada (`invalida` en `fechaHasta`) y prestación inexistente (`prestacionNoDisponible`), y acepta una inscripción válida

## 5. Verificación

- [x] 5.1 `npm test`, `npm run typecheck` y `npm run build` en verde; `grep -rnw any` sin usos del tipo `any` en `src/features/lista-espera` y `src/dev/lista-espera-mocks.ts`
- [x] 5.2 `openspec validate c-10-ajustes-lista-espera-publico --strict` sin errores
- [x] 5.3 Prueba manual con `npm run dev:mocks` en `/publico/lista-espera`: carga prestaciones, fecha pasada rechazada en el cliente, inscripción aceptada
