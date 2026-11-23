# Proposal

## Why

Cuando un turno se cancela o se mueve, el hueco queda vacío aunque haya pacientes que lo querrían: es la mitad del problema que ataca OdontoGestión (ausentismo y huecos de agenda). C-07 ya publica `HuecoLiberado` y C-03/C-09 dejaron listos tokens, outbox y acciones por enlace; C-10 cierra el circuito con una lista de espera que avisa a todos los compatibles a la vez y le da el turno al primero que acepta (RN-LE-01…08, US-040, US-042). Es parte del corte obligatorio (DD-16) y está en el camino crítico hacia C-12.

Por plazo (entrega 2026-11-25) el equipo acordó un **alcance mínimo**: se implementa el flujo completo de punta a punta, pero sin preferencias de franja horaria, sin filtros ni alta manual por el odontólogo y sin pulido de UI.

## What Changes

- Migración Flyway **V10**: tablas `entrada_lista_espera` y `aviso_hueco` (con `consultorio_id`, `timestamptz`, `@Version`), índice único parcial "una entrada activa o pendiente por paciente + prestación", y las FK `aviso_id` / `entrada_lista_espera_id` en `token_acceso` y `notificacion` diferidas desde C-03.
- `POST /api/v1/publico/lista-espera`: inscripción pública con el mismo anti-abuso de C-09 (límite por IP y por identidad, señuelo, validación estricta) y respuesta `202` genérica sin enumeración. La entrada nace `PENDIENTE_CONFIRMACION` y se encola el email `CONFIRMAR_LISTA_ESPERA`.
- Confirmación de la inscripción por el mecanismo existente `GET/POST /api/v1/acciones/{token}` con el propósito `CONFIRMAR_LISTA_ESPERA` → entrada `ACTIVA`.
- Manejador de `HuecoLiberado` (después del commit): busca entradas `ACTIVA` compatibles (misma prestación, fecha del hueco dentro del rango, anticipación mínima), crea un `aviso_hueco` por entrada y encola `AVISO_HUECO` a todos a la vez.
- `GET /api/v1/avisos/{token}` (resumen sin consumir) y `POST /api/v1/avisos/{token}/aceptar`: el primero que acepta gana (turno `RESERVADO`, origen `LISTA_ESPERA`), su entrada pasa a `CUBIERTA`, los demás avisos del hueco a `CERRADO`; los demás reciben `409`, enlace vencido o usado `410`.
- Vencimiento de avisos (`vencimiento_aviso_min` o inicio del hueco, lo que ocurra primero): perezoso al acceder y barrido periódico con el patrón de scheduler existente.
- Odontólogo: `GET /api/v1/lista-espera` (listado sin filtros) y `DELETE /api/v1/lista-espera/{id}` (→ `CANCELADA`).
- Frontend (TS vanilla): formulario público de inscripción, página `/avisos/:token`, soporte de `CONFIRMAR_LISTA_ESPERA` en `/acciones/:token` y pantalla de solo lectura con botón "Retirar" para el odontólogo.

**Fuera de alcance (etapa posterior):**
- Preferencia de franja horaria (`franja_desde` / `franja_hasta`) en la inscripción y en la compatibilidad.
- Filtros del listado del odontólogo y alta manual por el odontólogo (`POST /api/v1/lista-espera`, entrada que nace `ACTIVA`).
- Vencimiento de entradas por `fecha_hasta` (estado `VENCIDA` por fecha): en el MVP solo dejan de ser compatibles; tampoco hay job de depuración.
- Cancelación de la entrada por el paciente desde un enlace (RN-LE-09, segunda mitad).
- Compatibilidad por duración con otra prestación (RN-LE-02, variante "duración del hueco ≥ duración").
- Pulido visual de las pantallas nuevas.

## Capabilities

### New Capabilities
- `waiting-list`: inscripción pública y confirmación en la lista de espera, generación de avisos al liberarse un hueco, aceptación "el primero gana", vencimiento de avisos y gestión mínima por el odontólogo.
- `web-waiting-list`: vistas del frontend para inscribirse, aceptar un aviso y que el odontólogo consulte y retire entradas.

### Modified Capabilities
- `token-actions`: el resumen y la ejecución de `/acciones/{token}` admiten el propósito `CONFIRMAR_LISTA_ESPERA` (resumen de la entrada en lugar del turno).
- `web-token-actions`: la vista `/acciones/{token}` muestra y ejecuta la confirmación de la inscripción en la lista de espera.

## Impact

- **Backend**: nuevo paquete `listaespera` en `domain`, `application` y `api`; cambios aditivos en `AccionTokenService`/`ResumenAccionResponse` (C-09), `SolicitudReserva` (fábrica para `LISTA_ESPERA`), un scheduler nuevo en `infrastructure/scheduling`. Sin cambios en `TokenAccesoService`, `NotificationService`, `EmisorEnlacesConTokens`, `PlantillaNotificacion` ni `SecurityConfig` (ya contemplan avisos y lista de espera).
- **Base de datos**: V10 (tablas, índices, FK diferidas). `ddl-auto=validate`.
- **API**: 6 endpoints nuevos (2 públicos, 2 por token, 2 con sesión); `/acciones/{token}` amplía su contrato de forma compatible (`turno` puede ser `null` y aparece `entrada`).
- **Frontend**: feature nueva `lista-espera`, rutas `/lista-espera` (pública), `/avisos/:token` y una ruta privada para el odontólogo; ajuste del tipo `ResumenAccion` en `acciones-token`.
- **Governance ALTO**: el design debe ser aprobado por una persona antes de `/opsx:apply`.
