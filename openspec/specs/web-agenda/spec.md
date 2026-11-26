# web-agenda Specification

## Purpose
Define las pantallas con las que el odontólogo ve su agenda (día, semana y turnos de hoy) y opera los turnos desde la SPA: crearlos, moverlos, cancelarlos, marcarlos atendidos o ausentes, revertir una ausencia y consultar el historial de ausencias del paciente.

> Convención de los escenarios: la zona del consultorio es `America/Argentina/Buenos_Aires` (UTC−03:00). "Ahora" es el reloj de la aplicación. Todos los pacientes son ficticios.

## Requirements

### Requirement: Acceso a la agenda
La aplicación SHALL ofrecer la ruta privada `/agenda`, que muestra la agenda en vista `semana` y fecha de hoy (en hora del consultorio) cuando la URL no indica `vista` y `fecha` válidas, y MUST redirigir a login sin sesión.

#### Scenario: Valores por defecto
- **GIVEN** hay una sesión activa y ahora es `2026-11-25T13:00:00Z` (miércoles 10:00 en el consultorio)
- **WHEN** el usuario navega a `/agenda`
- **THEN** se muestra la vista semana del lunes 23/11/2026 al domingo 29/11/2026

#### Scenario: Parámetros inválidos
- **GIVEN** hay una sesión activa
- **WHEN** el usuario navega a `/agenda?vista=mes&fecha=2026-13-40`
- **THEN** la URL se reemplaza por la vista semana de la fecha de hoy, sin agregar una entrada al historial

#### Scenario: Sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a `/agenda?vista=dia&fecha=2026-11-24`
- **THEN** la aplicación redirige a `/login?next=` con la ruta original codificada

### Requirement: Turnos agrupados por día del consultorio
La agenda SHALL pedir los turnos del rango visible con `GET /api/v1/agenda?desde&hasta` (instantes UTC que cubren el día o la semana del consultorio) y ubicar cada turno en el día y la hora del consultorio, independientemente de la zona del navegador.

#### Scenario: Rango pedido para un día
- **WHEN** el usuario abre `/agenda?vista=dia&fecha=2026-11-24`
- **THEN** la solicitud es `GET /api/v1/agenda?desde=2026-11-24T03:00:00Z&hasta=2026-11-25T03:00:00Z`

#### Scenario: Turno cerca de medianoche UTC
- **GIVEN** la zona del navegador es UTC y la API devuelve un turno con inicio `2026-11-25T01:00:00Z`
- **WHEN** se renderiza la vista semana del 23/11/2026
- **THEN** el turno aparece en la columna del martes 24/11 a las 22:00

#### Scenario: Orden por hora
- **GIVEN** la API devuelve turnos del 24/11 a las 11:00 y a las 09:00 (hora del consultorio)
- **WHEN** se renderiza la vista día del 24/11
- **THEN** el turno de las 09:00 aparece antes que el de las 11:00

#### Scenario: Día sin turnos
- **GIVEN** la API devuelve una lista vacía
- **WHEN** se renderiza la vista día
- **THEN** se muestra el texto "Sin turnos"

### Requirement: Navegación por fechas y vistas
La agenda SHALL permitir cambiar entre vista día y semana, avanzar y retroceder un período e ir a hoy, reflejando cada cambio en la URL con el historial del navegador y sin recargar la página.

#### Scenario: Semana siguiente
- **GIVEN** se muestra `/agenda?vista=semana&fecha=2026-11-23`
- **WHEN** el usuario activa "Siguiente"
- **THEN** la URL pasa a `/agenda?vista=semana&fecha=2026-11-30` y se piden los turnos de esa semana

#### Scenario: Día anterior
- **GIVEN** se muestra `/agenda?vista=dia&fecha=2026-11-24`
- **WHEN** el usuario activa "Anterior"
- **THEN** la URL pasa a `/agenda?vista=dia&fecha=2026-11-23`

#### Scenario: Cambio a vista día
- **GIVEN** se muestra la semana del 23/11/2026
- **WHEN** el usuario elige la vista "Día"
- **THEN** la URL pasa a `/agenda?vista=dia&fecha=2026-11-23`

