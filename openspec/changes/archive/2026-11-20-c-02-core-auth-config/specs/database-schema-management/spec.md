# Spec Delta

## MODIFIED Requirements

### Requirement: Extensiones de PostgreSQL requeridas
La migración inicial SHALL habilitar las extensiones `btree_gist` (para restricciones de exclusión con columnas de igualdad) y `citext` (para comparaciones de texto sin distinguir mayúsculas), sin crear tablas de dominio.

#### Scenario: btree_gist disponible
- **GIVEN** la versión `1` fue aplicada
- **WHEN** se consulta el catálogo de extensiones instaladas
- **THEN** `btree_gist` figura como instalada

#### Scenario: citext disponible y funcional
- **GIVEN** la versión `1` fue aplicada
- **WHEN** se compara `'Ana@Example.com'::citext = 'ana@example.com'::citext`
- **THEN** el resultado es verdadero

#### Scenario: Restricción de exclusión con igualdad soportada
- **GIVEN** la versión `1` fue aplicada
- **WHEN** se crea una tabla temporal con `EXCLUDE USING gist (grupo WITH =, rango WITH &&)` e inserta dos filas del mismo grupo con rangos superpuestos
- **THEN** la segunda inserción es rechazada por violación de exclusión

#### Scenario: Sin tablas de dominio en la versión inicial
- **GIVEN** un esquema vacío en el que se aplica únicamente la versión `1`
- **WHEN** se listan las tablas de ese esquema
- **THEN** solo existe la tabla de historial de migraciones

## ADDED Requirements

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
