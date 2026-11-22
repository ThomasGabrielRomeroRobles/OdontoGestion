# agenda-configuration Specification

## Purpose

Define las prestaciones (tipos de turno con duración), los horarios semanales de trabajo y los bloqueos de agenda del consultorio, que son la base sobre la que se calcula la disponibilidad y se crean turnos.

## Requirements

### Requirement: Acceso autenticado a la configuración de agenda
Los recursos `/api/v1/prestaciones`, `/api/v1/horarios` y `/api/v1/bloqueos` SHALL exigir sesión del odontólogo, y sus escrituras MUST exigir token CSRF.

#### Scenario: Sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** se envía `GET /api/v1/prestaciones`
- **THEN** la API responde `401`

#### Scenario: Escritura sin CSRF
- **GIVEN** hay sesión activa y la solicitud no lleva `X-XSRF-TOKEN`
- **WHEN** se envía `POST /api/v1/horarios`
- **THEN** la API responde `403` y no se crea el horario

### Requirement: Alta de prestación
El odontólogo SHALL poder crear una prestación con nombre y duración en minutos; la prestación queda activa y asociada al consultorio, y la API responde `201` con su representación.

#### Scenario: Alta válida
- **GIVEN** la granularidad del consultorio es 15 min
- **WHEN** se envía `POST /api/v1/prestaciones` con `{"nombre":"Blanqueamiento","duracionMin":60}`
- **THEN** la API responde `201` con `id`, `nombre` "Blanqueamiento", `duracionMin` 60 y `activa` `true`

### Requirement: Duración múltiplo de la granularidad
La duración de una prestación SHALL ser un entero positivo múltiplo de la granularidad vigente del consultorio al crearla o editarla; si no lo es, la API MUST responder `422` con error en el campo `duracionMin`.

#### Scenario: Duración no múltiplo
- **GIVEN** la granularidad del consultorio es 15 min
- **WHEN** se crea una prestación con `duracionMin` 40
- **THEN** la API responde `422` con un error de campo `duracionMin` y no se crea la prestación

#### Scenario: Duración cero o negativa
- **WHEN** se crea una prestación con `duracionMin` 0
- **THEN** la API responde `422` con un error de campo `duracionMin`

#### Scenario: Granularidad distinta
- **GIVEN** la granularidad del consultorio es 30 min
- **WHEN** se crea una prestación con `duracionMin` 45
- **THEN** la API responde `422`

#### Scenario: Cambio de granularidad no altera prestaciones existentes
- **GIVEN** existe la prestación "Limpieza" de 45 min y la granularidad cambia de 15 a 30 min
- **WHEN** se lista `GET /api/v1/prestaciones`
- **THEN** "Limpieza" sigue con 45 min

### Requirement: Nombre de prestación único
El nombre de una prestación SHALL ser obligatorio y único dentro del consultorio sin distinguir mayúsculas; un duplicado MUST responder `422` con error en el campo `nombre`.

#### Scenario: Nombre duplicado
- **GIVEN** existe la prestación "Consulta"
- **WHEN** se crea una prestación con nombre "consulta"
- **THEN** la API responde `422` con un error de campo `nombre`

#### Scenario: Nombre vacío
- **WHEN** se crea una prestación con nombre vacío
- **THEN** la API responde `422` con un error de campo `nombre`

### Requirement: Listado y edición de prestaciones
`GET /api/v1/prestaciones` SHALL devolver todas las prestaciones del consultorio ordenadas por nombre, activas e inactivas; `PUT /api/v1/prestaciones/{id}` SHALL modificar nombre y duración con las mismas validaciones del alta.

#### Scenario: Listado con inactivas
- **GIVEN** existen "Consulta" activa y "Control" inactiva
- **WHEN** se envía `GET /api/v1/prestaciones`
- **THEN** la respuesta incluye ambas con su campo `activa`

#### Scenario: Edición válida
- **GIVEN** existe la prestación "Consulta" de 30 min
- **WHEN** se envía `PUT /api/v1/prestaciones/{id}` con `{"nombre":"Consulta","duracionMin":45}`
- **THEN** la API responde `200` con `duracionMin` 45

#### Scenario: Prestación inexistente
- **WHEN** se envía `PUT /api/v1/prestaciones/999999`
- **THEN** la API responde `404`

### Requirement: Desactivación de prestación sin pérdida de historial
Una prestación SHALL poder desactivarse y reactivarse; nunca MUST borrarse físicamente. Una prestación inactiva no se ofrece para nuevos turnos pero sigue disponible para consultar turnos e historial que la referencian.

