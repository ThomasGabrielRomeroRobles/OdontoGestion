# Spec Delta

## Purpose

Gestiona la cuenta del único odontólogo: su alta inicial por bootstrap sin registro público, el almacenamiento de la contraseña con hash adaptativo, la consulta y edición de su perfil y el cambio de contraseña.

## ADDED Requirements

### Requirement: Alta inicial del odontólogo por bootstrap
Al arrancar, si no existe ningún usuario, el sistema SHALL crear un único odontólogo ficticio activo, vinculado al consultorio, con el email de `BOOTSTRAP_ODONTOLOGO_EMAIL` (por defecto `odontologo@consultorio.example`) y la contraseña de `BOOTSTRAP_ODONTOLOGO_PASSWORD`.

#### Scenario: Primer arranque crea al odontólogo
- **GIVEN** una base sin usuarios y `BOOTSTRAP_ODONTOLOGO_PASSWORD` con una contraseña válida
- **WHEN** la aplicación arranca
- **THEN** existe exactamente un usuario activo con el email configurado, vinculado al consultorio, y puede iniciar sesión con esa contraseña

#### Scenario: Email por defecto
- **GIVEN** una base sin usuarios y `BOOTSTRAP_ODONTOLOGO_EMAIL` sin definir
- **WHEN** la aplicación arranca
- **THEN** el usuario creado tiene email `odontologo@consultorio.example`

### Requirement: Bootstrap idempotente
Si ya existe al menos un usuario, el arranque SHALL dejar intactos los usuarios existentes (incluida su contraseña) aunque las variables de bootstrap tengan otros valores.

#### Scenario: Segundo arranque sin duplicar
- **GIVEN** el bootstrap ya creó al odontólogo
- **WHEN** el proceso de bootstrap se ejecuta de nuevo
- **THEN** sigue existiendo exactamente un usuario

#### Scenario: Contraseña cambiada se conserva
- **GIVEN** el odontólogo cambió su contraseña después del bootstrap
- **WHEN** el bootstrap se ejecuta de nuevo con la contraseña original en `BOOTSTRAP_ODONTOLOGO_PASSWORD`
- **THEN** la contraseña vigente sigue siendo la que eligió el odontólogo

#### Scenario: Email de bootstrap distinto con usuario existente
- **GIVEN** existe un usuario y `BOOTSTRAP_ODONTOLOGO_EMAIL` vale otro email
- **WHEN** el bootstrap se ejecuta
- **THEN** no se crea un segundo usuario

### Requirement: Bootstrap sin contraseña válida falla de forma segura
Si no existe ningún usuario y `BOOTSTRAP_ODONTOLOGO_PASSWORD` falta o no cumple la política de contraseñas, el arranque SHALL fallar con un mensaje que nombre la variable y MUST NOT registrar su valor.

#### Scenario: Variable ausente
- **GIVEN** una base sin usuarios y `BOOTSTRAP_ODONTOLOGO_PASSWORD` sin definir
- **WHEN** se ejecuta el bootstrap
- **THEN** falla con un mensaje que menciona `BOOTSTRAP_ODONTOLOGO_PASSWORD` y no se crea ningún usuario

#### Scenario: Contraseña demasiado corta
- **GIVEN** una base sin usuarios y `BOOTSTRAP_ODONTOLOGO_PASSWORD` igual a `corta`
- **WHEN** se ejecuta el bootstrap
- **THEN** falla, no se crea ningún usuario y el log no contiene `corta`

#### Scenario: Variable ausente con usuario existente
- **GIVEN** existe un usuario y `BOOTSTRAP_ODONTOLOGO_PASSWORD` no está definida
- **WHEN** la aplicación arranca
- **THEN** el arranque es exitoso

### Requirement: Contraseñas con hash adaptativo
Las contraseñas SHALL almacenarse solo como hash BCrypt con factor de costo de al menos 10 y MUST NOT aparecer en texto plano en la base, en respuestas de la API ni en los logs.

#### Scenario: Hash almacenado
- **GIVEN** el bootstrap creó al odontólogo con la contraseña `ClaveDemo-2026`
- **WHEN** se lee el hash almacenado
- **THEN** tiene formato BCrypt (`$2a$`, `$2b$` o `$2y$`), costo ≥ 10 y no contiene `ClaveDemo-2026`

#### Scenario: Contraseña ausente de los logs
- **WHEN** se ejecutan el bootstrap, un login exitoso, un login fallido y un cambio de contraseña
- **THEN** ninguna de las contraseñas usadas aparece en la salida de log

