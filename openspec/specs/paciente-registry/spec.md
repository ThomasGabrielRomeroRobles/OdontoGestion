# paciente-registry Specification

## Purpose

Registra a los pacientes del consultorio sin cuenta ni contraseña, identificados por un DNI único por consultorio. Permite al odontólogo darlos de alta, editarlos, consultarlos y buscarlos, y ofrece a los casos de uso de turnos y de la página pública una forma de reutilizar o crear un paciente por DNI sin pisar sus datos de contacto.

## Requirements

### Requirement: Paciente sin cuenta vinculado al consultorio
Todo paciente SHALL pertenecer al consultorio actual y MUST NOT tener credenciales ni posibilidad de iniciar sesión. El cliente nunca indica el consultorio: el sistema lo resuelve.

#### Scenario: Alta vinculada al consultorio actual
- **GIVEN** el odontólogo inició sesión
- **WHEN** da de alta un paciente válido sin indicar consultorio
- **THEN** el paciente queda vinculado al consultorio actual

#### Scenario: Paciente sin consultorio rechazado por la base
- **WHEN** se inserta directamente en la base un paciente con `consultorio_id` nulo
- **THEN** la inserción es rechazada

#### Scenario: Sin datos de acceso en la respuesta
- **WHEN** el odontólogo consulta un paciente
- **THEN** la respuesta no contiene contraseña, hash ni token alguno

### Requirement: DNI único por consultorio
El DNI SHALL ser único dentro del consultorio (RN-PA-01). Se compara normalizado, sin puntos ni espacios, y MUST guardarse en esa forma.

#### Scenario: DNI con puntos normalizado
- **GIVEN** el odontólogo inició sesión
- **WHEN** da de alta un paciente con DNI `30.123.456`
- **THEN** el paciente queda guardado con DNI `30123456`

#### Scenario: Alta con DNI existente
- **GIVEN** existe un paciente con DNI `30123456`
- **WHEN** el odontólogo da de alta otro paciente con DNI `30.123.456`
- **THEN** la respuesta es `409` en formato `ProblemDetail` y no se crea ningún paciente

#### Scenario: Unicidad garantizada por la base
- **GIVEN** existe un paciente con DNI `30123456` en el consultorio
- **WHEN** se inserta directamente en la base otro paciente del mismo consultorio con DNI `30123456`
- **THEN** la inserción es rechazada por una restricción de la base

### Requirement: Datos obligatorios y formato del paciente
El alta y la edición SHALL exigir nombre, apellido, DNI de 7 u 8 dígitos (con o sin puntos de miles), teléfono y email válido (RN-PA-03). Fecha de nacimiento (no futura) y dirección son opcionales. Un dato inválido MUST responder `400` indicando el campo.

#### Scenario: Falta el email
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía un alta sin `email`
- **THEN** la respuesta es `400` con un error para el campo `email` y no se crea ningún paciente

#### Scenario: Falta el teléfono
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía un alta con `telefono` vacío
- **THEN** la respuesta es `400` con un error para el campo `telefono`

#### Scenario: DNI con formato inválido
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía un alta con DNI `12AB` o `123456`
- **THEN** la respuesta es `400` con un error para el campo `dni`

#### Scenario: Fecha de nacimiento futura
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía un alta con una `fechaNacimiento` posterior a la fecha actual
- **THEN** la respuesta es `400` con un error para el campo `fechaNacimiento`

#### Scenario: Opcionales ausentes
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía un alta válida sin `fechaNacimiento` ni `direccion`
- **THEN** la respuesta es `201` con `fechaNacimiento` nulo y `direccion` vacía

### Requirement: Alta de paciente por el odontólogo
`POST /api/v1/pacientes` SHALL crear un paciente activo con cero ausencias y responder `201` con la ficha y la cabecera `Location` del recurso nuevo. El email MUST guardarse sin espacios y en minúsculas.

#### Scenario: Alta válida
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `POST /api/v1/pacientes` con nombre `Ana`, apellido `Pérez`, DNI `30123456`, teléfono `+54 11 5555-0001` y email ` Ana.Perez@Ejemplo.TEST `
- **THEN** la respuesta es `201` con `Location` `/api/v1/pacientes/{id}`, `email` `ana.perez@ejemplo.test`, `cantidadAusencias` `0` y `activo` `true`