#### Scenario: Volver atrás
- **GIVEN** el usuario avanzó de la semana del 23/11 a la del 30/11
- **WHEN** usa el botón atrás del navegador
- **THEN** se vuelve a mostrar la semana del 23/11

### Requirement: Estado del turno visible
Cada turno SHALL mostrar hora de inicio y fin, apellido y nombre del paciente, prestación y su estado con una etiqueta de texto y un estilo distinto por estado, para los siete estados (incluido `PENDIENTE_CONFIRMACION`).

#### Scenario: Turno pendiente de confirmación
- **GIVEN** la API devuelve un turno `PENDIENTE_CONFIRMACION` de Lucía Gómez, prestación Limpieza, de 09:00 a 09:45
- **WHEN** se renderiza la agenda
- **THEN** el ítem muestra "09:00–09:45", "Gómez, Lucía", "Limpieza" y la etiqueta "Pendiente de confirmación" con el estilo de pendiente

#### Scenario: Estilos distintos por estado
- **GIVEN** la API devuelve un turno en cada uno de los estados `RESERVADO`, `CONFIRMADO`, `ATENDIDO` y `AUSENTE`
- **WHEN** se renderiza la agenda
- **THEN** cada ítem tiene un estilo propio de su estado y una etiqueta de texto distinta

### Requirement: Cancelados y expirados ocultos por defecto
La agenda SHALL ocultar los turnos `CANCELADO` y `EXPIRADO` salvo que el usuario active "Mostrar cancelados y expirados".

#### Scenario: Ocultos por defecto
- **GIVEN** la API devuelve un turno `RESERVADO` y uno `CANCELADO`
- **WHEN** se renderiza la agenda
- **THEN** solo se muestra el turno `RESERVADO`

#### Scenario: Mostrar cancelados
- **GIVEN** la agenda muestra solo el turno `RESERVADO` y la API también devolvió uno `CANCELADO`
- **WHEN** el usuario activa "Mostrar cancelados y expirados"
- **THEN** se muestran ambos turnos y el cancelado lleva la etiqueta "Cancelado"

### Requirement: Turnos de hoy
La aplicación SHALL ofrecer la ruta privada `/agenda/hoy`, enlazada desde la agenda, que lista los turnos de `GET /api/v1/turnos/hoy` con hora, paciente, prestación, estado y las acciones habilitadas de cada turno.

#### Scenario: Lista del día
- **GIVEN** la API devuelve dos turnos de hoy
- **WHEN** el usuario navega a `/agenda/hoy`
- **THEN** se listan ambos con hora, apellido y nombre, prestación y estado

#### Scenario: Acción desde hoy
- **GIVEN** `/agenda/hoy` muestra un turno `CONFIRMADO`
- **WHEN** el usuario lo marca como atendido y la API responde `200` con estado `ATENDIDO`
- **THEN** la lista se recarga y el turno muestra la etiqueta "Atendido"

### Requirement: Acciones habilitadas según el estado
El detalle de un turno SHALL ofrecer solo las acciones válidas para su estado: mover (`RESERVADO`/`CONFIRMADO` con inicio futuro), cancelar (`PENDIENTE_CONFIRMACION`/`RESERVADO`/`CONFIRMADO`), marcar atendido y marcar ausente (`RESERVADO`/`CONFIRMADO`), revertir ausencia (`AUSENTE`). Los estados finales MUST NOT ofrecer acciones.

#### Scenario: Turno reservado futuro
- **GIVEN** un turno `RESERVADO` con inicio dentro de 2 horas
- **WHEN** el usuario abre su detalle
- **THEN** se ofrecen "Mover", "Cancelar" y "Marcar atendido"

#### Scenario: Turno pendiente
- **GIVEN** un turno `PENDIENTE_CONFIRMACION`
- **WHEN** el usuario abre su detalle
- **THEN** solo se ofrece "Cancelar"

