# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: `specs/runtime-configuration`, `specs/demo-environment`.

Lo que ya existe en `main` y C-12 **reutiliza sin modificar** (verificado en el código):

| Pieza | Dónde | Notas |
|---|---|---|
| Empaquetado del frontend | `backend/build.gradle.kts`: `./gradlew bootJar -PwithFrontend` corre `npm ci` + `npm run build` y copia `frontend/dist` a `build/resources/main/static` | Opt-in; sin la propiedad el build no necesita Node. Nunca escribe en `src/`. |
| Perfil `demo` | `application-demo.yml` (caché de recursos 1 h, log `INFO`); `DemoProfileIT` | Arranca, pero no tiene fallback de la SPA. |
| Seguridad | `SecurityConfig`: `/api/**` exige sesión salvo `auth/login`, `publico/**`, `acciones/**`, `avisos/**`; `anyRequest().permitAll()`; `DispatcherType.FORWARD` y `ERROR` permitidos | El fallback no necesita cambios de seguridad. |
| Router del frontend | `frontend/src/shared/router/router.ts` con `history.pushState` y `popstate` | Rutas reales: `/login`, `/agenda`, `/agenda/hoy`, `/lista-espera`, `/notificaciones`, `/publico`, `/publico/lista-espera`, `/acciones/:token`, `/avisos/:token`, etc. |
| Enlaces de email | `EmisorEnlacesConTokens` arma `APP_BASE_URL` + `/acciones/{token}` o `/avisos/{token}` | Hoy esas URLs dan `404` en el perfil `demo`. |
| Datos de referencia | V2 (consultorio ficticio), V4 (prestaciones y horarios ficticios), `BootstrapSeeder` (`@Order(100)`, odontólogo) | Próximo orden libre para seeds: después de `200` (reservado por C-06). |
| Dominio para el seed | `Paciente.nuevo(Consultorio, DatosPaciente)`; `Turno.reservado(...)`, `confirmar()`, `marcarAtendido()`, `marcarAusente(Instant, int)`; `PacienteRepository.incrementarAusencias(id)` | El seed persiste con repositorios desde un service, sin pasar por `TurnoComandos` (que publica eventos y encola emails). |
| SMTP de pruebas | GreenMail (`greenmail-junit5`, ya en `build.gradle.kts`); `EmailChannelContractTest` | Patrón para el smoke. |
| Reloj | `ClockConfig` (UTC, reemplazable en tests) | El seed calcula "semana en curso" con el `Clock` y `app.timezone`. |

## Goals / Non-Goals

**Goals:**
- Que la demo se levante con un comando documentado y que cualquier ruta del frontend o enlace de email funcione en `http://localhost:8080`.
- Datos ficticios suficientes para mostrar agenda, estados, contador de ausencias y turnos de hoy sin cargarlos a mano.
- Una prueba automática del recorrido principal (flujo 2), más la verificación manual registrada de los flujos 2, 3 y 5 con SMTP real.

**Non-Goals:**
- Playwright o tests de navegador.
- Un tope de envíos diarios en el código: se verifica que la cuenta alcance (SU-10) y se documenta.
- Pantallas nuevas de frontend (ficha del paciente, historia clínica).
- Despliegue público, HTTPS, Docker Compose.

## Decisions

### D1. Fallback de la SPA con un controlador de reenvío, no con un `ErrorController`
Un `@Controller` en `infrastructure/web` (`SpaFallbackController`) con `@GetMapping` sobre un patrón que excluye `/api` y los segmentos con punto, que hace `forward:/index.html`. Patrón con `PathPatternParser`: `{*ruta}` no sirve para excluir; se usan dos mapeos explícitos, `/{seg:[^\.]*}` y `/{seg:^(?!api$)[^\.]*}/**`, y se verifica en el método que la ruta no empiece con `/api/`.

- **Alternativas**: un `ErrorController` que convierte todo `404` en `index.html` (también tapa los `404` de la API y de los assets, contradice la spec); un `ResourceResolver` personalizado (más código, menos explícito).
- **Solo cuando hay frontend**: el controlador se registra con `@ConditionalOnResource(resources = "classpath:static/index.html")`. En `dev` (sin frontend empaquetado) no existe y las rutas siguen en `404`, como hoy. Los tests lo activan con un `index.html` de prueba en `src/test/resources/static/`.
- **Seguridad**: sin cambios (ver Context). El token de `/acciones/{token}` no se lee ni se valida en el reenvío; lo maneja el frontend contra la API.
- **Caché**: `index.html` sale sin caché de larga duración (`Cache-Control: no-cache`) para que un build nuevo se vea al recargar; los assets con hash mantienen la caché de 1 h del perfil `demo`.

### D2. Seed de demo como service + runner, activado por propiedad
- `DemoSeedService` (`application/demo`, `@Transactional`): si el consultorio actual tiene pacientes, no hace nada. Si no, crea los pacientes y turnos.
- `DemoSeeder` (`infrastructure/seed`, `ApplicationRunner`, `@Order(300)`, `@ConditionalOnProperty("app.seed-demo.enabled")`) solo llama al service (regla dura 3).
- `app.seed-demo.enabled=true` en `application-demo.yml`; ausente (falso) en el resto. Por propiedad y no por `@Profile` para poder probarlo y apagarlo con una variable si hace falta.
- **Sin eventos**: los turnos se crean con las fábricas del dominio y `TurnoRepository.save`, no con `TurnoComandos`, así no se publican `TurnoCreado` ni `TurnoAusente` y no se encolan emails a direcciones ficticias. Los flujos con email se muestran en vivo, con el email real de quien presenta, desde la página pública.
- **Contador de ausencias**: cada turno sembrado como `AUSENTE` se marca con `marcarAusente(...)` usando un instante posterior a la tolerancia, y se llama a `PacienteRepository.incrementarAusencias` dentro de la misma transacción.

