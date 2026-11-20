# Spec Delta

## Purpose

Define las pantallas con las que el odontólogo configura su consultorio desde la SPA: datos y parámetros del consultorio, perfil y contraseña, prestaciones, horarios semanales y bloqueos de agenda.

## ADDED Requirements

### Requirement: Acceso a la configuración
La aplicación SHALL ofrecer la ruta privada `/configuracion`, accesible desde un enlace "Configuración" del layout autenticado, con secciones Consultorio, Perfil, Prestaciones, Horarios y Bloqueos navegables sin recarga.

#### Scenario: Enlace en el layout
- **GIVEN** hay una sesión activa
- **WHEN** se renderiza cualquier ruta privada
- **THEN** la navegación incluye el enlace "Configuración" a `/configuracion`

#### Scenario: Sección por defecto
- **GIVEN** hay una sesión activa
- **WHEN** el usuario navega a `/configuracion`
- **THEN** se muestran las pestañas de las cinco secciones y la sección Consultorio activa

#### Scenario: Sección directa
- **GIVEN** hay una sesión activa
- **WHEN** el usuario navega a `/configuracion/horarios`
- **THEN** se muestra la sección Horarios

#### Scenario: Sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a `/configuracion/prestaciones`
- **THEN** la aplicación redirige a `/login?next=/configuracion/prestaciones`

### Requirement: Presentación de errores de la API en formularios
Todo formulario de configuración SHALL mostrar los errores por campo de un `422` junto al campo correspondiente, el título del error si no hay errores de campo, un mensaje de conexión ante falla de red, y MUST conservar lo cargado por el usuario.

#### Scenario: Error de campo del servidor
- **GIVEN** el formulario de prestación está cargado con duración 40
- **WHEN** la API responde `422` con error en `duracionMin` "Debe ser múltiplo de 15"
- **THEN** el mensaje aparece junto al campo de duración y los valores cargados se conservan

#### Scenario: Error sin campos
- **WHEN** la API responde `409` con título "Conflicto"
- **THEN** el formulario muestra "Conflicto" como error general

#### Scenario: Falla de red
- **WHEN** la solicitud falla por red
- **THEN** el formulario muestra un mensaje de que no se pudo conectar con el servidor

#### Scenario: Envío en curso
- **WHEN** el usuario envía un formulario y la respuesta no llegó
- **THEN** el botón de envío está deshabilitado hasta que llegue la respuesta

### Requirement: Datos y parámetros del consultorio
La sección Consultorio SHALL mostrar y permitir editar nombre, dirección, teléfono, email de contacto y los parámetros (granularidad, anticipación mínima, límite de cancelación, recordatorio, vencimiento de avisos y de solicitudes pendientes) con `GET/PUT /api/v1/consultorio`.

#### Scenario: Carga inicial
- **GIVEN** `GET /api/v1/consultorio` responde con nombre "Consultorio Demo" y granularidad 15
- **WHEN** se abre la sección Consultorio
- **THEN** los campos muestran esos valores

#### Scenario: Guardado exitoso
- **WHEN** el usuario cambia el recordatorio a 48 h y guarda, y la API responde `200`
- **THEN** se envía `PUT /api/v1/consultorio` con el valor 48 y se muestra una confirmación de guardado

#### Scenario: Parámetro no positivo
- **WHEN** el usuario ingresa granularidad 0 y guarda
- **THEN** no se envía la solicitud y se indica que el valor debe ser mayor que cero

#### Scenario: Aviso de alcance de los cambios
- **WHEN** se muestra la sección Consultorio
- **THEN** se informa que los cambios de parámetros aplican a operaciones nuevas y no a turnos ya creados

### Requirement: Perfil y cambio de contraseña
La sección Perfil SHALL permitir editar nombre, apellido y matrícula del odontólogo con `GET/PUT /api/v1/perfil`, y cambiar la contraseña con `POST /api/v1/perfil/password` pidiendo la actual, la nueva y su repetición.

#### Scenario: Edición del perfil
- **WHEN** el usuario cambia la matrícula y guarda, y la API responde `200`
- **THEN** se muestra una confirmación de guardado

#### Scenario: Contraseñas que no coinciden
- **WHEN** la nueva contraseña y su repetición difieren
- **THEN** no se envía la solicitud y se indica que no coinciden

#### Scenario: Contraseña cambiada
- **WHEN** el usuario envía el cambio y la API responde `204`
- **THEN** se muestra una confirmación y los tres campos de contraseña quedan vacíos

