# Spec Delta

## Purpose

Registra la historia clínica simple de cada paciente del consultorio (anamnesis, odontograma FDI por pieza y notas de evolución), accesible solo por el odontólogo con sesión, con autor y fecha en cada modificación y sin exponer datos clínicos fuera de la API privada.

> Convención de los escenarios: la zona del consultorio es `America/Argentina/Buenos_Aires` (UTC−03:00). "Ahora" es el reloj de la aplicación. Todos los pacientes son ficticios. Las rutas son relativas a `/api/v1/pacientes/{id}/historia`.

## ADDED Requirements

### Requirement: Una única historia clínica por paciente
Cada paciente SHALL tener como máximo una historia clínica (RN-HC-01), vinculada al consultorio del paciente. La unicidad MUST garantizarse en la base, no solo en la aplicación.

#### Scenario: Escrituras sucesivas usan la misma historia
- **GIVEN** el odontólogo inició sesión y el paciente 7 no tiene historia
- **WHEN** guarda la anamnesis y después registra el estado de la pieza 16
- **THEN** existe exactamente una historia clínica para el paciente 7 y ambos datos cuelgan de ella

#### Scenario: Primeras escrituras concurrentes
- **GIVEN** el paciente 7 no tiene historia
- **WHEN** llegan a la vez la anamnesis y una evolución para el paciente 7
- **THEN** ambas escrituras se guardan y existe exactamente una historia clínica para el paciente 7

#### Scenario: Unicidad garantizada por la base
- **GIVEN** el paciente 7 tiene historia clínica
- **WHEN** se inserta directamente en la base otra historia para el paciente 7
- **THEN** la inserción es rechazada

### Requirement: Creación perezosa de la historia
La historia clínica SHALL crearse con la primera escritura clínica del odontólogo (RN-PA-05). La consulta MUST NOT crearla: para un paciente sin historia devuelve una ficha vacía.

#### Scenario: Consulta de un paciente sin historia
- **GIVEN** el paciente 7 existe y no tiene historia
- **WHEN** el odontólogo pide `GET /historia`
- **THEN** la respuesta es `200` con anamnesis vacía, las 32 piezas sin estado y ninguna evolución, y la base sigue sin historia para el paciente 7

#### Scenario: Paciente inexistente
- **WHEN** el odontólogo pide `GET /api/v1/pacientes/999999/historia` y el paciente no existe en el consultorio
- **THEN** la respuesta es `404` en formato `ProblemDetail`

### Requirement: Acceso exclusivo del odontólogo
Todos los endpoints de historia clínica SHALL exigir la sesión del odontólogo (RN-HC-02) y las escrituras MUST exigir el token CSRF. Ninguna ruta pública ni por token MUST devolver datos clínicos.

#### Scenario: Sin sesión
- **GIVEN** no hay sesión
- **WHEN** se pide `GET /historia` de un paciente existente
- **THEN** la respuesta es `401` y no contiene datos clínicos

#### Scenario: Escritura sin CSRF
- **GIVEN** hay sesión del odontólogo
- **WHEN** envía `PUT /historia/anamnesis` sin token CSRF
- **THEN** la respuesta es `403` y la anamnesis no cambia

### Requirement: Anamnesis editable con autoría
El odontólogo SHALL poder crear y reemplazar la anamnesis (alergias, medicación actual, enfermedades previas, antecedentes relevantes, embarazo opcional y observaciones). Cada guardado MUST registrar el autor y la fecha, que la consulta devuelve.

#### Scenario: Crear la anamnesis
- **GIVEN** el paciente 7 no tiene historia y ahora es `2026-11-26T13:00:00Z`
- **WHEN** el odontólogo envía `PUT /historia/anamnesis` con `alergias` "Penicilina" y `embarazo` nulo
- **THEN** la respuesta es `200`, la anamnesis devuelve "Penicilina", el nombre del odontólogo como autor y `2026-11-26T13:00:00Z` como fecha

