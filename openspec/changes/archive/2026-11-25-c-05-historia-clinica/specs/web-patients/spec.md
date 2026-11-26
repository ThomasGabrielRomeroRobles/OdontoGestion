## REMOVED Requirements

### Requirement: Marcador de historia clínica
**Reason**: C-05 entrega la historia clínica; la tarjeta "próximamente" deja de tener sentido.
**Migration**: la ficha enlaza a la pantalla de historia clínica (requisito "Acceso a la historia clínica").

## ADDED Requirements

### Requirement: Acceso a la historia clínica
La ficha del paciente SHALL incluir una sección "Historia clínica" con un enlace "Ver historia clínica" a `/pacientes/{id}/historia`, la pantalla de historia clínica de C-05, y MUST NOT mostrar el texto "próximamente".

#### Scenario: Enlace desde la ficha
- **WHEN** se renderiza la ficha del paciente con id 1
- **THEN** la sección de historia clínica muestra el enlace "Ver historia clínica" a `/pacientes/1/historia`
- **AND** no aparece el texto "próximamente"
