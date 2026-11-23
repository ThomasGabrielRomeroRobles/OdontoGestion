# Spec Delta

## MODIFIED Requirements

### Requirement: Resumen de la acción sin consumir el token
`GET /api/v1/acciones/{token}` SHALL devolver, para un token vigente de propósito `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` o `CANCELAR_TURNO`, la acción, su vencimiento y un resumen del turno (consultorio, prestación, inicio, fin, estado), sin datos del paciente; para `CONFIRMAR_LISTA_ESPERA`, la acción, su vencimiento y un resumen de la entrada (consultorio, prestación, `fechaDesde`, `fechaHasta`, estado) con `turno` nulo. MUST NOT consumir ni modificar el token, el turno ni la entrada.

#### Scenario: Resumen de un token vigente
- **GIVEN** un token `CANCELAR_TURNO` vigente para un turno `RESERVADO` de "Consulta"
- **WHEN** se envía `GET /api/v1/acciones/{token}` sin sesión
- **THEN** la respuesta es `200` con `accion` `CANCELAR_TURNO`, `venceEn`, y un `turno` con consultorio, prestación "Consulta", `inicio`, `fin` y `estado` `RESERVADO`, sin nombre, DNI, email ni teléfono del paciente

#### Scenario: Mostrar varias veces no consume
- **GIVEN** un token `CONFIRMAR_TURNO` vigente
- **WHEN** se envía el `GET` tres veces y luego el `POST` de confirmación
- **THEN** los tres `GET` responden `200` y el `POST` ejecuta la acción

#### Scenario: Token de otro tipo de ruta
- **GIVEN** un token vigente de propósito `ACEPTAR_AVISO`
- **WHEN** se envía `GET /api/v1/acciones/{token}`
- **THEN** la respuesta es `410` y el token no se consume

#### Scenario: Resumen de una inscripción en lista de espera
- **GIVEN** un token `CONFIRMAR_LISTA_ESPERA` vigente para una entrada `PENDIENTE_CONFIRMACION` de "Consulta"
- **WHEN** se envía `GET /api/v1/acciones/{token}` sin sesión
- **THEN** la respuesta es `200` con `accion` `CONFIRMAR_LISTA_ESPERA`, `disponible` verdadero, `turno` nulo y una `entrada` con prestación "Consulta", `fechaDesde`, `fechaHasta` y `estado` `PENDIENTE_CONFIRMACION`, sin datos del paciente

## ADDED Requirements

### Requirement: Confirmar la inscripción en la lista de espera
El `POST` de `/api/v1/acciones/{token}` con la acción `CONFIRMAR_LISTA_ESPERA` SHALL consumir el token y pasar la entrada de `PENDIENTE_CONFIRMACION` a `ACTIVA` en la misma transacción. Si la entrada ya no está pendiente (retirada o vencida) MUST responder `410` o `422` sin activarla.

#### Scenario: Confirmación exitosa
- **GIVEN** un token `CONFIRMAR_LISTA_ESPERA` vigente para una entrada `PENDIENTE_CONFIRMACION`
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_LISTA_ESPERA`
- **THEN** la respuesta es `200` con la `entrada` en estado `ACTIVA` y el token queda usado

#### Scenario: Entrada retirada antes de confirmar
- **GIVEN** una entrada que el odontólogo pasó a `CANCELADA` antes de que el paciente confirme
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_LISTA_ESPERA`
- **THEN** la respuesta es `410` y la entrada sigue `CANCELADA`