#### Scenario: Reemplazar la anamnesis
- **GIVEN** la anamnesis del paciente 7 tiene `alergias` "Penicilina" y `medicacionActual` "Ibuprofeno"
- **WHEN** el odontólogo envía `PUT /historia/anamnesis` con `alergias` "Ninguna" y sin `medicacionActual`
- **THEN** la anamnesis queda con `alergias` "Ninguna" y `medicacionActual` vacía

#### Scenario: Texto demasiado largo
- **WHEN** el odontólogo envía una anamnesis con `observaciones` de 2001 caracteres
- **THEN** la respuesta es `400` en formato `ProblemDetail` con el campo señalado y la anamnesis no cambia

### Requirement: Odontograma FDI de 32 piezas permanentes
El odontograma SHALL usar numeración FDI y aceptar solo las 32 piezas permanentes (11-18, 21-28, 31-38, 41-48). Cada pieza MUST tener a lo sumo un estado vigente; la consulta devuelve siempre las 32 piezas, con estado nulo las no registradas.

#### Scenario: Registrar el estado de una pieza
- **GIVEN** ahora es `2026-11-26T13:00:00Z`
- **WHEN** el odontólogo envía `PUT /historia/odontograma/piezas/16` con `estado` `CARIES` y `observacion` "Oclusal"
- **THEN** la respuesta es `200` y la pieza 16 queda `CARIES` con "Oclusal", el odontólogo como autor y `2026-11-26T13:00:00Z` como fecha

#### Scenario: Pieza fuera del conjunto FDI permanente
- **WHEN** el odontólogo envía `PUT /historia/odontograma/piezas/19` (o `51`, temporaria) con un estado válido
- **THEN** la respuesta es `422` en formato `ProblemDetail` con código `pieza-fdi-invalida` y no se crea historia ni pieza

#### Scenario: Bordes del rango FDI
- **WHEN** el odontólogo registra las piezas `11`, `18`, `41` y `48`
- **THEN** las cuatro se guardan, mientras que `10`, `49` y `20` responden `422`

#### Scenario: Actualizar una pieza ya registrada
- **GIVEN** la pieza 16 está `CARIES`
- **WHEN** el odontólogo la registra como `OBTURADA`
- **THEN** la pieza 16 queda `OBTURADA` y existe un único registro para la pieza 16 de esa historia

#### Scenario: Estado desconocido
- **WHEN** el odontólogo envía la pieza 16 con `estado` `ROTA`
- **THEN** la respuesta es `400` en formato `ProblemDetail` y la pieza no cambia

### Requirement: Estados de pieza del MVP
El estado de una pieza SHALL ser uno de `SANA`, `CARIES`, `OBTURADA`, `CORONA`, `ENDODONCIA`, `AUSENTE`, `IMPLANTE`, `FRACTURADA` o `A_EXTRAER`, por pieza completa (sin caras). La observación MUST ser opcional y de hasta 500 caracteres.

#### Scenario: Todos los estados aceptados
- **WHEN** el odontólogo registra la pieza 21 con cada uno de los nueve estados
- **THEN** cada registro responde `200` con el estado enviado

#### Scenario: Observación demasiado larga
- **WHEN** el odontólogo registra la pieza 21 con una observación de 501 caracteres
- **THEN** la respuesta es `400` y la pieza no cambia

### Requirement: Notas de evolución con autor y turno opcional
El odontólogo SHALL poder registrar notas de evolución (texto de 1 a 4000 caracteres, fecha de la consulta no futura en hora del consultorio, por defecto hoy) con autor y fecha de alta (RN-HC-04). El turno asociado MUST ser opcional y, si se indica, pertenecer al mismo paciente.

#### Scenario: Alta con turno del paciente
- **GIVEN** el turno 40 es del paciente 7
- **WHEN** el odontólogo envía `POST /historia/evoluciones` con `nota` "Obturación pieza 16" y `turnoId` 40
- **THEN** la respuesta es `201` con la nota, el turno 40, el odontólogo como autor y la fecha de alta

