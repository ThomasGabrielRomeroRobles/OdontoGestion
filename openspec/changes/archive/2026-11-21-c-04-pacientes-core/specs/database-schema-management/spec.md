# Spec Delta

## MODIFIED Requirements

### Requirement: Tablas de dominio con consultorio y tiempos UTC
Toda tabla de dominio de negocio SHALL tener clave primaria `BIGINT` generada, referencia obligatoria a `consultorio` (salvo la propia tabla `consultorio`) y columnas de auditoría `creado_en` y `actualizado_en` de tipo `timestamptz` no nulas.

#### Scenario: Versiones de C-02 aplicadas
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca
- **THEN** el historial de migraciones registra las versiones `1`, `2` y `3` aplicadas con éxito

#### Scenario: Usuario referencia al consultorio
- **WHEN** se inspecciona la tabla `usuario`
- **THEN** su columna `consultorio_id` es no nula y tiene clave foránea a `consultorio`

#### Scenario: Usuario sin consultorio rechazado
- **WHEN** se inserta directamente un usuario con `consultorio_id` nulo
- **THEN** la inserción es rechazada

#### Scenario: Timestamps en UTC
- **WHEN** se inspeccionan las columnas `creado_en` y `actualizado_en` de `consultorio` y `usuario`
- **THEN** son de tipo `timestamp with time zone` y no nulas

#### Scenario: Versión de C-04 aplicada
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca
- **THEN** el historial de migraciones registra la versión `5` aplicada con éxito

#### Scenario: Paciente referencia al consultorio
- **WHEN** se inspecciona la tabla `paciente`
- **THEN** su columna `consultorio_id` es no nula y tiene clave foránea a `consultorio`

#### Scenario: Timestamps de paciente en UTC
- **WHEN** se inspeccionan las columnas `creado_en` y `actualizado_en` de `paciente`
- **THEN** son de tipo `timestamp with time zone` y no nulas
