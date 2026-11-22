# Spec Delta

## Purpose

Pantalla privada del odontólogo para revisar el registro de envíos a pacientes, filtrarlo por estado y tipo, y reintentar los envíos fallidos sin salir de la aplicación.

## ADDED Requirements

### Requirement: Pantalla del registro de envíos
La aplicación SHALL ofrecer la ruta privada `/notificaciones`, accesible desde la navegación del marco autenticado, que muestre las notificaciones con tipo, destinatario, estado (`PENDIENTE`, `ENVIADA`, `FALLIDA`), intentos, último error y fechas en la zona horaria de presentación.

#### Scenario: Listado visible
- **GIVEN** la API devuelve tres notificaciones en estados distintos
- **WHEN** el odontólogo autenticado abre `/notificaciones`
- **THEN** ve una fila por notificación con su tipo, destinatario, estado, intentos y error, y la fecha mostrada en America/Argentina/Buenos_Aires

#### Scenario: Registro vacío
- **GIVEN** la API devuelve una página vacía
- **WHEN** el odontólogo abre `/notificaciones`
- **THEN** ve un mensaje que indica que no hay envíos registrados

#### Scenario: Acceso sin sesión
- **WHEN** alguien sin sesión navega a `/notificaciones`
- **THEN** es redirigido al inicio de sesión con el regreso a `/notificaciones`

### Requirement: Filtros y paginación
La pantalla SHALL permitir filtrar por estado y por tipo y navegar entre páginas; cada cambio MUST volver a consultar la API con los filtros y la página elegidos.

#### Scenario: Filtrar por estado
- **WHEN** el odontólogo elige el estado `FALLIDA`
- **THEN** la pantalla consulta la API con `estado=FALLIDA` desde la primera página y muestra el resultado

#### Scenario: Página siguiente
- **GIVEN** la API indica que hay más páginas
- **WHEN** el odontólogo pide la página siguiente
- **THEN** la pantalla consulta la API con la página siguiente conservando los filtros

### Requirement: Reintento desde la pantalla
Cada notificación `FALLIDA` SHALL mostrar una acción de reintento que invoque el reintento de la API y, si tiene éxito, actualice la fila; los errores de la API MUST mostrarse con el mensaje del `ProblemDetail`.

#### Scenario: Reintento exitoso
- **GIVEN** una notificación `FALLIDA`
- **WHEN** el odontólogo pulsa reintentar y la API responde `200`
- **THEN** la fila pasa a mostrarse `PENDIENTE` y la acción de reintento desaparece

#### Scenario: Reintento rechazado
- **WHEN** el odontólogo pulsa reintentar y la API responde `422` con un `ProblemDetail`
- **THEN** la pantalla muestra el `detail` del error y la fila no cambia

#### Scenario: Solo las fallidas se reintentan
- **WHEN** la pantalla muestra notificaciones `PENDIENTE` y `ENVIADA`
- **THEN** esas filas no muestran la acción de reintento