#### Scenario: Turno atendido
- **GIVEN** un turno `ATENDIDO`
- **WHEN** el usuario abre su detalle
- **THEN** no se ofrece ninguna acción

#### Scenario: No se mueve un turno ya iniciado
- **GIVEN** un turno `CONFIRMADO` cuyo inicio fue hace 10 minutos
- **WHEN** el usuario abre su detalle
- **THEN** no se ofrece "Mover"

### Requirement: Paciente del turno por DNI o alta rápida
Al crear un turno, la aplicación SHALL buscar el paciente con `GET /api/v1/pacientes?dni=` y, si no existe, ofrecer un alta rápida (nombre, apellido, DNI, teléfono, email) con `POST /api/v1/pacientes`, validando el formato en el cliente y mostrando los errores del servidor por campo.

#### Scenario: Paciente existente
- **GIVEN** la API devuelve un paciente con DNI `30111222`, Lucía Gómez
- **WHEN** el usuario busca el DNI `30.111.222`
- **THEN** se selecciona a "Gómez, Lucía (DNI 30111222)" como paciente del turno

#### Scenario: Paciente inexistente
- **GIVEN** la API devuelve una lista vacía para el DNI `40999888`
- **WHEN** el usuario busca ese DNI
- **THEN** se muestra el formulario de alta rápida con el DNI `40999888` precargado

#### Scenario: Alta rápida exitosa
- **GIVEN** el formulario de alta rápida está completo con datos válidos
- **WHEN** el usuario lo envía y la API responde `201` con el paciente
- **THEN** ese paciente queda seleccionado para el turno

#### Scenario: DNI inválido en el cliente
- **GIVEN** el usuario ingresa el DNI `12ab`
- **WHEN** intenta buscar
- **THEN** se muestra "Debe tener 7 u 8 dígitos." junto al campo y no se envía la solicitud

#### Scenario: DNI duplicado en el alta
- **GIVEN** el formulario de alta rápida tiene el DNI `30111222`
- **WHEN** la API responde `409`
- **THEN** la aplicación vuelve a buscar ese DNI y selecciona al paciente existente

#### Scenario: Error de campo del servidor
- **GIVEN** el formulario de alta rápida está cargado
- **WHEN** la API responde `400` con error en `email` "No es un email válido."
- **THEN** el mensaje aparece junto al campo de email y los valores cargados se conservan

### Requirement: Horarios libres para la prestación
Al crear un turno, la aplicación SHALL ofrecer solo las prestaciones activas y, elegidas prestación y fecha, mostrar como opciones los huecos de `GET /api/v1/agenda/disponibilidad?prestacionId&desde&hasta` (con `desde` = `hasta` = la fecha elegida, en el calendario del consultorio) en hora del consultorio.

#### Scenario: Solo prestaciones activas
- **GIVEN** existen las prestaciones Consulta (activa) y Blanqueamiento (inactiva)
- **WHEN** se abre el formulario de nuevo turno
- **THEN** solo se ofrece Consulta

#### Scenario: Huecos de la fecha
- **GIVEN** se eligieron Limpieza (id 3) y la fecha 24/11/2026
- **WHEN** la API de `GET /api/v1/agenda/disponibilidad?prestacionId=3&desde=2026-11-24&hasta=2026-11-24` responde huecos con inicio `12:00Z` y `13:00Z`
- **THEN** se ofrecen las opciones "09:00" y "10:00"

#### Scenario: Sin huecos
- **GIVEN** se eligieron prestación y fecha
- **WHEN** la API de disponibilidad responde una lista vacía
- **THEN** se muestra "No hay horarios libres para esa fecha"

### Requirement: Creación del turno
Confirmado el hueco, la aplicación SHALL enviar `POST /api/v1/turnos` con `{pacienteId, prestacionId, inicio}` (inicio en UTC) y, ante `201`, cerrar el formulario y mostrar la agenda del día del turno con el turno nuevo.

