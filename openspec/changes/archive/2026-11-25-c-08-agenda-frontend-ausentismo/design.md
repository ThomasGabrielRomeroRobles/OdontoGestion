# Design

## Context

- **Estado del repo (`main` al 2026-11-22)**:
  - Están cerrados C-01, C-02 y C-06.
  - El frontend ya tiene shell, router, sesión, cliente HTTP con CSRF y `ProblemDetail`, y la feature `config`, que incluye `zoned-time.ts` y las APIs de prestaciones y consultorio.
  - `/agenda` y `/pacientes` son rellenos (`placeholderRoute`).
- **Dependencias de backend**:
  - **C-07 no empezó**: no hay endpoints de turnos ni DTOs publicados.
  - **C-04** está archivado en su rama, pero sin mergear. Su contrato de pacientes (`GET /pacientes?dni=`, `POST /pacientes`, `PacienteResponse`) es estable y se toma tal cual.
  - El backend de ausentismo de C-08 (P3) depende de C-07.
- **Restricciones del router**: compara el path exacto y no tiene parámetros de ruta. Lo que varía dentro de una pantalla va en la query (`context.url`).
- **Restricciones del proyecto**:
  - TS strict, sin `any` ni frameworks (reglas 13 y 14).
  - Fechas en UTC en la API y mostradas en `America/Argentina/Buenos_Aires` (regla 7).
  - Datos ficticios (regla 12).
  - TDD con Vitest y jsdom.
- Motivación: ver `proposal.md` (sección Why).

## Goals / Non-Goals

**Goals:**
- Dejar la agenda completa y probada contra un **contrato provisional v0** explícito, de modo que la integración con C-07 y P3 sea sustituir la implementación de un puerto y ajustar diferencias puntuales.
- Que toda regla de habilitación de acciones (estados, tolerancia, 24 h) sea una función pura con tests deterministas y reloj inyectado.
- Que el modo mocks permita demostrar la agenda en el navegador sin backend.

**Non-Goals:**
- Reimplementar reglas del servidor como autoridad. El cliente solo **habilita u oculta** acciones y la API decide: `409`/`422` se muestran tal como llegan.
- Una grilla de calendario con posicionamiento por píxeles o arrastrar y soltar. Las vistas día y semana son listas ordenadas por hora (D4).
- Actualización en tiempo real (polling o websockets). La agenda se recarga después de cada acción propia y con un botón "Actualizar". RN-NO-08, "la agenda se actualiza inmediatamente", se cumple en la próxima carga.
- Pantalla de pacientes y acceso a la historia clínica: sin cambios.

## Decisions

### D1. Contrato provisional v0 (C-07 + backend de C-08)

Lo fija este change a partir de `CHANGES.md` (bloques C-07 y C-08) y de la KB (`04` §turno, RN-TU, RN-AS). Convenciones: JSON en camelCase, instantes ISO-8601 UTC (`...Z`), errores `ProblemDetail`. **Se acuerda con P1/P2/P3 en su `/opsx:propose` de C-07**. Las diferencias se resuelven en el grupo de integración de `tasks.md`.

**`TurnoResponse`**
```jsonc
{
  "id": 41,
  "paciente":   { "id": 7, "nombre": "Lucía", "apellido": "Gómez", "dni": "30111222", "cantidadAusencias": 1 },
  "prestacion": { "id": 3, "nombre": "Limpieza", "duracionMin": 45 },
  "inicio": "2026-11-24T12:00:00Z",
  "fin":    "2026-11-24T12:45:00Z",
  "estado": "RESERVADO",            // RN-TU-01: PENDIENTE_CONFIRMACION | RESERVADO | CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO | EXPIRADO
  "origen": "ODONTOLOGO",           // PUBLICA | ODONTOLOGO | LISTA_ESPERA
  "pendienteHasta": null,           // solo en PENDIENTE_CONFIRMACION
  "motivoCancelacion": null,
  "canceladoPor": null,             // ODONTOLOGO | PACIENTE | SISTEMA
  "ausenteMarcadoEn": null          // instante en que se marcó AUSENTE (para la ventana de 24 h de RN-AS-06)
}
```