### D3. Contenido del seed
- **Pacientes**: 10, con nombres y apellidos inventados, DNI de 8 dígitos ficticios en un rango fijo (`90000001`–`90000010`) y emails `pacienteNN@demo.example`. Teléfono ficticio. Sin datos clínicos.
- **Turnos**: alrededor de 15 en la semana en curso (lunes a domingo, zona `app.timezone`), dentro de los horarios de V4 y con prestaciones activas, calculados desde el `Clock`:
  - pasados: algunos `ATENDIDO` y 3 `AUSENTE` (repartidos en 2 pacientes, uno con 2 ausencias);
  - de hoy: al menos 2 (uno `CONFIRMADO`), para que `/agenda/hoy` muestre algo;
  - futuros: `RESERVADO` y `CONFIRMADO`.
- **Superposición**: los horarios se eligen en franjas fijas separadas; si igual choca (`EXCLUDE` de V9, por ejemplo con un bloqueo cargado a mano), ese turno se omite y se loguea en `WARN` sin datos personales. El seed nunca hace fallar el arranque.
- **Ventana del recordatorio**: los turnos sembrados para las próximas 24 h pueden disparar recordatorios cuando C-11 esté en `main`. Para no mandar emails a `.example`, ver Riesgos.

### D4. Smoke de punta a punta en el backend
`DemoSmokeIT` (`@ActiveProfiles("demo")`, Testcontainers, GreenMail en un puerto libre, `Clock` fijo):
1. `GET /api/v1/publico/disponibilidad` → toma el primer hueco.
2. `POST /api/v1/publico/solicitudes-turno` con datos ficticios (`.example`).
3. Corre el job del outbox (`ProcesadorNotificacionesJob`) de forma directa, sin esperar el `@Scheduled`.
4. Lee el email de GreenMail, extrae el token del enlace `/acciones/{token}`.
5. `POST /api/v1/acciones/{token}` con la acción de confirmar → turno `RESERVADO`.
6. (Grupo 5, bloqueado por C-11) avanza el reloj a la ventana del recordatorio, corre el scheduler de C-11 y espera un único email de recordatorio.

Se elige un test de backend y no Playwright porque ya existe toda la infraestructura (Testcontainers, GreenMail, `Clock`) y no suma dependencias de Node en CI.

### D5. Guía en un `README.md` en la raíz
No existe `README.md` en la raíz. Se crea con: requisitos (Java 21, Node LTS, PostgreSQL con `btree_gist`, Docker para tests), `.env` desde `.env.example`, levantar la demo (`./gradlew bootJar -PwithFrontend` y `java -jar ... --spring.profiles.active=demo`, más el equivalente con `run-local.ps1`), usuario del odontólogo, checklist de los flujos 2, 3 y 5, y cómo correr los tests (`./gradlew test`, `npm test`, `npm run build`). `backend/README.md` y `frontend/README.md` enlazan a esta guía en lugar de repetirla.

### D6. Verificaciones manuales registradas en el PR
- **SMTP real (SU-10)**: enviar al menos un email por la cuenta elegida (flujo 2) y anotar el límite diario de la cuenta y que alcanza para la demo (incluido el aviso a toda la lista de espera).
- **Secretos (regla 11)**: `git grep` de contraseñas, `MAIL_PASSWORD`, `DB_PASSWORD` con valor, y `git ls-files .env` vacío.
- **Logs y emails (regla 10)**: recorrer los flujos con el log en `INFO` y revisar que no aparezcan tokens, contraseñas ni datos clínicos; revisar el cuerpo de los emails recibidos.

## Risks / Trade-offs

- **[Recordatorios a emails ficticios]** Cuando C-11 entre, los turnos sembrados en las próximas 24 h van a disparar recordatorios a `@demo.example`. El envío falla y queda registrado como fallido en `notificacion`, lo que ensucia la pantalla de notificaciones. → Mitigación: se resuelve en el grupo 5 de `tasks.md`, según cómo C-11 marque un turno como recordado (por ejemplo, sembrar esos turnos como ya recordados o dejar los futuros fuera de la ventana).
- **[Patrón del fallback]** Un error en el patrón puede capturar `/api/...` y devolver HTML a la API. → Mitigación: los escenarios de `SpaFallbackIT` cubren `/api` inexistente y assets inexistentes.
- **[Datos de la semana]** Si la demo se presenta otro día, los turnos "de hoy" pueden no coincidir. → Con la base recreada el seed vuelve a calcular desde el reloj; la guía explica cómo recrear la base.
- **[Tiempo]** Es el último día. → Los grupos 1 (fallback) y 4 (guía) son los que más impacto tienen y no dependen de nadie; van primero.

## Migration Plan

Sin migraciones de base. Para la demo: recrear la base (o usar una vacía), poner `SPRING_PROFILES_ACTIVE=demo`, construir con `-PwithFrontend` y arrancar. Rollback: quitar `app.seed-demo.enabled` o volver a `dev`.

## Open Questions

- ¿Quién presenta y con qué cuenta SMTP? Define a qué email llegan los mensajes de los flujos en vivo.
- ¿Se quiere una pantalla de ficha de paciente con sus ausencias? Hoy queda cubierto por el detalle del turno (ver Supuestos en `proposal.md`).
