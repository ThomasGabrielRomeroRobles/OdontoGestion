# Tasks

> TDD estricto: en cada tarea "RED â†’ GREEN", primero se escribe el test y se ejecuta para confirmar que falla por la razÃ³n esperada. DespuÃ©s se implementa lo mÃ­nimo para que pase, se triangula y se refactoriza.
>
> **Alcance de las tareas**:
> - Este change es solo frontend (P5, Juan Ignacio).
> - Los grupos 1â€“8 se completan **ahora**, contra dobles inyectados, `fetch` mockeado y los mocks de desarrollo (design D2, D10).
> - El grupo 9 estÃ¡ **bloqueado** hasta que C-07 (P1/P2) y el backend de ausentismo de C-08 (P3) estÃ©n archivados en `main`, y C-04 mergeado (design D11).
>
> **Comandos** (en `frontend/`): `npm test`, `npm run typecheck` y `npm run build`.
>
> **Reglas que aplican a todas las tareas**:
> - Sin `any`.
> - Sin librerÃ­as nuevas.
> - Datos ficticios.
> - Fechas mostradas con `ZONA_CONSULTORIO`.
> - Los tests de calendario y vistas corren ademÃ¡s con `TZ` de proceso distinta (`vi.stubEnv('TZ', 'Asia/Tokyo')`, como en C-06).

## 1. Cliente HTTP: miembros de extensiÃ³n del ProblemDetail (`web-http-client`)

- [x] 1.1 RED: en `shared/http/client.test.ts`, agregar los escenarios "Miembros de extensiÃ³n conservados" (`409` con `alternativas`, sin repetir `title` ni `status`) y "Sin miembros de extensiÃ³n" (`extensions` es `{}`). Agregar tambiÃ©n que un error no `ProblemDetail` (502 HTML) tiene `extensions` `{}`. Confirmar que fallan
- [x] 1.2 GREEN: sumar `extensions: Readonly<Record<string, unknown>>` al `ApiError` de tipo `http` y extraerlo en `toHttpError` (design D8). Actualizar los dobles que construyen `ApiError` (`dev/config-mocks.ts` y los tests de `features/config`). Verificar con `npm test` en verde y `npm run typecheck` sin errores

## 2. Contrato v0: APIs de agenda y pacientes

- [x] 2.1 RED: en `features/agenda/agenda-api.test.ts`, con `fetch` mockeado, fijar para cada operaciÃ³n del contrato v0 (design D1) el mÃ©todo, la URL con query codificada, el cuerpo JSON, la presencia de `X-XSRF-TOKEN` en escrituras y el tipo devuelto:
  - `listarAgenda(desde, hasta)`
  - `disponibilidad(prestacionId, fecha, excluirTurnoId?)` (sin el parÃ¡metro cuando no viene)
  - `turnosHoy`, `crearTurno`, `moverTurno`, `cancelarTurno(id, motivo?)`, `marcarAtendido`, `marcarAusente`, `revertirAusencia`, `ausenciasPaciente`

  Confirmar que fallan
- [x] 2.2 GREEN: crear `features/agenda/agenda-api.ts` con los tipos `TurnoResponse`, `EstadoTurno`, `Hueco`, `AusenciasPaciente` y `CrearTurnoInput`, el tipo `AgendaApi` y su implementaciÃ³n sobre `apiRequest`. Verificar que 2.1 pasa
- [x] 2.3 RED â†’ GREEN: crear `features/agenda/pacientes-api.ts` con el contrato real de C-04 (`buscarPorDni(dni)` â†’ `GET pacientes?dni=`, `crearPaciente(input)` â†’ `POST pacientes`, tipo `PacienteResponse` de 10 campos y tipo `PacientesApi`). Verificar con `pacientes-api.test.ts` (mÃ©todo, URL, cuerpo y CSRF)

## 3. Modelo puro: estados, acciones, calendario y errores