**Endpoints** (todos con sesión; las escrituras llevan CSRF a través del cliente existente):

| Método y ruta | Cuerpo / query | Éxito | Errores relevantes | Dueño |
|---|---|---|---|---|
| `GET /agenda` | `desde`, `hasta` (instantes) | `200 TurnoResponse[]` ordenado por `inicio`, **todos los estados** | `422` rango inválido | P1 |
| `GET /agenda/disponibilidad` | `prestacionId`, `fecha` (`yyyy-MM-dd` del consultorio), `excluirTurnoId?` | `200 [{inicio, fin}]` | `422`, `404` prestación | P1 |
| `GET /turnos/hoy` | — | `200 TurnoResponse[]` del día del consultorio | — | P2 |
| `POST /turnos` | `{pacienteId, prestacionId, inicio}` | `201 TurnoResponse` (`RESERVADO`) | `409` + `alternativas`, `422` fuera de horario o bloqueo, `404` | P1 |
| `PUT /turnos/{id}/mover` | `{inicio}` | `200 TurnoResponse` | `409` + `alternativas`, `422` estado o horario, `404` | P2 |
| `POST /turnos/{id}/cancelar` | `{motivo?}` (≤ 200) | `200 TurnoResponse` (`CANCELADO`) | `422` transición, `404` | P2 |
| `POST /turnos/{id}/atendido` | — | `200 TurnoResponse` (`ATENDIDO`) | `422` transición, `404` | P2 |
| `POST /turnos/{id}/ausente` | — | `200 TurnoResponse` (`AUSENTE`) | `422` antes de la tolerancia o por transición, `404` | P3 |
| `POST /turnos/{id}/revertir-ausencia` | — | `200 TurnoResponse` (estado previo) | `422` fuera de 24 h o no ausente, `404` | P3 |
| `GET /pacientes/{id}/ausencias` | — | `200 {cantidad, ausencias: [{turnoId, inicio, prestacionNombre}]}` (más reciente primero) | `404` | P3 |

- **Cuerpo del `409`** de `POST /turnos` y de `mover`: `ProblemDetail` con el miembro de extensión `alternativas: [{inicio, fin}]`. Puede ser vacío.
- **`excluirTurnoId`**: al mover, el propio turno no debe ocupar su hueco en el cálculo. Así se puede correr un turno 15 min.
- **Ya reales, se consumen tal cual**:
  - Pacientes (C-04): `GET /pacientes?dni=`, `POST /pacientes` (→ `409` DNI duplicado, `400` con campos).
  - C-06/C-02: `GET /prestaciones` y `GET /consultorio` (`parametros.toleranciaAusenteMin`, `granularidadMin`).
- Alternativa descartada: esperar el contrato de C-07. Bloquearía a P5 hasta G4 y llevaría a C-08 al camino crítico.

### D2. Puerto `AgendaApi` con tres implementaciones

```
            +---------------------+
 vistas --> |  AgendaApi (tipo)   | <-- PacientesApi (tipo)
            +---------------------+
              |        |        |
   agenda-api.ts   agenda-mocks.ts   dobles de test
   (apiRequest)    (DEV, memoria)    (Vitest)
```

- Las vistas reciben `AgendaApi`, `PacientesApi`, `obtenerConsultorio`, `listarPrestaciones` y `ahora: () => Date` por parámetro, igual que en C-06 (D5). Los tests de vista inyectan dobles. Los tests de `agenda-api.ts` mockean `fetch` y fijan método, URL, query, cuerpo y CSRF del contrato v0.
- **Por qué**: el día de la integración el cambio queda acotado a `agenda-api.ts` y a los tipos. Las vistas y el modelo no se tocan salvo que cambie la semántica.

