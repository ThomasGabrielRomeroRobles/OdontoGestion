# Spec Delta

## ADDED Requirements

### Requirement: Acceso a la historia clínica desde el turno
El detalle de un turno SHALL ofrecer el enlace "Historia clínica" que navega a `/pacientes/{pacienteId}/historia` del paciente del turno, sin incluir datos clínicos en la URL.

#### Scenario: Enlace en el detalle del turno
- **GIVEN** un turno del paciente 7
- **WHEN** el usuario abre el detalle del turno
- **THEN** se muestra el enlace "Historia clínica" con destino `/pacientes/7/historia`

#### Scenario: Navegación dentro de la SPA
- **WHEN** el usuario hace clic en "Historia clínica"
- **THEN** la aplicación navega a `/pacientes/7/historia` sin recargar la página