- [x] 3.1 RED â†’ GREEN: en `features/agenda/modelo.ts`, la etiqueta y la clase CSS de cada uno de los 7 estados. Verificar con `modelo.test.ts`: las 7 etiquetas son distintas (por ejemplo "Pendiente de confirmaciÃ³n", "Reservado", "Cancelado", "Atendido", "Ausente") y las 7 clases tambiÃ©n
- [x] 3.2 RED â†’ GREEN: `accionesHabilitadas(turno, ahora, toleranciaMin)` segÃºn la tabla de design D5. Verificar con `modelo.test.ts`:
  - Cada estado tiene el conjunto exacto de acciones.
  - "Mover" desaparece con `inicio <= ahora`.
  - "Ausente" a las 09:14 da deshabilitado con "Disponible desde las 09:15", y a las 09:15 da habilitado.
  - "Revertir" a las 23 h 59 min sÃ­ aparece; a las 24 h y con `ausenteMarcadoEn` nulo, no.
  - Con tolerancia 0 (fallback), "Ausente" estÃ¡ habilitado desde el inicio.
- [x] 3.3 RED â†’ GREEN: en `features/agenda/calendario.ts`, sobre `zoned-time.ts`:
  - `rangoDia(fecha)` y `rangoSemana(fecha)` (lunes a domingo) en instantes UTC.
  - `parsearVistaYFecha(query, hoy)`, con normalizaciÃ³n de invÃ¡lidos.
  - `agruparPorDia(turnos)`, `fechaLocal(instante)`, `horaLocal(instante)` y los desplazamientos de dÃ­a y de semana.

  Verificar con `calendario.test.ts` y `TZ=Asia/Tokyo`:
  - El rango del 24/11 es `03:00Z`â€“`03:00Z` del dÃ­a siguiente.
  - La semana del miÃ©rcoles 25/11 empieza el lunes 23.
  - `2026-11-25T01:00Z` cae el martes 24 a las 22:00.
  - `vista=mes&fecha=2026-13-40` da semana y hoy, marcado como normalizado.
- [x] 3.4 RED â†’ GREEN: en `features/agenda/errores.ts`, `traducirErrorAccion(error)` segÃºn la tabla de design D7. Verificar con `errores.test.ts`:
  - `409` con `alternativas` vÃ¡lidas da mensaje y alternativas.
  - `409` con alternativas mal formadas descarta esos elementos.
  - `409` sin alternativas da una lista vacÃ­a.
  - `422` usa el `detail` (o el `title`) y marca `recargar`.
  - `404` da "El turno ya no existe." con `cerrar` y `recargar`.
  - Red da "No se pudo conectar. ReintentÃ¡."

## 4. Vista `/agenda`: dÃ­a, semana y navegaciÃ³n (`web-agenda`)

- [x] 4.1 RED: en `pages/agenda-page.test.ts`, con dobles de `AgendaApi` y reloj fijo, cubrir los escenarios de los requisitos:
  - "Acceso a la agenda": valores por defecto y parÃ¡metros invÃ¡lidos con `replace`.
  - "Turnos agrupados por dÃ­a del consultorio": rango pedido, cerca de medianoche, orden y "Sin turnos".
  - "NavegaciÃ³n por fechas y vistas": siguiente, anterior y cambio de vista.

  Confirmar que fallan
- [x] 4.2 GREEN: implementar `pages/agenda-page.ts`, con barra (DÃ­a/Semana, Anterior/Siguiente/Hoy, Actualizar, Nuevo turno, enlace a `/agenda/hoy`) y listas por dÃ­a (design D4). Verificar que 4.1 pasa
- [x] 4.3 RED â†’ GREEN: requisitos "Estado del turno visible" (contenido del Ã­tem y estilos por estado), "Cancelados y expirados ocultos por defecto" (control "Mostrar cancelados y expirados") e indicador "N aus." de "Historial de ausencias del paciente". Verificar con nuevos casos en `agenda-page.test.ts`
- [x] 4.4 RED â†’ GREEN: escenario "Falla al cargar la agenda" (mensaje de conexiÃ³n y "Reintentar", que repite la solicitud). Verificar en `agenda-page.test.ts`
- [x] 4.5 RED â†’ GREEN: crear `features/agenda/index.ts` con `createAgendaRoutes(deps)` (ruta privada `/agenda`) y reemplazar el `placeholderRoute('/agenda', â€¦)` en `bootstrap.ts`, con las deps inyectables en `BootstrapDeps` (por defecto, las APIs reales). Verificar con `bootstrap.test.ts`: `/agenda` renderiza la agenda y sin sesiÃ³n redirige a `/login?next=â€¦` (escenario "Sin sesiÃ³n"). El resto de los tests de `bootstrap` sigue en verde