#### Scenario: Alta seguida de consulta
- **GIVEN** el odontólogo dio de alta un paciente y recibió su `id`
- **WHEN** envía `GET /api/v1/pacientes/{id}`
- **THEN** la respuesta es `200` con los mismos datos devueltos en el alta

### Requirement: Consulta de la ficha del paciente
`GET /api/v1/pacientes/{id}` SHALL devolver `{id, nombre, apellido, dni, fechaNacimiento, telefono, email, direccion, cantidadAusencias, activo}`. Un id inexistente o de otro consultorio MUST responder `404`.

#### Scenario: Paciente existente
- **GIVEN** existe el paciente con DNI `30123456`
- **WHEN** el odontólogo consulta su ficha por id
- **THEN** la respuesta es `200` con exactamente esos diez campos

#### Scenario: Paciente inexistente
- **WHEN** el odontólogo consulta un id que no existe
- **THEN** la respuesta es `404` en formato `ProblemDetail`

#### Scenario: Paciente de otro consultorio
- **GIVEN** existe en la base un paciente de otro consultorio
- **WHEN** el odontólogo consulta su ficha por id
- **THEN** la respuesta es `404`

### Requirement: Edición de los datos administrativos
`PUT /api/v1/pacientes/{id}` SHALL reemplazar nombre, apellido, DNI, fecha de nacimiento, teléfono, email y dirección con las mismas validaciones del alta. MUST NOT modificar `cantidadAusencias` ni `activo`.

#### Scenario: Edición válida
- **GIVEN** existe un paciente con teléfono `+54 11 5555-0001`
- **WHEN** el odontólogo envía `PUT` con teléfono `+54 11 5555-0002` y el resto de los datos iguales
- **THEN** la respuesta es `200` y una consulta posterior devuelve el teléfono nuevo

#### Scenario: Cambio a un DNI de otro paciente
- **GIVEN** existen los pacientes A (DNI `30123456`) y B (DNI `30123457`)
- **WHEN** el odontólogo edita B con DNI `30123456`
- **THEN** la respuesta es `409` y B conserva el DNI `30123457`

#### Scenario: Conservar el propio DNI
- **GIVEN** existe un paciente con DNI `30123456`
- **WHEN** el odontólogo lo edita enviando el mismo DNI `30.123.456`
- **THEN** la respuesta es `200`

#### Scenario: Campos de solo lectura ignorados
- **GIVEN** existe un paciente con `cantidadAusencias` `2`
- **WHEN** el odontólogo envía `PUT` con `cantidadAusencias` `0` y `activo` `false`
- **THEN** la respuesta es `200` con `cantidadAusencias` `2` y `activo` `true`

#### Scenario: Edición de paciente inexistente
- **WHEN** el odontólogo envía `PUT` a un id que no existe
- **THEN** la respuesta es `404`

### Requirement: Búsqueda de pacientes
`GET /api/v1/pacientes` SHALL devolver una lista de fichas del consultorio actual ordenada por apellido y nombre, con un máximo de 20 resultados. Con `dni` busca coincidencia exacta del DNI normalizado; con `q` busca texto sin distinguir mayúsculas en apellido, nombre y email, o prefijo de DNI; sin parámetros devuelve los primeros 20.

#### Scenario: Búsqueda exacta por DNI
- **GIVEN** existen pacientes con DNI `30123456` y `30123457`
- **WHEN** el odontólogo envía `GET /api/v1/pacientes?dni=30.123.456`
- **THEN** la respuesta es `200` con una lista de un solo paciente, el de DNI `30123456`

#### Scenario: DNI sin coincidencias
- **WHEN** el odontólogo busca por un `dni` que no está registrado
- **THEN** la respuesta es `200` con una lista vacía

#### Scenario: Búsqueda por apellido sin distinguir mayúsculas
- **GIVEN** existen los pacientes "Ana Pérez" y "Luis Gómez"
- **WHEN** el odontólogo envía `GET /api/v1/pacientes?q=pér`
- **THEN** la lista contiene a "Ana Pérez" y no a "Luis Gómez"