#### Scenario: Desactivar
- **GIVEN** existe la prestación "Endodoncia" activa
- **WHEN** se envía `POST /api/v1/prestaciones/{id}/desactivar`
- **THEN** la API responde `200` con `activa` `false` y la prestación sigue apareciendo en el listado

#### Scenario: Reactivar
- **GIVEN** existe la prestación "Endodoncia" inactiva
- **WHEN** se envía `POST /api/v1/prestaciones/{id}/activar`
- **THEN** la API responde `200` con `activa` `true`

#### Scenario: Sin borrado
- **WHEN** se envía `DELETE /api/v1/prestaciones/{id}`
- **THEN** la API responde `405` y la prestación sigue existiendo

### Requirement: Horario de trabajo válido
Un horario de trabajo SHALL tener `diaSemana` entre 1 (lunes) y 7 (domingo) y `horaDesde` menor que `horaHasta`, ambas en hora local del consultorio y alineadas a la granularidad; si no, la API MUST responder `422` con error de campo.

#### Scenario: Alta válida
- **WHEN** se envía `POST /api/v1/horarios` con `{"diaSemana":6,"horaDesde":"09:00","horaHasta":"12:00"}`
- **THEN** la API responde `201` con el horario creado

#### Scenario: Rango invertido
- **WHEN** se crea un horario con `horaDesde` "14:00" y `horaHasta` "13:00"
- **THEN** la API responde `422` con un error de campo `horaHasta`

#### Scenario: Día fuera de rango
- **WHEN** se crea un horario con `diaSemana` 8
- **THEN** la API responde `422` con un error de campo `diaSemana`

#### Scenario: Hora no alineada a la granularidad
- **GIVEN** la granularidad del consultorio es 15 min
- **WHEN** se crea un horario con `horaDesde` "09:10"
- **THEN** la API responde `422` con un error de campo `horaDesde`

### Requirement: Horarios sin solapamiento en el mismo día
Dos horarios de trabajo del mismo consultorio y el mismo día MUST NOT solaparse, con intervalos semiabiertos `[desde, hasta)`; la restricción SHALL garantizarse en la base y un solapamiento MUST responder `422`.

#### Scenario: Solapamiento rechazado
- **GIVEN** existe el horario lunes 09:00-13:00
- **WHEN** se crea el horario lunes 12:00-15:00
- **THEN** la API responde `422` y no se crea el horario

#### Scenario: Horarios contiguos permitidos
- **GIVEN** existe el horario lunes 09:00-13:00
- **WHEN** se crea el horario lunes 13:00-14:00
- **THEN** la API responde `201`

#### Scenario: Mismo rango en otro día
- **GIVEN** existe el horario lunes 09:00-13:00
- **WHEN** se crea el horario martes 09:00-13:00
- **THEN** la API responde `201`

#### Scenario: Edición que genera solapamiento
- **GIVEN** existen los horarios lunes 09:00-13:00 y lunes 14:00-18:00
- **WHEN** se edita el segundo a lunes 12:00-18:00 con `PUT /api/v1/horarios/{id}`
- **THEN** la API responde `422` y el horario conserva 14:00-18:00

### Requirement: Listado, edición y baja de horarios
`GET /api/v1/horarios` SHALL devolver los horarios del consultorio ordenados por día y hora de inicio; `PUT /api/v1/horarios/{id}` SHALL editarlos con las mismas validaciones y `DELETE /api/v1/horarios/{id}` SHALL eliminarlos respondiendo `204`.

#### Scenario: Listado ordenado
- **GIVEN** existen martes 09:00-13:00, lunes 14:00-18:00 y lunes 09:00-13:00
- **WHEN** se envía `GET /api/v1/horarios`
- **THEN** el orden es lunes 09:00, lunes 14:00, martes 09:00

#### Scenario: Baja
- **GIVEN** existe un horario
- **WHEN** se envía `DELETE /api/v1/horarios/{id}`
- **THEN** la API responde `204` y el horario ya no aparece en el listado

#### Scenario: Baja de inexistente
- **WHEN** se envía `DELETE /api/v1/horarios/999999`
- **THEN** la API responde `404`

### Requirement: Bloqueo de agenda válido
Un bloqueo SHALL tener instantes `desde` y `hasta` con zona horaria explícita (se almacenan en UTC), `desde` menor que `hasta`, y un `motivo` opcional de hasta 200 caracteres que sin valor se guarda y devuelve como `""`; si no, la API MUST responder `422` con error de campo, incluso ante fechas mal formadas.