#### Scenario: Turno de otro paciente
- **GIVEN** el turno 41 es del paciente 8
- **WHEN** el odontólogo registra una evolución del paciente 7 con `turnoId` 41
- **THEN** la respuesta es `422` en formato `ProblemDetail` con código `turno-de-otro-paciente` y no se guarda la evolución

#### Scenario: Fecha futura
- **GIVEN** hoy es `2026-11-26` en el consultorio
- **WHEN** el odontólogo registra una evolución con `fecha` `2026-11-27`
- **THEN** la respuesta es `422` y no se guarda la evolución

#### Scenario: Nota vacía
- **WHEN** el odontólogo registra una evolución con `nota` "   "
- **THEN** la respuesta es `400` y no se guarda la evolución

### Requirement: Evoluciones en orden cronológico
El listado de evoluciones SHALL devolverse en orden cronológico ascendente por fecha de la consulta y, a igual fecha, por fecha de alta.

#### Scenario: Orden del listado
- **GIVEN** el paciente 7 tiene evoluciones con fechas `2026-11-20`, `2026-10-05` y `2026-11-20` (esta última dada de alta después)
- **WHEN** el odontólogo pide `GET /historia/evoluciones`
- **THEN** se listan `2026-10-05`, la primera de `2026-11-20` y la segunda de `2026-11-20`, en ese orden

### Requirement: Edición de una evolución por su autor dentro de la ventana
Una evolución SHALL poder editarse solo por su autor y solo dentro de la ventana de edición (24 h desde su alta por defecto). La edición MUST conservar la fecha de alta y registrar la fecha de edición; la respuesta indica si la evolución sigue editable.

#### Scenario: Edición dentro de la ventana
- **GIVEN** el odontólogo dio de alta una evolución a las `2026-11-26T10:00:00Z` y ahora es `2026-11-26T20:00:00Z`
- **WHEN** envía `PUT /historia/evoluciones/{evolucionId}` con una nota nueva
- **THEN** la respuesta es `200` con la nota nueva, la misma fecha de alta y `2026-11-26T20:00:00Z` como fecha de edición

#### Scenario: Edición fuera de la ventana
- **GIVEN** la evolución se dio de alta a las `2026-11-25T10:00:00Z` y ahora es `2026-11-26T10:00:01Z`
- **WHEN** el autor intenta editarla
- **THEN** la respuesta es `422` con código `evolucion-fuera-de-ventana` y la nota no cambia

#### Scenario: Justo en el límite de la ventana
- **GIVEN** la evolución se dio de alta a las `2026-11-25T10:00:00Z` y ahora es `2026-11-26T10:00:00Z`
- **WHEN** el autor la edita
- **THEN** la edición se acepta

#### Scenario: Otro usuario no es el autor
- **GIVEN** la evolución fue escrita por otro usuario del consultorio
- **WHEN** el odontólogo con sesión intenta editarla
- **THEN** la respuesta es `403` en formato `ProblemDetail` y la nota no cambia

#### Scenario: Evolución de otro paciente
- **WHEN** el odontólogo edita por la ruta del paciente 7 una evolución que pertenece al paciente 8
- **THEN** la respuesta es `404`

### Requirement: Datos clínicos fuera de logs, URLs y errores
Los datos clínicos (anamnesis, observaciones de piezas, notas de evolución) MUST NOT aparecer en logs, en URLs ni en notificaciones (RN-HC-06), ni en el `detail` de las respuestas de error.

#### Scenario: Escrituras sin datos clínicos en los logs
- **GIVEN** se capturan los logs de la aplicación
- **WHEN** el odontólogo guarda una anamnesis con `alergias` "AlergiaFicticiaXYZ", una pieza con observación "ObsFicticiaXYZ" y una evolución con nota "NotaFicticiaXYZ"
- **THEN** ninguno de esos textos aparece en los logs capturados

#### Scenario: Error de validación sin eco del contenido
- **WHEN** el odontólogo envía una nota de evolución de 4001 caracteres que empieza con "NotaFicticiaXYZ"
- **THEN** la respuesta `400` no contiene "NotaFicticiaXYZ"
