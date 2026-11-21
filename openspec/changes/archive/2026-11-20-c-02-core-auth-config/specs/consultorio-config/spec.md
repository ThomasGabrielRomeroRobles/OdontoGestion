# Spec Delta

## Purpose

Modela el consultorio único del MVP del que cuelgan todos los datos de negocio, y permite al odontólogo consultar y editar sus datos de contacto y los parámetros operativos (granularidad, anticipación, cancelación, recordatorio, vencimientos, tolerancia) con valores por defecto de la KB.

## ADDED Requirements

### Requirement: Consultorio único del MVP
Tras aplicar las migraciones SHALL existir un consultorio ficticio "Consultorio Demo" con zona horaria `America/Argentina/Buenos_Aires`, y toda operación de negocio MUST resolver el consultorio actual sin que el cliente lo indique.

#### Scenario: Consultorio creado por migración
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca
- **THEN** existe exactamente un consultorio, con nombre "Consultorio Demo" y zona horaria `America/Argentina/Buenos_Aires`

#### Scenario: Consultorio resuelto sin parámetro
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/consultorio` sin indicar identificador alguno
- **THEN** la respuesta es `200` con los datos de "Consultorio Demo"

#### Scenario: Usuario vinculado al consultorio
- **WHEN** se inspecciona el odontólogo creado por bootstrap
- **THEN** referencia al consultorio actual

### Requirement: Parámetros operativos con valores por defecto
El consultorio SHALL tener los parámetros enteros `granularidadMin` (15), `anticipacionMinReservaMin` (120), `limiteCancelacionHoras` (2), `recordatorioHorasAntes` (24), `vencimientoAvisoMin` (60), `vencimientoPendienteMin` (30) y `toleranciaAusenteMin` (15), con esos valores por defecto.

#### Scenario: Defaults tras la migración
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca y el odontólogo envía `GET /api/v1/consultorio`
- **THEN** `parametros` contiene `granularidadMin` 15, `anticipacionMinReservaMin` 120, `limiteCancelacionHoras` 2, `recordatorioHorasAntes` 24, `vencimientoAvisoMin` 60, `vencimientoPendienteMin` 30 y `toleranciaAusenteMin` 15

#### Scenario: Parámetros positivos garantizados por la base
- **WHEN** se intenta actualizar directamente en la base `granularidad_min` a `0`
- **THEN** la actualización es rechazada por una restricción de la base

### Requirement: Consulta de datos y parámetros del consultorio
`GET /api/v1/consultorio` SHALL devolver `{nombre, direccion, telefono, emailContacto, zonaHoraria, parametros}` del consultorio actual, sin identificadores internos de otros recursos.

#### Scenario: Forma de la respuesta
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/consultorio`
- **THEN** la respuesta es `200` e incluye `nombre`, `direccion`, `telefono`, `emailContacto`, `zonaHoraria` y el objeto `parametros` con sus siete valores

#### Scenario: Sin sesión
- **WHEN** se envía `GET /api/v1/consultorio` sin sesión
- **THEN** la respuesta es `401`

### Requirement: Edición de datos y parámetros del consultorio
`PUT /api/v1/consultorio` SHALL reemplazar `nombre`, `direccion`, `telefono`, `emailContacto` y los siete `parametros`, y devolver el consultorio resultante. La zona horaria MUST NOT poder modificarse por esta vía.

#### Scenario: Edición exitosa
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `PUT /api/v1/consultorio` con `nombre` "Consultorio Sonrisa", `emailContacto` "turnos@sonrisa.example" y `parametros.granularidadMin` 30
- **THEN** la respuesta es `200` con esos valores y un `GET /api/v1/consultorio` posterior los devuelve

#### Scenario: Zona horaria ignorada
- **WHEN** el odontólogo envía `PUT /api/v1/consultorio` con `zonaHoraria` "UTC"
- **THEN** la zona horaria sigue siendo `America/Argentina/Buenos_Aires`

#### Scenario: Datos estructurales inválidos
- **WHEN** el odontólogo envía `PUT /api/v1/consultorio` con `nombre` vacío y `emailContacto` "no-es-email"
- **THEN** la respuesta es `400` con errores para los campos `nombre` y `emailContacto` y el consultorio no cambia

#### Scenario: Parámetro ausente
- **WHEN** el odontólogo envía `PUT /api/v1/consultorio` sin `parametros.recordatorioHorasAntes`
- **THEN** la respuesta es `400` con un error para ese campo

### Requirement: Parámetros enteros positivos
Todo parámetro enviado en `PUT /api/v1/consultorio` SHALL ser un entero mayor que cero; un valor cero o negativo MUST responder `422` indicando el parámetro incumplido, sin aplicar ningún cambio.

#### Scenario: Valor cero
- **WHEN** el odontólogo envía `parametros.granularidadMin` igual a `0`
- **THEN** la respuesta es `422` con `errors` que incluye el campo `parametros.granularidadMin` y ningún dato del consultorio cambia

#### Scenario: Valor negativo
- **WHEN** el odontólogo envía `parametros.limiteCancelacionHoras` igual a `-1`
- **THEN** la respuesta es `422` con `errors` que incluye el campo `parametros.limiteCancelacionHoras`

#### Scenario: Valor mínimo aceptado
- **WHEN** el odontólogo envía todos los parámetros con valor `1`
- **THEN** la respuesta es `200`

### Requirement: Cambios aplicados solo a operaciones nuevas
Los casos de uso SHALL leer los parámetros vigentes en el momento de cada operación nueva, sin reinicio; modificar parámetros MUST NOT recalcular ni alterar datos ya persistidos de otras entidades.

#### Scenario: Valor nuevo visible de inmediato
- **GIVEN** `granularidadMin` vale 15
- **WHEN** el odontólogo lo cambia a 30 y un caso de uso consulta los parámetros vigentes
- **THEN** obtiene 30 sin reiniciar la aplicación

#### Scenario: Sin efectos sobre otros datos
- **GIVEN** existe el odontólogo con su perfil
- **WHEN** se modifican los parámetros del consultorio
- **THEN** los datos del odontólogo permanecen sin cambios
