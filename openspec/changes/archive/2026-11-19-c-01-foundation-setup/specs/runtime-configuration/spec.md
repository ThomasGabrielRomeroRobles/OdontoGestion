# Spec Delta

## Purpose

Define cómo arranca y se configura OdontoGestión: perfiles de ejecución, parámetros externos por variables de entorno, manejo de secretos, reloj de referencia en UTC y servido del frontend desde el mismo origen que la API.

## ADDED Requirements

### Requirement: Configuración externa por variables de entorno
La aplicación SHALL leer su configuración de conexión, correo, URL base, zona horaria y parámetros operativos desde las variables de entorno documentadas en la KB (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `MAIL_*`, `APP_BASE_URL`, `APP_TIMEZONE`, etc.), con valores por defecto no sensibles cuando corresponda.

#### Scenario: Arranque con variables de entorno provistas
- **GIVEN** las variables `DB_URL`, `DB_USER` y `DB_PASSWORD` apuntan a una base PostgreSQL disponible
- **WHEN** la aplicación arranca con el perfil `dev`
- **THEN** el contexto de la aplicación se inicia correctamente y se conecta a esa base

#### Scenario: Valor por defecto de parámetro no sensible
- **GIVEN** la variable `APP_TIMEZONE` no está definida
- **WHEN** la aplicación arranca
- **THEN** la zona horaria de presentación configurada es `America/Argentina/Buenos_Aires`

#### Scenario: Parámetro sobrescrito por entorno
- **GIVEN** la variable `APP_BASE_URL` vale `http://localhost:9999`
- **WHEN** la aplicación arranca
- **THEN** la URL base configurada de la aplicación es `http://localhost:9999`

### Requirement: Secretos fuera del repositorio
Ningún archivo versionado SHALL contener valores reales de contraseñas o credenciales; el repositorio MUST incluir una plantilla `.env.example` con todas las variables sin valores sensibles y MUST ignorar `.env`.

#### Scenario: Plantilla de variables completa y sin secretos
- **WHEN** se inspecciona `.env.example`
- **THEN** contiene cada variable de la tabla de la KB y las variables sensibles (`DB_PASSWORD`, `MAIL_PASSWORD`, `BOOTSTRAP_ODONTOLOGO_PASSWORD`, `MAIL_USER`) están vacías

#### Scenario: Archivo de secretos local ignorado
- **WHEN** se crea un archivo `.env` en la raíz del repositorio
- **THEN** git lo ignora (`git check-ignore .env` lo reporta)

#### Scenario: Configuración versionada sin credenciales literales
- **WHEN** se inspeccionan los `application*.yml` versionados
- **THEN** las propiedades de contraseña solo referencian variables de entorno (`${...}`) y no contienen valores literales

### Requirement: Perfiles de ejecución dev y demo
La aplicación SHALL ofrecer los perfiles `dev` (desarrollo con frontend servido por Vite) y `demo` (frontend servido por la propia aplicación), seleccionables con `SPRING_PROFILES_ACTIVE`.

#### Scenario: Perfil dev activo
- **GIVEN** `SPRING_PROFILES_ACTIVE=dev`
- **WHEN** la aplicación arranca
- **THEN** el perfil activo reportado es `dev` y el arranque es exitoso

#### Scenario: Perfil demo activo
- **GIVEN** `SPRING_PROFILES_ACTIVE=demo`
- **WHEN** la aplicación arranca
- **THEN** el perfil activo reportado es `demo` y el arranque es exitoso

#### Scenario: Hibernate no altera el esquema en ningún perfil
- **WHEN** la aplicación arranca con cualquiera de los perfiles
- **THEN** la estrategia de esquema de Hibernate es `validate`

### Requirement: Reloj de referencia en UTC
La aplicación SHALL exponer una única fuente de tiempo en UTC que toda la lógica dependiente del tiempo use, reemplazable en pruebas por un reloj fijo.

#### Scenario: Reloj por defecto en UTC
- **WHEN** la aplicación arranca sin configuración de reloj especial
- **THEN** la fuente de tiempo provista opera en la zona `UTC`

#### Scenario: Reloj fijo en pruebas
- **GIVEN** una prueba reemplaza la fuente de tiempo por un reloj fijo en `2026-11-20T12:00:00Z`
- **WHEN** un componente consulta la hora actual
- **THEN** obtiene exactamente `2026-11-20T12:00:00Z`

#### Scenario: Instantes persistidos sin desplazamiento
- **GIVEN** la zona del sistema operativo es distinta de UTC
- **WHEN** se persiste y se lee un instante en una columna `timestamptz`
- **THEN** el instante leído es igual al instante escrito

### Requirement: Frontend empaquetado en el mismo origen
El build de la aplicación SHALL poder incluir el build del frontend como recursos estáticos del backend, de modo que en el perfil `demo` frontend y API compartan origen y no se requiera CORS. La exposición de esos recursos a través de la seguridad HTTP la define `C-02`.

#### Scenario: Artefacto con frontend incluido
- **GIVEN** se pide incluir el frontend en el build
- **WHEN** se construye el artefacto del backend
- **THEN** el artefacto contiene `static/index.html` y los recursos de `static/assets/` generados por el build del frontend

#### Scenario: Build del backend sin frontend
- **GIVEN** no se pide incluir el frontend en el build
- **WHEN** se ejecuta el build del backend
- **THEN** el build termina con éxito sin requerir Node instalado y el artefacto no contiene `static/index.html`

#### Scenario: Desarrollo con proxy en lugar de CORS
- **GIVEN** el servidor de desarrollo del frontend está configurado
- **WHEN** el frontend solicita una ruta bajo `/api`
- **THEN** la solicitud se reenvía a `http://localhost:8080` con el mismo path, sin configuración CORS en el backend