#### Scenario: Búsqueda por prefijo de DNI
- **GIVEN** existe un paciente con DNI `30123456`
- **WHEN** el odontólogo envía `GET /api/v1/pacientes?q=3012`
- **THEN** la lista contiene a ese paciente

#### Scenario: Orden y tope de resultados
- **GIVEN** existen 25 pacientes que coinciden con la búsqueda
- **WHEN** el odontólogo busca
- **THEN** la lista tiene 20 pacientes ordenados por apellido y luego por nombre

#### Scenario: Solo pacientes del consultorio actual
- **GIVEN** existe en la base un paciente de otro consultorio con apellido "Pérez"
- **WHEN** el odontólogo busca `q=pérez`
- **THEN** ese paciente no figura en la lista

### Requirement: Reutilizar o crear paciente por DNI
El sistema SHALL ofrecer a los casos de uso de turnos y de la página pública una operación que, dado un DNI y datos de contacto, devuelve el paciente existente con ese DNI sin modificar sus datos (RN-PU-04) o crea uno nuevo si no existe, e informa cuál de los dos ocurrió.

#### Scenario: DNI nuevo crea al paciente
- **GIVEN** no existe ningún paciente con DNI `30999888`
- **WHEN** se invoca la operación con DNI `30999888` y datos de contacto válidos
- **THEN** existe un paciente nuevo con esos datos y la operación informa que fue creado

#### Scenario: DNI existente no sobrescribe el contacto
- **GIVEN** existe un paciente con DNI `30123456` y email `ana@ejemplo.test`
- **WHEN** se invoca la operación con DNI `30.123.456` y email `otra@ejemplo.test`
- **THEN** devuelve ese mismo paciente, que conserva el email `ana@ejemplo.test`, e informa que no fue creado

#### Scenario: Pedidos concurrentes con el mismo DNI
- **GIVEN** no existe ningún paciente con DNI `30777666`
- **WHEN** se invoca la operación dos veces en paralelo con ese DNI
- **THEN** ambas invocaciones terminan sin error, devuelven el mismo paciente y existe exactamente un paciente con ese DNI

#### Scenario: Usable dentro de una transacción mayor
- **GIVEN** un caso de uso con transacción propia invoca la operación con un DNI que otra transacción acaba de registrar
- **WHEN** la operación encuentra el DNI ya registrado
- **THEN** devuelve el paciente existente y la transacción del caso de uso puede seguir y confirmarse

### Requirement: Acceso exclusivo del odontólogo
Todas las rutas `/api/v1/pacientes/**` SHALL exigir sesión del odontólogo; sin sesión MUST responder `401` y las escrituras sin token CSRF válido MUST responder `403`, sin ejecutar el caso de uso.

#### Scenario: Búsqueda sin sesión
- **WHEN** se envía `GET /api/v1/pacientes` sin sesión
- **THEN** la respuesta es `401` en formato `ProblemDetail`

#### Scenario: Alta sin sesión
- **WHEN** se envía `POST /api/v1/pacientes` con token CSRF válido y sin sesión
- **THEN** la respuesta es `401` y no se crea ningún paciente

#### Scenario: Alta sin token CSRF
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `POST /api/v1/pacientes` sin la cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` y no se crea ningún paciente

### Requirement: Datos personales fuera de los logs
El sistema MUST NOT escribir en los logs el DNI, el email ni el teléfono de un paciente, tampoco en los errores de duplicado o de validación.

#### Scenario: Duplicado sin datos personales en el log
- **GIVEN** existe un paciente con DNI `30123456` y email `ana@ejemplo.test`
- **WHEN** el odontólogo intenta dar de alta otro paciente con ese DNI
- **THEN** la salida de logs de la operación no contiene `30123456` ni `ana@ejemplo.test`

#### Scenario: Detalle del conflicto sin datos personales
- **WHEN** un alta responde `409` por DNI duplicado
- **THEN** el `detail` del `ProblemDetail` no contiene el DNI