### Requirement: Email único sin distinguir mayúsculas
El email del usuario SHALL ser único en el sistema sin distinguir mayúsculas, garantizado por una restricción de la base de datos, y se MUST almacenar normalizado (sin espacios en los extremos y en minúsculas).

#### Scenario: Duplicado con otra capitalización rechazado por la base
- **GIVEN** existe un usuario con email `odontologo@consultorio.example`
- **WHEN** se inserta directamente en la base otro usuario con email `ODONTOLOGO@consultorio.example`
- **THEN** la inserción es rechazada por violación de unicidad

#### Scenario: Email normalizado
- **GIVEN** `BOOTSTRAP_ODONTOLOGO_EMAIL` vale ` Odontologo@Consultorio.Example `
- **WHEN** el bootstrap crea al usuario
- **THEN** el email almacenado es `odontologo@consultorio.example`

### Requirement: Consulta y edición del perfil
`GET /api/v1/perfil` SHALL devolver `{email, nombre, apellido, matricula}` del odontólogo de la sesión y `PUT /api/v1/perfil` con `{nombre, apellido, matricula}` SHALL actualizarlos y devolver el perfil resultante. El email MUST NOT poder modificarse por esta vía.

#### Scenario: Leer el perfil
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/perfil`
- **THEN** la respuesta es `200` con su `email`, `nombre`, `apellido` y `matricula`

#### Scenario: Editar el perfil
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `PUT /api/v1/perfil` con `nombre` "Laura", `apellido` "Pérez" y `matricula` "MN-12345"
- **THEN** la respuesta es `200` con esos valores y un `GET /api/v1/perfil` posterior los devuelve

#### Scenario: Nombre vacío
- **WHEN** el odontólogo envía `PUT /api/v1/perfil` con `nombre` vacío
- **THEN** la respuesta es `400` con un error para el campo `nombre` y el perfil no cambia

#### Scenario: Email no modificable
- **WHEN** el odontólogo envía `PUT /api/v1/perfil` incluyendo `email` "otro@consultorio.example"
- **THEN** el email del perfil sigue siendo el original

#### Scenario: Matrícula opcional
- **WHEN** el odontólogo envía `PUT /api/v1/perfil` sin `matricula`
- **THEN** la respuesta es `200` y `matricula` queda vacía

### Requirement: Cambio de contraseña
`POST /api/v1/perfil/password` con `{passwordActual, passwordNueva}` SHALL reemplazar la contraseña y responder `204` cuando `passwordActual` es correcta y `passwordNueva` cumple la política; la sesión actual MUST seguir activa con un identificador de sesión nuevo.

#### Scenario: Cambio exitoso
- **GIVEN** el odontólogo inició sesión con la contraseña `ClaveDemo-2026`
- **WHEN** envía `POST /api/v1/perfil/password` con `passwordActual` `ClaveDemo-2026` y `passwordNueva` `OtraClave-2026`
- **THEN** la respuesta es `204`, un login posterior con `OtraClave-2026` responde `200` y con `ClaveDemo-2026` responde `401`

#### Scenario: Sesión conservada y rotada
- **GIVEN** el odontólogo inició sesión con el identificador `S1`
- **WHEN** cambia su contraseña con éxito
- **THEN** la respuesta asigna un identificador distinto de `S1` y `GET /api/v1/auth/me` con el nuevo identificador responde `200`

#### Scenario: Contraseña actual incorrecta
- **WHEN** el odontólogo envía `passwordActual` incorrecta
- **THEN** la respuesta es `422` y la contraseña no cambia

#### Scenario: Campo ausente
- **WHEN** el odontólogo envía el cuerpo sin `passwordNueva`
- **THEN** la respuesta es `400` con un error para el campo `passwordNueva`

### Requirement: Política de contraseñas
Una contraseña nueva (de bootstrap o de cambio) SHALL tener entre 8 caracteres y 72 bytes en UTF-8 y, en un cambio, MUST ser distinta de la actual; si no cumple, el cambio responde `422`.

#### Scenario: Demasiado corta
- **WHEN** el odontólogo cambia su contraseña por `Ab-1234` (7 caracteres)
- **THEN** la respuesta es `422` y la contraseña no cambia

#### Scenario: Longitud mínima aceptada
- **WHEN** el odontólogo cambia su contraseña por `Ab-12345` (8 caracteres)
- **THEN** la respuesta es `204`

#### Scenario: Demasiado larga
- **WHEN** el odontólogo cambia su contraseña por una de 73 bytes en UTF-8
- **THEN** la respuesta es `422`

#### Scenario: Igual a la actual
- **WHEN** el odontólogo envía `passwordNueva` igual a `passwordActual`
- **THEN** la respuesta es `422`