#### Scenario: Contraseña actual incorrecta
- **WHEN** la API responde `422` con error en `passwordActual`
- **THEN** el error aparece junto al campo de contraseña actual y los campos de contraseña quedan vacíos

### Requirement: Gestión de prestaciones
La sección Prestaciones SHALL listar todas las prestaciones con nombre, duración y estado, y permitir crear, editar, desactivar y reactivar, reflejando el resultado sin recargar la página.

#### Scenario: Listado
- **GIVEN** la API devuelve "Consulta" 30 min activa y "Control" 15 min inactiva
- **WHEN** se abre la sección Prestaciones
- **THEN** se listan ambas, con "Control" marcada como inactiva y acción "Activar"

#### Scenario: Alta
- **WHEN** el usuario crea "Blanqueamiento" de 60 min y la API responde `201`
- **THEN** "Blanqueamiento" aparece en el listado como activa

#### Scenario: Desactivar
- **WHEN** el usuario activa "Desactivar" en "Endodoncia" y confirma
- **THEN** se envía `POST /api/v1/prestaciones/{id}/desactivar` y la fila pasa a inactiva

#### Scenario: Lista vacía
- **GIVEN** la API devuelve una lista vacía
- **WHEN** se abre la sección Prestaciones
- **THEN** se indica que no hay prestaciones cargadas y se ofrece crear una

### Requirement: Validación de duración en el cliente
El formulario de prestación SHALL rechazar antes de enviar una duración vacía, no entera, no positiva o no múltiplo de la granularidad del consultorio, y un nombre vacío.

#### Scenario: Duración no múltiplo
- **GIVEN** la granularidad del consultorio es 15
- **WHEN** el usuario ingresa duración 40 y guarda
- **THEN** no se envía la solicitud y se indica que debe ser múltiplo de 15

#### Scenario: Granularidad tomada del consultorio
- **GIVEN** la granularidad del consultorio es 30
- **WHEN** el usuario ingresa duración 45 y guarda
- **THEN** no se envía la solicitud y se indica que debe ser múltiplo de 30

#### Scenario: Nombre vacío
- **WHEN** el usuario guarda con el nombre vacío
- **THEN** no se envía la solicitud y se indica que el nombre es obligatorio

### Requirement: Gestión de horarios semanales
La sección Horarios SHALL mostrar los horarios agrupados por día de lunes a domingo, permitir agregar, editar y eliminar franjas, y validar antes de enviar que la hora de inicio sea menor que la de fin y que no se solape con otra franja del mismo día.

#### Scenario: Agrupado por día
- **GIVEN** la API devuelve lunes 09:00-13:00 y lunes 14:00-18:00
- **WHEN** se abre la sección Horarios
- **THEN** el lunes muestra ambas franjas en orden y los días sin franjas indican "Sin atención"

#### Scenario: Rango invertido
- **WHEN** el usuario agrega una franja 14:00-13:00
- **THEN** no se envía la solicitud y se indica que la hora de fin debe ser posterior a la de inicio

#### Scenario: Solapamiento detectado en el cliente
- **GIVEN** el lunes tiene la franja 09:00-13:00
- **WHEN** el usuario agrega lunes 12:00-15:00
- **THEN** no se envía la solicitud y se indica que se superpone con 09:00-13:00

#### Scenario: Eliminar franja
- **WHEN** el usuario elimina una franja, confirma y la API responde `204`
- **THEN** la franja desaparece del día

### Requirement: Gestión de bloqueos
La sección Bloqueos SHALL listar los bloqueos vigentes y futuros con su rango y motivo, y permitir crear, editar y eliminar bloqueos ingresando fecha y hora en la zona del consultorio.

#### Scenario: Conversión a UTC al crear
- **WHEN** el usuario crea un bloqueo del 24/12/2026 00:00 al 26/12/2026 00:00 (hora de Buenos Aires)
- **THEN** se envía `desde` "2026-12-24T03:00:00Z" y `hasta` "2026-12-26T03:00:00Z"

#### Scenario: Presentación en hora local
- **GIVEN** la API devuelve un bloqueo con `desde` "2026-12-24T03:00:00Z"
- **WHEN** se abre la sección Bloqueos
- **THEN** se muestra como 24/12/2026 00:00

#### Scenario: Rango invertido
- **WHEN** el usuario ingresa un fin anterior al inicio
- **THEN** no se envía la solicitud y se indica que el fin debe ser posterior al inicio

#### Scenario: Eliminar
- **WHEN** el usuario elimina un bloqueo, confirma y la API responde `204`
- **THEN** el bloqueo desaparece del listado
