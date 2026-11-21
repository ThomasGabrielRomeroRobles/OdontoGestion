# Spec Delta

## Purpose

Permite que el odontólogo, único usuario con login, inicie, consulte y cierre una sesión de servidor segura (cookie de sesión, rotación de identificador, expiración por inactividad) y protege el login contra fuerza bruta con limitación de intentos.

## ADDED Requirements

### Requirement: Inicio de sesión con email y contraseña
`POST /api/v1/auth/login` con `{email, password}` SHALL autenticar al odontólogo activo comparando la contraseña contra su hash y, ante éxito, crear una sesión de servidor y responder `200` con `{email, nombre, apellido}`. El email MUST compararse sin distinguir mayúsculas ni espacios en los extremos.

#### Scenario: Credenciales válidas
- **GIVEN** existe el odontólogo `odontologo@consultorio.example` con contraseña `ClaveDemo-2026`
- **WHEN** se envía `POST /api/v1/auth/login` con esas credenciales y un token CSRF válido
- **THEN** la respuesta es `200` con `email` igual a `odontologo@consultorio.example` y se emite una cookie `JSESSIONID`

#### Scenario: Email con mayúsculas y espacios
- **GIVEN** existe el odontólogo `odontologo@consultorio.example`
- **WHEN** se inicia sesión con email ` Odontologo@Consultorio.EXAMPLE ` y la contraseña correcta
- **THEN** la respuesta es `200`

#### Scenario: Último acceso registrado
- **GIVEN** el reloj de la aplicación marca `2026-11-20T12:00:00Z`
- **WHEN** el odontólogo inicia sesión con éxito
- **THEN** su último acceso queda registrado como `2026-11-20T12:00:00Z`

#### Scenario: Respuesta sin datos sensibles
- **WHEN** el odontólogo inicia sesión con éxito
- **THEN** el cuerpo de la respuesta no contiene la contraseña ni su hash

### Requirement: Credenciales inválidas con respuesta genérica
Un login con email inexistente, contraseña incorrecta o usuario inactivo SHALL responder `401` con un `ProblemDetail` idéntico en los tres casos, sin crear sesión autenticada y sin revelar cuál dato falló.

#### Scenario: Contraseña incorrecta
- **WHEN** se inicia sesión con un email existente y una contraseña incorrecta
- **THEN** la respuesta es `401` con `detail` "Email o contraseña incorrectos." y la sesión sigue anónima

#### Scenario: Email inexistente indistinguible
- **WHEN** se inicia sesión con un email inexistente
- **THEN** la respuesta es `401` con el mismo `title` y `detail` que ante una contraseña incorrecta

#### Scenario: Usuario inactivo
- **GIVEN** el odontólogo está marcado como inactivo
- **WHEN** inicia sesión con la contraseña correcta
- **THEN** la respuesta es `401` con el mismo cuerpo genérico

#### Scenario: Cuerpo incompleto
- **WHEN** se envía `POST /api/v1/auth/login` sin el campo `password`
- **THEN** la respuesta es `400` con un error para el campo `password`

### Requirement: Rotación del identificador de sesión
Al iniciar sesión con éxito el sistema SHALL emitir un identificador de sesión distinto del que la solicitud traía, de modo que un identificador fijado antes del login no quede autenticado.

#### Scenario: Identificador previo no reutilizado
- **GIVEN** una solicitud trae una sesión anónima existente con identificador `S1`
- **WHEN** inicia sesión con éxito
- **THEN** la respuesta asigna un identificador de sesión distinto de `S1`

#### Scenario: Identificador previo no autenticado
- **GIVEN** un login exitoso partió de la sesión anónima `S1`
- **WHEN** se envía `GET /api/v1/auth/me` con la cookie de `S1`
- **THEN** la respuesta es `401`

### Requirement: Cookie de sesión segura
La cookie de sesión SHALL ser `HttpOnly` y `SameSite=Lax`, MUST llevar `Secure` cuando así se configure para HTTPS, y el identificador de sesión MUST NOT viajar nunca en la URL.

#### Scenario: Atributos de la cookie
- **WHEN** el odontólogo inicia sesión con éxito
- **THEN** la cookie `JSESSIONID` emitida tiene los atributos `HttpOnly` y `SameSite=Lax`

#### Scenario: Sin identificador en la URL
- **WHEN** se inspecciona cualquier respuesta de la aplicación
- **THEN** ningún enlace ni redirección contiene `;jsessionid=`

#### Scenario: Cookie Secure configurable
- **GIVEN** la aplicación está configurada con cookie de sesión segura
- **WHEN** el odontólogo inicia sesión con éxito
- **THEN** la cookie `JSESSIONID` tiene el atributo `Secure`