### D3. Estructura de la feature

```
features/agenda/
  agenda-api.ts        # tipos del contrato v0 + AgendaApi real sobre apiRequest
  pacientes-api.ts     # búsqueda por DNI y alta (contrato real de C-04)
  modelo.ts            # puro: estados -> etiqueta/clase, acciones habilitadas, ventanas de tiempo
  calendario.ts        # puro: rango de día/semana en zona del consultorio, agrupar por día, formatos
  errores.ts           # puro: ApiError -> {mensaje, alternativas, recargar}
  index.ts             # createAgendaRoutes(deps)
  pages/
    agenda-page.ts     # /agenda: barra (vista, fechas, actualizar, nuevo turno) + lista
    hoy-page.ts        # /agenda/hoy
    turno-detalle.ts   # panel del turno: datos, acciones, ausencias del paciente
    nuevo-turno.ts     # flujo de creación
    mover-turno.ts     # elegir fecha y hueco nuevo
    cancelar-turno.ts  # motivo + confirmación
    ausencias.ts       # componente reutilizable (cantidad + fechas)
dev/agenda-mocks.ts    # implementación en memoria (solo DEV)
```

- Se reutiliza `features/config/zoned-time.ts` (`ZONA_CONSULTORIO`, conversiones). No se duplica.
- Las funciones de calendario que falten se agregan en `calendario.ts` sobre la misma técnica `Intl`.

### D4. Rutas y vistas

- `/agenda?vista=dia|semana&fecha=yyyy-MM-dd`. Por defecto: `semana` y la fecha de hoy en la zona del consultorio. La semana va de lunes a domingo.
  - "Anterior", "Siguiente" y "Hoy" navegan con `navigate`, así que el historial y la URL compartible funcionan.
  - Parámetros inválidos → se normalizan a los valores por defecto (`replace`).
- `/agenda/hoy`: ruta privada aparte, con un enlace desde la barra de la agenda. No se agrega enlace en el layout, porque `web-app-shell` no cambia.
- **Vistas como listas**: la de día es una lista por hora y la de semana, siete columnas con la lista de cada día. Cada ítem muestra hora inicio–fin, apellido y nombre, prestación y una etiqueta de estado. Hay una clase CSS por estado (`turno--reservado`, etc.) y una **etiqueta de texto** visible (accesibilidad: el estado no se comunica solo con color).
- `CANCELADO` y `EXPIRADO` se ocultan por defecto, con un control "Mostrar cancelados y expirados". Así se evita que un hueco reasignado se vea duplicado.
- Alternativa descartada: grilla horaria con alturas proporcionales. Cuesta más (solapes visuales, CSS de posicionamiento) y no lo exige ningún criterio de aceptación.

### D5. Reglas de habilitación como funciones puras (`modelo.ts`)

| Acción | Estados (RN-TU-02) | Condición temporal (cliente, orientativa) |
|---|---|---|
| Mover | `RESERVADO`, `CONFIRMADO` | `inicio > ahora` |
| Cancelar | `PENDIENTE_CONFIRMACION`, `RESERVADO`, `CONFIRMADO` | — |
| Marcar atendido | `RESERVADO`, `CONFIRMADO` | — |
| Marcar ausente | `RESERVADO`, `CONFIRMADO` | `ahora >= inicio + toleranciaAusenteMin` (RN-AS-01) |
| Revertir ausencia | `AUSENTE` | `ausenteMarcadoEn != null` y `ahora < ausenteMarcadoEn + 24 h` (RN-AS-06) |

