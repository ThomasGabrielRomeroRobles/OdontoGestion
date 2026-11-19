# database-schema-management Specification

## Purpose

Garantiza que el esquema de PostgreSQL evolucione solo mediante migraciones versionadas y reproducibles, que el mapeo de persistencia se valide contra ese esquema y que estén disponibles las extensiones que el dominio necesita (`btree_gist`, `citext`).

## Requirements

### Requirement: Esquema versionado por migraciones
El esquema de la base SHALL crearse y modificarse exclusivamente mediante migraciones versionadas que se aplican automáticamente y en orden al arrancar la aplicación.

#### Scenario: Base vacía migrada al arrancar
- **GIVEN** una base PostgreSQL vacía
- **WHEN** la aplicación arranca
- **THEN** el historial de migraciones registra la versión `1` como aplicada con éxito

#### Scenario: Arranque idempotente
- **GIVEN** una base en la que la versión `1` ya fue aplicada
- **WHEN** la aplicación vuelve a arrancar
- **THEN** no se reaplica ninguna migración y el arranque es exitoso

#### Scenario: Migración aplicada modificada
- **GIVEN** una base con la versión `1` aplicada y un script de la versión `1` cuyo contenido cambió
- **WHEN** la aplicación arranca
- **THEN** el arranque falla por discrepancia de checksum en lugar de alterar el esquema

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
- **GIVEN** la versión `1` fue aplicada
- **WHEN** se listan las tablas del esquema de la aplicación
- **THEN** solo existe la tabla de historial de migraciones

### Requirement: Mapeo validado contra el esquema
La capa de persistencia SHALL validar al arrancar que el mapeo coincide con el esquema migrado y MUST NOT crear ni alterar tablas por sí misma.

#### Scenario: Arranque con mapeo consistente
- **GIVEN** el esquema migrado y el mapeo de persistencia coinciden
- **WHEN** la aplicación arranca
- **THEN** el arranque es exitoso

#### Scenario: Estrategia de esquema no destructiva
- **WHEN** se inspecciona la configuración efectiva de la aplicación en cualquier perfil
- **THEN** la estrategia de generación de esquema es `validate`

#### Scenario: Pruebas de integración contra PostgreSQL real
- **WHEN** se ejecuta la suite de pruebas de integración
- **THEN** la base utilizada es PostgreSQL 15 o superior con `btree_gist` disponible, y no una base en memoria