### Requirement: Consulta de la sesión actual
`GET /api/v1/auth/me` SHALL responder `200` con `{email, nombre, apellido}` del odontólogo cuando hay sesión autenticada y `401` en formato `ProblemDetail` cuando no la hay.

#### Scenario: Con sesión
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/auth/me`
- **THEN** la respuesta es `200` con su `email`, `nombre` y `apellido`

#### Scenario: Sin sesión
- **WHEN** se envía `GET /api/v1/auth/me` sin cookie de sesión
- **THEN** la respuesta es `401` con `Content-Type: application/problem+json`

### Requirement: Cierre de sesión
`POST /api/v1/auth/logout` SHALL invalidar la sesión en el servidor, expirar la cookie de sesión y responder `204`; con una sesión ya inexistente MUST responder también `204`.

#### Scenario: Logout invalida la sesión
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `POST /api/v1/auth/logout` con token CSRF válido
- **THEN** la respuesta es `204` y un `GET /api/v1/auth/me` posterior con la misma cookie responde `401`

#### Scenario: Logout sin sesión
- **WHEN** se envía `POST /api/v1/auth/logout` con token CSRF válido y sin sesión
- **THEN** la respuesta es `204`

#### Scenario: Logout sin token CSRF
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `POST /api/v1/auth/logout` sin cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` y la sesión sigue activa

### Requirement: Expiración por inactividad
La sesión SHALL expirar tras el tiempo de inactividad configurado (`SESSION_TIMEOUT_MIN`, 30 minutos por defecto); una solicitud con una sesión expirada MUST tratarse como no autenticada.

#### Scenario: Timeout por defecto
- **GIVEN** `SESSION_TIMEOUT_MIN` no está definida
- **WHEN** se crea una sesión al iniciar sesión
- **THEN** su intervalo máximo de inactividad es de 30 minutos

#### Scenario: Timeout configurable
- **GIVEN** `SESSION_TIMEOUT_MIN` vale `5`
- **WHEN** se crea una sesión al iniciar sesión
- **THEN** su intervalo máximo de inactividad es de 5 minutos

#### Scenario: Sesión expirada
- **GIVEN** la sesión del odontólogo expiró por inactividad
- **WHEN** envía `GET /api/v1/auth/me` con esa cookie
- **THEN** la respuesta es `401`

### Requirement: Limitación de intentos fallidos
Tras 5 fallos de login consecutivos para un mismo email o una misma IP, el sistema SHALL rechazar los intentos siguientes de esa clave con `429` y cabecera `Retry-After`, sin evaluar la contraseña, durante una espera que se duplica con cada nuevo bloqueo (1, 2, 4, 8 y como máximo 15 minutos).

#### Scenario: Quinto fallo bloquea
- **GIVEN** se registraron 5 fallos para `odontologo@consultorio.example`
- **WHEN** se intenta iniciar sesión con ese email y la contraseña correcta
- **THEN** la respuesta es `429` con `Retry-After: 60` y no se crea sesión

#### Scenario: Cuatro fallos no bloquean
- **GIVEN** se registraron 4 fallos para un email
- **WHEN** se inicia sesión con ese email y la contraseña correcta
- **THEN** la respuesta es `200`

#### Scenario: Bloqueo por IP con emails distintos
- **GIVEN** desde la IP `10.0.0.7` se registraron 5 fallos con 5 emails distintos
- **WHEN** desde esa IP se intenta iniciar sesión con cualquier email
- **THEN** la respuesta es `429`

#### Scenario: Espera creciente
- **GIVEN** un email quedó bloqueado 1 minuto y el bloqueo venció
- **WHEN** se registra un nuevo fallo para ese email
- **THEN** el email queda bloqueado 2 minutos

#### Scenario: Fin del bloqueo
- **GIVEN** un email quedó bloqueado 1 minuto
- **WHEN** pasan 61 segundos y se inicia sesión con la contraseña correcta
- **THEN** la respuesta es `200`

#### Scenario: Éxito reinicia el contador
- **GIVEN** se registraron 4 fallos para un email
- **WHEN** inicia sesión con éxito y luego registra 4 fallos más
- **THEN** el siguiente intento con la contraseña correcta responde `200`

#### Scenario: Olvido por inactividad
- **GIVEN** se registraron 4 fallos para un email hace más de 15 minutos
- **WHEN** se registra un nuevo fallo para ese email
- **THEN** el contador de ese email vale 1 y no hay bloqueo

#### Scenario: Email inexistente también se limita
- **GIVEN** se registraron 5 fallos para un email que no existe
- **WHEN** se intenta iniciar sesión con ese email
- **THEN** la respuesta es `429`, igual que para un email existente