- Los estados finales (`CANCELADO`, `EXPIRADO`, `ATENDIDO`, y `AUSENTE` fuera de la ventana) no ofrecen acciones.
- El reloj es `ahora: () => Date`, inyectado. Los tests fijan instantes en el borde (justo antes y justo en el límite).
- Si `GET /consultorio` falla, la tolerancia cae a `0`: el botón de ausente se habilita desde el inicio y el `422` del servidor decide. Es el mismo criterio que C-06 D8: el cliente nunca es la autoridad.
- La condición "mover solo turnos futuros" es una suposición del cliente (la KB no lo prohíbe explícitamente). Si P2 la descarta, se ajusta en una línea.

### D6. Flujo de creación (US-021) y de mover (US-022)

```
[DNI] --buscar--> encontrado? --si--> [paciente elegido]
                      |no
                      v
              [alta rápida: nombre, apellido, DNI, teléfono, email]
                      |  409 DNI duplicado -> vuelve a buscar y lo selecciona
                      v
[prestación activa] -> [fecha] -> GET disponibilidad -> [huecos como botones]
                      |
                      v
               POST /turnos --201--> cierra, recarga agenda en la fecha del turno
                      |
                      +--409--> mensaje + botones con `alternativas` + refresca huecos
                      +--422--> mensaje (detail) sin perder lo cargado
```

- Mover reutiliza el selector "fecha → huecos" con la prestación del turno y `excluirTurnoId`, y maneja el `409` de la misma manera.
- La validación de cliente del alta rápida replica el formato de C-04 (DNI de 7 u 8 dígitos con puntos opcionales, email, campos obligatorios). Los `400` del servidor se muestran por campo con `form-errors` de C-06.

### D7. Traducción uniforme de errores (`errores.ts`)

| Error | Mensaje | Efecto |
|---|---|---|
| `409` con `alternativas` | `detail` o "Ese horario ya no está disponible." | Muestra las alternativas como opciones |
| `422` | `detail`; si falta, mensaje propio del `type` (`ausente-antes-de-tolerancia`, `reversion-fuera-de-plazo`, `transicion-invalida`, ver D12); si no, `title` | En acciones sobre un turno: recarga la agenda (el estado pudo cambiar) |
| `404` | "El turno ya no existe." | Cierra el panel y recarga |
| red | "No se pudo conectar. Reintentá." | Conserva lo cargado |
| `401` | — | Lo resuelve el cliente HTTP global (redirección a login) |

### D8. Miembros de extensión del `ProblemDetail` en el cliente HTTP

- `ApiError` de tipo `http` suma `extensions: Readonly<Record<string, unknown>>`, con los miembros del cuerpo que no son `type/title/status/detail/instance/errors`. Si no hay ninguno, es `{}`.
- `errores.ts` hace el *narrowing* de `extensions['alternativas']` a `{inicio, fin}[]` y descarta los elementos mal formados (sin `any`).
- Alternativa descartada: un campo `alternativas` específico en el cliente genérico, porque acopla `shared/http` a la agenda. La otra opción, un `fetch` propio en la agenda, duplica CSRF y 401.
- Es un cambio compatible: los consumidores actuales no leen `extensions`.

### D9. Ausencias del paciente (US-061)

- El panel de detalle del turno incluye el componente `ausencias`: "Ausencias: N" y la lista de fechas en hora del consultorio (`GET /pacientes/{id}/ausencias`). Se carga al abrir el panel y se recarga después de marcar o revertir una ausencia.
- El componente queda exportado para que la futura pantalla de pacientes lo monte sin cambios. Esa pantalla hoy no existe y su carril no está asignado.
- La cantidad también se muestra resumida en el ítem de la agenda cuando es mayor que 0 (por ejemplo "2 aus."), para dar contexto al agendar (US-061, "tener contexto").

### D10. Modo mocks de desarrollo

