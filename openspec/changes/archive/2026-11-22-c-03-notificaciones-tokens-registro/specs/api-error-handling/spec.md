# Spec Delta

## ADDED Requirements

### Requirement: Token de acceso inválido como recurso desaparecido
Cuando un caso de uso informa que un token de acceso del paciente es inválido (inexistente, mal formado, vencido, usado, revocado o de otro propósito), la respuesta SHALL ser `410 Gone` en formato `ProblemDetail` con el mismo `type`, `title` y `detail` genéricos para todas las causas, sin datos del turno, aviso o entrada, y MUST NOT incluir ni registrar en logs el valor del token.

#### Scenario: Token inválido
- **WHEN** un caso de uso informa un token de acceso inválido
- **THEN** la respuesta es `410` en formato `ProblemDetail` con un `detail` genérico que no menciona la causa

#### Scenario: Cuerpos idénticos para causas distintas
- **WHEN** se compara la respuesta de un token vencido con la de un token inexistente
- **THEN** ambas tienen el mismo estado, `type`, `title` y `detail`

#### Scenario: Token ausente de la respuesta y de los logs
- **WHEN** una solicitud con un token inválido en la ruta produce el `410`
- **THEN** ni el cuerpo de la respuesta ni los logs de la aplicación contienen el valor del token