## 5. Detalle del turno: acciones simples y ausencias (`web-agenda`)

- [x] 5.1 RED â†’ GREEN: crear `pages/ausencias.ts` (componente reutilizable, design D9). Verificar con `ausencias.test.ts` los escenarios "Paciente con ausencias" (fechas en hora del consultorio, mÃ¡s reciente primero) y "Sin ausencias"
- [x] 5.2 RED â†’ GREEN: crear `pages/turno-detalle.ts`, el panel que se abre al activar un turno, con sus datos, el componente de ausencias y los botones de `accionesHabilitadas`. La tolerancia sale de `obtenerConsultorio` (fallback 0). Verificar con `turno-detalle.test.ts` los escenarios del requisito "Acciones habilitadas segÃºn el estado" y "Antes de la tolerancia" / "Justo en la tolerancia"
- [x] 5.3 RED â†’ GREEN: requisitos "Marcar un turno como atendido", "Marcar ausente tras la tolerancia" (exitoso y rechazo `422`) y "Revertir una ausencia dentro de 24 horas". DespuÃ©s de cada acciÃ³n se recargan la vista y el historial de ausencias. Verificar con casos en `turno-detalle.test.ts` que comprueban la solicitud enviada y el estado mostrado
- [x] 5.4 RED â†’ GREEN: crear `pages/cancelar-turno.ts`, con motivo opcional, lÃ­mite de 200 caracteres, confirmaciÃ³n y "Volver". Verificar con `cancelar-turno.test.ts` los escenarios "CancelaciÃ³n con motivo", "Desistir de cancelar", "Motivo demasiado largo" y "Falla de red al cancelar" (el motivo se conserva)
- [x] 5.5 RED â†’ GREEN: requisito "Errores al operar un turno", escenarios "TransiciÃ³n invÃ¡lida" (mensaje y recarga) y "Turno inexistente" (cierra y recarga). Verificar con casos en `turno-detalle.test.ts`

## 6. Crear turno: paciente, horarios libres y conflicto (`web-agenda`)

- [x] 6.1 RED â†’ GREEN: paso de paciente en `pages/nuevo-turno.ts` (bÃºsqueda por DNI con validaciÃ³n de cliente y alta rÃ¡pida que reutiliza `form-errors` de C-06). Verificar con `nuevo-turno.test.ts` los 6 escenarios de "Paciente del turno por DNI o alta rÃ¡pida" (incluido el `409` de DNI duplicado, que vuelve a buscar y selecciona)
- [x] 6.2 RED â†’ GREEN: selector reutilizable "fecha â†’ huecos" (`pages/selector-hueco.ts`), con prestaciones activas desde `listarPrestaciones` y huecos de `disponibilidad` en hora del consultorio. Verificar con `selector-hueco.test.ts` los escenarios "Solo prestaciones activas", "Huecos de la fecha" y "Sin huecos"
- [x] 6.3 RED â†’ GREEN: confirmaciÃ³n con `POST /turnos`, que ante `201` cierra y navega a `/agenda?vista=dia&fecha=<dÃ­a del turno>`, y ante `422` conserva lo elegido. Verificar con los escenarios de "CreaciÃ³n del turno" en `nuevo-turno.test.ts`
- [x] 6.4 RED â†’ GREEN: requisito "Conflicto de horario con alternativas" (con alternativas, elegir una y sin alternativas; en todos los casos se vuelve a pedir la disponibilidad). Verificar con casos en `nuevo-turno.test.ts`

## 7. Mover turno (`web-agenda`)

- [x] 7.1 RED â†’ GREEN: crear `pages/mover-turno.ts` sobre el selector de 6.2, con la prestaciÃ³n del turno y `excluirTurnoId`. Verificar con `mover-turno.test.ts` los escenarios "Huecos excluyendo el propio turno", "Movimiento exitoso" y un `409` con alternativas (mismo comportamiento que 6.4)

## 8. Turnos de hoy, mocks de desarrollo y documentaciÃ³n