- Se reutiliza el flag existente `VITE_CONFIG_MOCKS=true` (`npm run dev` con mocks). Un solo interruptor activa todos los mocks de desarrollo, y renombrarlo rompería la costumbre del equipo. Su nombre y su descripción se actualizan en `frontend/README.md`.
- `agenda-mocks.ts` comparte el estado de prestaciones y consultorio con `config-mocks.ts`: recibe esas funciones al construirse. Además mantiene en memoria pacientes ficticios, turnos de la semana actual en todos los estados y ausencias.
- Reproduce las reglas del contrato:
  - Superposición `[inicio, fin)` solo entre estados activos, con `409` y alternativas (los 3 huecos libres más cercanos).
  - Transiciones válidas, y `422` en las inválidas.
  - Tolerancia de ausente y ventana de 24 h, con el reloj real.
  - Contador de ausencias.
- Carga con `import()` detrás de `import.meta.env.DEV`, así que queda fuera del build de producción, igual que C-06 D9.

### D11. Qué queda hecho ahora y qué queda pendiente de integración

| Ítem | Ahora (contra mocks y dobles) | Pendiente de integración real | Bloqueado por |
|---|---|---|---|
| Vistas día, semana y hoy, colores y etiquetas | Completo y testeado | Verificar contra `GET /agenda` y `GET /turnos/hoy` reales | C-07 (P1/P2) |
| Crear turno, disponibilidad, `409` con alternativas | Completo y testeado | Confirmar la forma de `alternativas`, el parámetro `fecha` y `excluirTurnoId` | C-07 (P1) |
| Búsqueda y alta rápida de paciente | Completo, contra el contrato **real** de C-04 | Prueba manual cuando C-04 esté en `main` | Merge de C-04 (P4) |
| Mover, cancelar, atendido | Completo y testeado | Confirmar rutas, cuerpos y estado previo en `422` | C-07 (P2) |
| Marcar ausente y tolerancia | Completo y testeado | Confirmar el `422` antes de la tolerancia y el DTO | Backend C-08 (P3) |
| Revertir ausencia (24 h) | Completo y testeado | Confirmar la ruta `revertir-ausencia`, el campo `ausenteMarcadoEn` y el estado al revertir | Backend C-08 (P3) |
| Historial de ausencias | Completo y testeado | Confirmar la forma de `GET /pacientes/{id}/ausencias` | Backend C-08 (P3) |
| Cliente HTTP `extensions` | Completo (real) | — | — |
| Sincronizar `agenda-api.ts` con los DTOs finales y correr la demo end to end | — | Grupo 9 de `tasks.md` | C-07 + P3 archivados |

**Regla de cierre**: el change se puede mergear parcial a `main` como "front contra mocks". Con el flag apagado, las rutas usan la API real y muestran el error de conexión o `404` hasta que exista el backend, sin romper nada. Pero **no se archiva ni se marca `[x]` en `CHANGES.md`** hasta completar el grupo 9.

### D12. Diferencias con el contrato real (tarea 9.1)

Comparación del contrato v0 (D1) con el código real: C-07 (`TurnoController`, `AgendaController`, `TurnoDto`) y el backend de ausentismo de C-08 de P3 (rama `feat/c-08-ausentismo-backend`: `TurnoController#ausente`/`#revertirAusencia`, `PacienteController#ausencias`, `AusenciasPacienteResponse`, excepciones de dominio).