#### Scenario: Creación exitosa
- **GIVEN** se eligieron el paciente 7, la prestación 3 y el hueco de las 09:00 del 24/11/2026
- **WHEN** el usuario confirma
- **THEN** se envía `POST /api/v1/turnos` con `{"pacienteId":7,"prestacionId":3,"inicio":"2026-11-24T12:00:00Z"}` y, ante `201`, la agenda del 24/11 se recarga y muestra el turno `RESERVADO`

#### Scenario: Fuera de horario
- **GIVEN** el formulario de nuevo turno está completo
- **WHEN** la API responde `422` con detail "Fuera del horario de atención"
- **THEN** se muestra ese mensaje y se conservan paciente, prestación y fecha elegidos

### Requirement: Conflicto de horario con alternativas
Ante un `409` al crear o mover un turno, la aplicación SHALL informar que el horario ya no está disponible, ofrecer como opciones las `alternativas` del cuerpo del error, en hora del consultorio, y actualizar los huecos mostrados.

#### Scenario: Conflicto con alternativas
- **GIVEN** el usuario confirma el hueco de las 09:00
- **WHEN** la API responde `409` con `alternativas` de inicio `13:00Z` y `14:00Z`
- **THEN** se muestra "Ese horario ya no está disponible.", se ofrecen "10:00" y "11:00", y se vuelve a pedir la disponibilidad

#### Scenario: Elegir una alternativa
- **GIVEN** se ofrecen las alternativas "10:00" y "11:00" tras un `409`
- **WHEN** el usuario elige "10:00" y confirma
- **THEN** se envía `POST /api/v1/turnos` con inicio `2026-11-24T13:00:00Z`

#### Scenario: Conflicto sin alternativas
- **GIVEN** el usuario confirma un hueco
- **WHEN** la API responde `409` sin `alternativas`
- **THEN** se muestra el mensaje de conflicto y los huecos actualizados, sin opciones adicionales

### Requirement: Mover un turno
Desde el detalle, "Mover" SHALL pedir una fecha, mostrar los huecos de `GET /api/v1/agenda/disponibilidad` con la prestación del turno (la API no excluye el rango del propio turno), y enviar `PUT /api/v1/turnos/{id}/mover` con `{inicio}`. Ante `409` aplica el conflicto con alternativas.

#### Scenario: Huecos con la prestación del turno
- **GIVEN** el turno 41 de Limpieza (id 3) está abierto en "Mover"
- **WHEN** el usuario elige la fecha 24/11/2026
- **THEN** se solicita `GET /api/v1/agenda/disponibilidad?prestacionId=3&desde=2026-11-24&hasta=2026-11-24`, sin `excluirTurnoId`

#### Scenario: Movimiento exitoso
- **GIVEN** el usuario eligió el hueco de las 11:00 del 24/11/2026 para el turno 41
- **WHEN** confirma y la API responde `200`
- **THEN** se envió `PUT /api/v1/turnos/41/mover` con `{"inicio":"2026-11-24T14:00:00Z"}` y la agenda se recarga mostrando el turno a las 11:00

### Requirement: Cancelar un turno con motivo
"Cancelar" SHALL pedir un motivo opcional (hasta 200 caracteres) y una confirmación explícita, y enviar `POST /api/v1/turnos/{id}/cancelar` con `{motivo}`. Ante `200`, la agenda se recarga.

#### Scenario: Cancelación con motivo
- **GIVEN** el detalle del turno 41 `RESERVADO` está abierto
- **WHEN** el usuario elige "Cancelar", escribe "Paciente avisó por teléfono" y confirma
- **THEN** se envía `POST /api/v1/turnos/41/cancelar` con `{"motivo":"Paciente avisó por teléfono"}` y la agenda se recarga sin el turno visible

#### Scenario: Desistir de cancelar
- **GIVEN** el diálogo de cancelación está abierto
- **WHEN** el usuario elige "Volver"
- **THEN** no se envía ninguna solicitud y el turno sigue igual

#### Scenario: Motivo demasiado largo
- **GIVEN** el diálogo de cancelación está abierto
- **WHEN** el usuario escribe un motivo de 201 caracteres e intenta confirmar
- **THEN** se muestra "Máximo 200 caracteres." y no se envía la solicitud

