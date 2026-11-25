# Tasks

> Responsables según el plan de G6 (confirmar en el equipo): P5 (Juan Ignacio) grupos 1 y 4, P4 grupo 2, P1 grupos 3 y 5, P2 grupo 6. TDD estricto: RED → GREEN → TRIANGULATE → REFACTOR. Tests de integración con Testcontainers, nunca H2. `Clock` fijo en los tests que dependen de la fecha. Solo datos ficticios.
> Governance MEDIO: implementar con checkpoints. Orden sugerido: 1 y 4 primero (no dependen de nadie), después 2, 3 y 6. El grupo 5 espera a C-11.

## 1. Fallback de la SPA (`runtime-configuration`) — P5

- [x] 1.1 RED: `SpaFallbackIT` con perfil `demo` y un `index.html` de prueba en `src/test/resources/static/` (más un `assets/app-test.js`). Cubrir los 6 escenarios de "Rutas del frontend servidas desde el mismo origen": `GET /agenda` sin sesión → `200` con el `index.html`; `GET /acciones/tok-ficticio` y `/avisos/tok-ficticio` → `200` con el `index.html`; `GET /api/v1/no-existe` con sesión → `404` `ProblemDetail`; `GET /assets/no-existe.js` → `404`; `GET /assets/app-test.js` → `200` con ese archivo; `POST /agenda` → no es `index.html`
- [x] 1.2 GREEN: `infrastructure/web/SpaFallbackController` según design D1 (`forward:/index.html`, `@ConditionalOnResource("classpath:static/index.html")`, `Cache-Control: no-cache` para `index.html`). Verificar: `SpaFallbackIT` verde y `DemoProfileIT`/`DevProfileIT` siguen verdes
- [x] 1.3 TRIANGULATE: rutas anidadas (`/agenda/hoy`, `/publico/lista-espera`) → `index.html`; `/api` sola y `/api/` → no `index.html`. Verificar con casos nuevos en `SpaFallbackIT`
- [x] 1.4 Prueba con el artefacto real: `./gradlew bootJar -PwithFrontend`, arrancar con `--spring.profiles.active=demo` y recargar el navegador en `/agenda`, `/agenda/hoy`, `/publico` y en un enlace `/acciones/...` de un email. Verificar que ninguna da `404` y registrar el resultado en el PR

## 2. Seed de demo (`demo-environment`) — P4

- [ ] 2.1 RED: `DemoSeedIT` (Testcontainers, `Clock` fijo en un miércoles, `app.seed-demo.enabled=true`) con los escenarios "Primer arranque de la demo", "Contador de ausencias coherente", "Turnos dentro del horario y sin superposición", "El seed no envía emails" y "Datos ficticios"
- [ ] 2.2 GREEN: `application/demo/DemoSeedService` (`@Transactional`) y `infrastructure/seed/DemoSeeder` (`@Order(300)`, `@ConditionalOnProperty`) según design D2 y D3; propiedad `app.seed-demo.enabled` en `AppProperties` y `true` en `application-demo.yml`. Verificar: `DemoSeedIT` verde
- [ ] 2.3 TRIANGULATE: escenarios "Seed idempotente" (segundo arranque o segunda llamada al service no crea nada) y "Seed desactivado fuera de la demo" (perfil `dev`). Además, un bloqueo que pisa una franja del seed hace que ese turno se omita sin fallar el arranque. Verificar con casos en `DemoSeedIT`
- [ ] 2.4 Verificar en el navegador con la demo levantada: la agenda de la semana muestra turnos en los 4 estados, `/agenda/hoy` lista los de hoy y el detalle de un turno de un paciente con ausencias muestra el contador y el historial

## 3. Smoke de punta a punta (`demo-environment`) — P1

- [ ] 3.1 RED → GREEN: `DemoSmokeIT` según design D4, pasos 1 a 5 (disponibilidad → solicitud pública → outbox → email en GreenMail → confirmación por token → turno `RESERVADO`). Verificar con el escenario "Solicitud y confirmación por email" en verde
- [ ] 3.2 TRIANGULATE: el email recibido no contiene datos clínicos ni el DNI, y el token no aparece en ningún log del test. Verificar con aserciones sobre el cuerpo del email y la salida capturada

## 4. Guía de ejecución local y de pruebas — P5

- [x] 4.1 Crear `README.md` en la raíz según design D5: requisitos (Java 21, Node LTS, PostgreSQL con `btree_gist`, Docker para tests), `.env` desde `.env.example`, levantar la demo (`./gradlew bootJar -PwithFrontend` + perfil `demo`, y con `run-local.ps1`), cómo recrear la base, checklist de los flujos 2, 3 y 5, y cómo correr los tests. Verificar siguiendo la guía paso a paso en una PC limpia del equipo
- [x] 4.2 Ajustar `.env.example` (comentario de `SPRING_PROFILES_ACTIVE=demo` para la demo y `NOTIFICATION_POLL_INTERVAL=PT1M` recomendado) y enlazar la guía desde `backend/README.md` y `frontend/README.md`. Verificar que `ConfiguracionSinSecretosTest` sigue verde

## 5. Recordatorio en el smoke (BLOQUEADO por C-11) — P1

> Precondición: C-11 `recordatorios-scheduler` mergeado en `main`. Hasta entonces este grupo no se empieza.

- [ ] 5.1 RED → GREEN: extender `DemoSmokeIT` con el paso 6 de design D4. Verificar con el escenario "Recordatorio del turno confirmado" en verde
- [ ] 5.2 Revisar el riesgo de recordatorios a emails `.example` de design D3/Riesgos y ajustar las fechas del seed si hace falta. Verificar con `DemoSeedIT` verde

## 6. Verificación de la demo y cierre — P2 con todo el equipo

- [ ] 6.1 SMTP real (SU-10): con la cuenta elegida, recorrer el flujo 2 y confirmar que el email llega y queda `ENVIADA` en `/notificaciones`. Anotar en el PR el límite diario de la cuenta y si alcanza para la demo
- [ ] 6.2 Secretos (regla 11): `git ls-files .env` vacío y `git grep -n -i "password\s*[:=]\s*[^$ {]"` sin valores reales. Registrar el resultado en el PR
- [ ] 6.3 Logs y emails (regla 10): recorrer los flujos 2, 3 y 5 con la demo y revisar el log en `INFO` y los emails recibidos; ni tokens, ni contraseñas, ni datos clínicos. Registrar el resultado de cada flujo en el PR
- [ ] 6.4 Verificación final: `./gradlew test` en `backend/` (Docker activo), `npm test`, `npm run typecheck` y `npm run build` en `frontend/`, todo en verde
- [ ] 6.5 `openspec validate c-12-demo-hardening --strict` sin errores

## Workflow follow-up

- Si C-11 no llega a `main` antes de la presentación, el change se puede mergear con los grupos 1–4 y 6 cerrados, documentando el grupo 5 como pendiente. **No se archiva** hasta cerrar el grupo 5.
- Al cerrar: `/opsx:archive c-12-demo-hardening`, marcar `[x]` en `CHANGES.md` y abrir el PR final revisado por otra persona.