- [x] 8.1 RED â†’ GREEN: crear `pages/hoy-page.ts` y registrar la ruta privada `/agenda/hoy` en `createAgendaRoutes`. Reutiliza el panel de detalle. Verificar con `hoy-page.test.ts` los escenarios "Lista del dÃ­a" y "AcciÃ³n desde hoy"
- [x] 8.2 RED â†’ GREEN: crear `dev/agenda-mocks.ts` (design D10), que recibe prestaciones y consultorio de `config-mocks` y trae pacientes, turnos de la semana en los 7 estados y ausencias, todo ficticio. Verificar con `agenda-mocks.test.ts`:
  - Un `crearTurno` superpuesto con un activo rechaza `409` con hasta 3 `alternativas`.
  - Un turno que termina a las 10:00 no choca con uno de las 10:00.
  - Un cancelado no ocupa el hueco.
  - `marcarAusente` antes de la tolerancia da `422`, y despuÃ©s suma 1 al contador.
  - `revertirAusencia` fuera de 24 h da `422`, y dentro resta 1.
  - Una transiciÃ³n invÃ¡lida da `422`.
  - `buscarPorDni` con puntos encuentra al paciente.
  - Un DNI duplicado en el alta da `409`.
- [x] 8.3 Cablear los mocks en `main.ts` detrÃ¡s de `import.meta.env.DEV && VITE_CONFIG_MOCKS === 'true'` (import dinÃ¡mico). Verificar que `npm run build` termina y que en `dist/` no aparece la palabra `agenda-mocks` ni los nombres ficticios del mock (`grep -r`)
- [x] 8.4 Actualizar `frontend/README.md`: quÃ© activa `VITE_CONFIG_MOCKS`, cÃ³mo probar la agenda con mocks y el estado "front contra contrato v0, pendiente de integraciÃ³n" con un enlace a design D1/D11. Verificar ejecutando el comando documentado (`VITE_CONFIG_MOCKS=true npm run dev`) y recorriendo crear, mover, cancelar, ausente, revertir y hoy en el navegador
- [x] 8.5 VerificaciÃ³n del grupo: `npm test`, `npm run typecheck` y `npm run build` en verde. AdemÃ¡s, `grep -rn "any" src/features/agenda src/dev/agenda-mocks.ts` sin usos del tipo `any`

## 9. IntegraciÃ³n real con C-07 y el backend de ausentismo

> PrecondiciÃ³n: C-07 y el backend de C-08 (P3) archivados en `main`, y C-04 mergeado. Hasta entonces este grupo no se empieza.

- [x] 9.1 Comparar los DTOs y rutas reales de C-07 y P3 con el contrato v0 (design D1). Listar las diferencias en `design.md` (secciÃ³n nueva "Diferencias con el contrato real") y ajustar `agenda-api.ts`, `pacientes-api.ts`, sus tests y `agenda-mocks.ts`. Verificar con `npm test` y `npm run typecheck` en verde
- [x] 9.2 Si alguna diferencia cambia comportamiento observable (por ejemplo, el estado al revertir o el parÃ¡metro de disponibilidad), actualizar el delta `specs/web-agenda/spec.md` y sus tests. Verificar con `openspec validate c-08-agenda-frontend-ausentismo --strict` sin errores
- [x] 9.3 Prueba manual end to end contra el backend real con el seed ficticio (`npm run dev`, con el proxy a `:8080`): crear (incluido un `409` provocado con dos pestaÃ±as), mover, cancelar, atendido, ausente pasada la tolerancia, revertir, historial y hoy. Verificar registrando el resultado de cada paso en el PR
- [x] 9.4 VerificaciÃ³n final: todos los tests del repo en verde (`npm test` en `frontend/` y `./gradlew test` en `backend/`) y `npm run build` sin errores

## Workflow follow-up

- El change se puede mergear a `main` como "front contra contrato v0" al cerrar el grupo 8, con PR revisado por otra persona. **No se archiva** hasta cerrar el grupo 9 (design D11).
- Compartir design D1 con Thomas (P1), Martina (P2) y JeremÃ­as (P3) como insumo del `/opsx:propose` de C-07 y del backend de C-08.
- Al cerrar el grupo 9: `/opsx:archive c-08-agenda-frontend-ausentismo` (coordinado con P3, que comparte C-08), marcar `[x]` en `CHANGES.md` y abrir el PR final.