### Requirement: Marcar un turno como atendido
"Marcar atendido" SHALL enviar `POST /api/v1/turnos/{id}/atendido` y, ante `200`, recargar la vista mostrando el turno con estado `ATENDIDO`.

#### Scenario: Atendido
- **GIVEN** el detalle del turno 41 `CONFIRMADO` está abierto
- **WHEN** el usuario elige "Marcar atendido" y la API responde `200` con estado `ATENDIDO`
- **THEN** se envió `POST /api/v1/turnos/41/atendido` y el turno muestra la etiqueta "Atendido" sin acciones

### Requirement: Marcar ausente tras la tolerancia
"Marcar ausente" SHALL estar habilitado solo cuando ahora es igual o posterior al inicio más `toleranciaAusenteMin` del consultorio, y enviar `POST /api/v1/turnos/{id}/ausente`. El `422` de la API MUST mostrarse, porque la API decide.

#### Scenario: Antes de la tolerancia
- **GIVEN** la tolerancia es 15 min, un turno `RESERVADO` empieza a las 09:00 y ahora son las 09:14
- **WHEN** el usuario abre su detalle
- **THEN** "Marcar ausente" está deshabilitado e indica "Disponible desde las 09:15"

#### Scenario: Justo en la tolerancia
- **GIVEN** la tolerancia es 15 min, un turno `RESERVADO` empieza a las 09:00 y ahora son las 09:15
- **WHEN** el usuario abre su detalle
- **THEN** "Marcar ausente" está habilitado

#### Scenario: Ausente exitoso
- **GIVEN** "Marcar ausente" está habilitado para el turno 41 de un paciente con 1 ausencia
- **WHEN** el usuario confirma y la API responde `200` con estado `AUSENTE`
- **THEN** se envió `POST /api/v1/turnos/41/ausente`, el turno muestra "Ausente" y el historial del paciente se recarga

#### Scenario: Rechazo del servidor
- **GIVEN** "Marcar ausente" está habilitado
- **WHEN** la API responde `422` con `type` terminado en `/ausente-antes-de-tolerancia` y detail "Todavía no pasó la tolerancia"
- **THEN** se muestra ese mensaje y la agenda se recarga

### Requirement: Revertir una ausencia dentro de 24 horas
Un turno `AUSENTE` SHALL ofrecer "Revertir ausencia" solo mientras ahora es anterior a `ausenteMarcadoEn` más 24 horas, y enviar `POST /api/v1/turnos/{id}/revertir-ausencia`. Ante `200`, el turno MUST mostrarse en el estado que devuelve la API (su estado previo real, `RESERVADO` o `CONFIRMADO`) y se recargan la vista y el historial de ausencias.

#### Scenario: Dentro de la ventana
- **GIVEN** un turno `AUSENTE` con `ausenteMarcadoEn` hace 23 h 59 min
- **WHEN** el usuario abre su detalle
- **THEN** se ofrece "Revertir ausencia"

#### Scenario: Fuera de la ventana
- **GIVEN** un turno `AUSENTE` con `ausenteMarcadoEn` hace exactamente 24 h
- **WHEN** el usuario abre su detalle
- **THEN** no se ofrece ninguna acción

#### Scenario: Reversión exitosa
- **GIVEN** el detalle del turno 41 `AUSENTE` ofrece "Revertir ausencia" y el paciente tiene 2 ausencias
- **WHEN** el usuario confirma y la API responde `200` con estado `RESERVADO`
- **THEN** se envió `POST /api/v1/turnos/41/revertir-ausencia`, el turno muestra "Reservado" y el historial se recarga

#### Scenario: Reversión de un turno que estaba confirmado
- **GIVEN** el turno 41 estaba `CONFIRMADO` antes de marcarse ausente y su detalle ofrece "Revertir ausencia"
- **WHEN** el usuario confirma y la API responde `200` con estado `CONFIRMADO`
- **THEN** el turno muestra "Confirmado"