#### Scenario: Alta válida
- **WHEN** se envía `POST /api/v1/bloqueos` con `{"desde":"2026-12-24T03:00:00Z","hasta":"2026-12-26T03:00:00Z","motivo":"Fiestas"}`
- **THEN** la API responde `201` con el bloqueo y sus instantes en UTC

#### Scenario: Rango invertido
- **WHEN** se crea un bloqueo con `hasta` anterior a `desde`
- **THEN** la API responde `422` con un error de campo `hasta`

#### Scenario: Instante sin zona horaria
- **WHEN** se crea un bloqueo con `desde` "2026-12-24T00:00:00" (sin zona)
- **THEN** la API responde `422` con un error de campo `desde`

#### Scenario: Fecha mal formada
- **WHEN** se crea un bloqueo con `desde` "no-es-fecha"
- **THEN** la API responde `422` (no `400`) con un error de campo `desde`

#### Scenario: Sin motivo
- **WHEN** se crea un bloqueo sin `motivo` o con `motivo` `null`
- **THEN** la API responde `201` con `motivo` `""`

#### Scenario: Campo obligatorio ausente
- **WHEN** se crea un bloqueo sin `hasta`
- **THEN** la API responde `400` con un error de campo `hasta`

### Requirement: Listado, edición y baja de bloqueos
`GET /api/v1/bloqueos` SHALL devolver, ordenados por `desde`, los bloqueos que se intersecan con `[desde, hasta)`; ambos filtros son opcionales e independientes: sin `desde` el inicio es ahora y sin `hasta` no hay límite superior. `PUT /api/v1/bloqueos/{id}` SHALL editarlos y `DELETE /api/v1/bloqueos/{id}` SHALL eliminarlos con `204`.

#### Scenario: Filtro por rango
- **GIVEN** existen bloqueos en diciembre y en enero
- **WHEN** se envía `GET /api/v1/bloqueos?desde=2026-12-01T03:00:00Z&hasta=2027-01-01T03:00:00Z`
- **THEN** la respuesta incluye solo los bloqueos que se intersecan con diciembre

#### Scenario: Sin rango excluye bloqueos pasados
- **GIVEN** existe un bloqueo que terminó ayer y otro que termina mañana
- **WHEN** se envía `GET /api/v1/bloqueos`
- **THEN** la respuesta incluye solo el que termina mañana

#### Scenario: Solo desde
- **GIVEN** existe un bloqueo que terminó ayer y otro que termina mañana
- **WHEN** se envía `GET /api/v1/bloqueos` solo con `desde` igual a hace una semana
- **THEN** la respuesta incluye ambos

#### Scenario: Solo hasta
- **GIVEN** existe un bloqueo que terminó ayer, otro de la semana próxima y otro del año próximo
- **WHEN** se envía `GET /api/v1/bloqueos` solo con `hasta` igual a dentro de un mes
- **THEN** la respuesta incluye solo el de la semana próxima

#### Scenario: Filtro mal formado
- **WHEN** se envía `GET /api/v1/bloqueos?desde=ayer`
- **THEN** la API responde `422` con un error de campo `desde`

#### Scenario: Baja
- **GIVEN** existe un bloqueo
- **WHEN** se envía `DELETE /api/v1/bloqueos/{id}`
- **THEN** la API responde `204`

### Requirement: Datos iniciales de configuración de agenda
Al aplicar las migraciones sobre una base vacía, el consultorio SHALL quedar con las prestaciones ficticias Consulta (30), Limpieza (45), Obturación (60), Endodoncia (90), Extracción (45) y Control (15), activas, y horarios L-V 09:00-13:00 y 14:00-18:00. Los arranques posteriores MUST NOT volver a sembrar.

#### Scenario: Seed aplicado
- **GIVEN** una base recién migrada (el consultorio lo crea la migración `V2`)
- **WHEN** se lista `GET /api/v1/prestaciones` y `GET /api/v1/horarios`
- **THEN** hay 6 prestaciones activas con esas duraciones y 10 horarios (2 por día de lunes a viernes)

#### Scenario: Segundo arranque
- **GIVEN** el seed ya se aplicó y el odontólogo desactivó "Control"
- **WHEN** la aplicación vuelve a arrancar
- **THEN** sigue habiendo 6 prestaciones, "Control" sigue inactiva y no se duplican horarios
