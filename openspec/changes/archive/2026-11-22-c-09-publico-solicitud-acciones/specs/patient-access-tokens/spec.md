# Spec Delta

## ADDED Requirements

### Requirement: Identificación sin consumo entre propósitos admitidos
El sistema SHALL ofrecer una consulta de solo lectura que, dado un valor y un conjunto de propósitos admitidos, devuelva el propósito, la referencia y el vencimiento del token si existe, no fue usado, no venció y su propósito pertenece al conjunto. Si no, MUST rechazarlo como token inválido, con el mismo rechazo uniforme, y nunca modificar el token.

#### Scenario: Identificar un token vigente
- **GIVEN** un token `CANCELAR_TURNO` vigente para el turno 101
- **WHEN** se identifica con los propósitos admitidos `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **THEN** se obtiene el propósito `CANCELAR_TURNO`, la referencia al turno 101 y su vencimiento, y el token sigue sin usar

#### Scenario: Propósito fuera del conjunto
- **GIVEN** un token `ACEPTAR_AVISO` vigente
- **WHEN** se identifica con los propósitos admitidos `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **THEN** es rechazado como token inválido y sigue consumible con `ACEPTAR_AVISO`

#### Scenario: Token usado o vencido
- **WHEN** se identifica un token ya usado o uno vencido según el reloj de la aplicación
- **THEN** ambos son rechazados con el mismo rechazo que un valor inexistente