| Punto | Contrato v0 (D1) | Real | Ajuste en el front |
|---|---|---|---|
| `paciente.dni` en el turno | Venía | **No viene** (regla dura #10) | Fuera del tipo; el detalle no muestra DNI |
| `paciente.cantidadAusencias` | Venía | Viene, `int` obligatorio (`TurnoDto.PacienteResumen`) | Tipo `number` obligatorio; sin `?? 0` |
| `prestacion.duracionMin` | Venía | No viene (`{id, nombre}`) | Duración = `fin - inicio` |
| `version` | No estaba | Viene (`long`) | Tipo `number`, solo informativo |
| `ausenteMarcadoEn` | Venía | Viene, `Instant` o `null` | Tipo `string \| null` obligatorio |
| `GET /agenda` | `desde`/`hasta` instantes | `desde`/`hasta` fechas `yyyy-MM-dd` inclusivas | `fechasInclusivas` |
| `GET /agenda/disponibilidad` | `fecha`, `excluirTurnoId?` | `desde`/`hasta` fechas, **sin** `excluirTurnoId` | `desde = hasta = fecha`; al mover, el rango propio sigue ocupado (delta actualizado) |
| `409` | Solo `hueco-no-disponible` + `alternativas` | También `modificacion-concurrente` sin `alternativas` | Se distinguen por el miembro `alternativas` |
| `POST /turnos/{id}/ausente` | `200 TurnoResponse` | Igual; `422` `.../transicion-invalida` (se chequea primero) o `.../ausente-antes-de-tolerancia` | Mensaje de respaldo por `type` (D7) |
| `POST /turnos/{id}/revertir-ausencia` | Estado previo (pregunta abierta) | **Estado previo real** (`estadoPrevioAusencia`: `RESERVADO` o `CONFIRMADO`); `422` `.../transicion-invalida` si no está `AUSENTE`, `.../reversion-fuera-de-plazo` a partir de las 24 h | La UI muestra el estado devuelto (escenario nuevo en el delta) |
| `GET /pacientes/{id}/ausencias` | `{cantidad, ausencias: [{turnoId, inicio, prestacionNombre}]}` | Igual, la más reciente primero | Sin cambios |
| `type` de los `422` | `about:blank` implícito | `https://odontogestion.example/problemas/<codigo>`, title "Regla de negocio incumplida" | `ApiError.code` (último segmento) + `MENSAJES_422` en `errores.ts` si falta `detail` |

Los mocks de desarrollo (`dev/agenda-mocks.ts`) reproducen el contrato real: mismos campos, mismo `code` y mismo `detail` en cada `422`, y reversión al estado previo real.

## Risks / Trade-offs

- [El contrato v0 difiere del que implementen P1/P2/P3] → Se comparte este `design.md` (D1) como insumo del `/opsx:propose` de C-07. Las diferencias quedan en un solo módulo (D2), y el grupo 9 de tareas existe para eso.
- [Lógica de reglas duplicada en el cliente (estados, tolerancia, 24 h)] → Solo habilita botones. El servidor es la autoridad y sus `422` se muestran (D5, D7).
- [Mocks que se alejan de la realidad] → Los mocks implementan el mismo contrato que fijan los tests de `agenda-api.ts`. En la integración, los mocks se ajustan junto con los tipos.
- [C-04 sin mergear] → El contrato de pacientes ya está archivado en su rama. Si cambia antes del merge, se ajusta `pacientes-api.ts`.
- [Zona horaria del navegador distinta de la del consultorio] → Todo cálculo de día y semana usa `ZONA_CONSULTORIO` con `Intl`, y los tests corren con otra `TZ` de proceso, como en C-06 D7.
- [Lista en lugar de grilla] → Se ve menos "calendario". A cambio, el riesgo y el costo son menores con la entrega el 25/11. La grilla queda como mejora de UI en C-12 si sobra tiempo.

## Migration Plan

- No hay migraciones ni despliegue: es solo frontend.
- Se puede mergear a `main` sin backend (D11). El rollback es revertir el merge, y `/agenda` vuelve a ser el relleno.

## Open Questions

Estas preguntas no cambian los specs ni las tareas: se resuelven con P1/P2/P3 en la propuesta de C-07 y se ajustan en el grupo 9.

- ¿A qué estado vuelve un turno al revertir la ausencia (`RESERVADO` o `CONFIRMADO`, según el estado previo)? El front solo muestra lo que devuelve la API.
- ¿`GET /agenda` incluye `CANCELADO`/`EXPIRADO`, o se filtra en el servidor? El front tolera ambas opciones, porque los oculta por defecto.
- ¿PA-06 (si `ATENDIDO` y `AUSENTE` ocupan rango) cambia qué huecos devuelve la disponibilidad? Al front no le afecta.