#### Scenario: Reversión fuera de plazo según el servidor
- **GIVEN** el detalle ofrece "Revertir ausencia"
- **WHEN** la API responde `422` con `type` terminado en `/reversion-fuera-de-plazo` y sin detail
- **THEN** se muestra "Ya pasaron 24 horas desde la marca: la ausencia no se puede revertir." y la agenda se recarga

### Requirement: Historial de ausencias del paciente
El detalle de un turno SHALL mostrar la cantidad y las fechas de las ausencias del paciente desde `GET /api/v1/pacientes/{id}/ausencias`, en hora del consultorio, y la agenda MUST indicar la cantidad en el ítem del turno cuando es mayor que cero.

#### Scenario: Paciente con ausencias
- **GIVEN** la API responde `{"cantidad":2,"ausencias":[{"turnoId":30,"inicio":"2026-11-10T12:00:00Z","prestacionNombre":"Consulta"},{"turnoId":12,"inicio":"2026-10-02T17:30:00Z","prestacionNombre":"Control"}]}`
- **WHEN** el usuario abre el detalle de un turno de ese paciente
- **THEN** se muestra "Ausencias: 2" con "10/11/2026 09:00 — Consulta" y "02/10/2026 14:30 — Control"

#### Scenario: Sin ausencias
- **GIVEN** la API responde `{"cantidad":0,"ausencias":[]}`
- **WHEN** el usuario abre el detalle
- **THEN** se muestra "Sin ausencias registradas"

#### Scenario: Indicador en la agenda
- **GIVEN** un turno cuyo paciente tiene `cantidadAusencias` 2
- **WHEN** se renderiza la agenda
- **THEN** el ítem del turno muestra "2 aus."

### Requirement: Errores al operar un turno
Ante un error de una acción sobre un turno, la aplicación SHALL mostrar el `detail` de un `422` (si falta, un mensaje propio según el último segmento de su `type`: `ausente-antes-de-tolerancia`, `reversion-fuera-de-plazo` o `transicion-invalida`; si el `type` no es conocido, el `title`) y recargar la agenda, informar "El turno ya no existe." y recargar ante `404`, y ante una falla de red mostrar un mensaje de conexión sin perder lo cargado.

#### Scenario: Transición inválida
- **GIVEN** el detalle muestra un turno `RESERVADO` que otro proceso ya canceló
- **WHEN** el usuario elige "Marcar atendido" y la API responde `422` con detail "Transición inválida"
- **THEN** se muestra "Transición inválida" y la agenda se recarga mostrando el estado actual

#### Scenario: Transición inválida sin detail
- **WHEN** una acción responde `422` con `type` terminado en `/transicion-invalida` y sin detail
- **THEN** se muestra "El turno cambió de estado y esa acción ya no corresponde." y la agenda se recarga

#### Scenario: Turno inexistente
- **WHEN** una acción responde `404`
- **THEN** se muestra "El turno ya no existe.", se cierra el detalle y la agenda se recarga

#### Scenario: Falla de red al cancelar
- **GIVEN** el diálogo de cancelación tiene el motivo "Viaje"
- **WHEN** la solicitud falla por red
- **THEN** se muestra "No se pudo conectar. Reintentá." y el motivo sigue cargado

#### Scenario: Falla al cargar la agenda
- **WHEN** `GET /api/v1/agenda` falla por red
- **THEN** se muestra el mensaje de conexión y un botón "Reintentar" que repite la solicitud

### Requirement: Acceso a la historia clínica desde el turno
El detalle de un turno SHALL ofrecer el enlace "Historia clínica" que navega a `/pacientes/{pacienteId}/historia` del paciente del turno, sin incluir datos clínicos en la URL.

#### Scenario: Enlace en el detalle del turno
- **GIVEN** un turno del paciente 7
- **WHEN** el usuario abre el detalle del turno
- **THEN** se muestra el enlace "Historia clínica" con destino `/pacientes/7/historia`

#### Scenario: Navegación dentro de la SPA
- **WHEN** el usuario hace clic en "Historia clínica"
- **THEN** la aplicación navega a `/pacientes/7/historia` sin recargar la página
