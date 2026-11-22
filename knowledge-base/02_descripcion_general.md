# Descripción General

## Stack tecnológico

El stack base es **impuesto y no negociable** por la consigna del trabajo académico; los detalles marcados como Suposición son decisiones del equipo.

| Capa | Tecnología | Versión mínima |
|------|-----------|----------------|
| Frontend | TypeScript "vanilla" (sin framework de UI), HTML + CSS | **Suposición:** TypeScript 5.x |
| Bundler / dev server | Vite (solo en desarrollo, con proxy a la API) | **Suposición:** Vite 5.x |
| Servido del frontend | En producción/demo: archivos estáticos servidos por Spring Boot (build de Vite copiado a `static/`) | Ver DD-12 |
| Backend | Spring Boot, API REST | **Suposición:** Spring Boot 3.x, Java 21 |
| Build | Gradle | **Suposición:** Gradle 8.x |
| Persistencia | Hibernate / JPA | Provisto por Spring Data JPA |
| Base de datos | PostgreSQL **instalado en la máquina** (local) | **Suposición:** 15+ (extensión `btree_gist`) |
| Migraciones | **Suposición:** Flyway | Ver DD-04 |
| Seguridad | Spring Security, **autenticación por sesión** (cookie de sesión del servidor) + protección CSRF | Ver DD-03 |
| Email | Spring Mail (SMTP) detrás de un puerto; cuenta SMTP real (ej. Gmail con contraseña de aplicación) | Ver [12](12_notificaciones.md) |
| Scheduler | Spring `@Scheduled` en el mismo proceso, sin colas de mensajes | Ver DD-13 |
| Tests backend | JUnit 5, Mockito, **Testcontainers (PostgreSQL)** para TDD estricto | Ver DD-15 |
| Tests frontend | **Suposición:** Vitest | Compatible con Vite |
| Hosting | **Ninguno en el MVP:** demo local. Despliegue público como opción de etapa posterior | Ver DD-11 |

Prioridad de calidad: **velocidad de entrega** (~7 días) con un **mínimo de mantenibilidad** (capas claras, entidad `Consultorio`, tests). Se trabaja con **TDD estricto**.

## Arquitectura general

Aplicación web para **un consultorio con un único odontólogo** (no multi-tenant, aunque el modelo incluye la entidad `Consultorio`). Un frontend en TypeScript consume una API REST; el backend es un monolito modular en capas.

```
 Navegador (TS vanilla)
   |  HTTP / JSON (REST + cookie de sesión + CSRF)   [dev: Vite proxy -> :8080]
   v
 +---------------------------------------------+
 |  Spring Boot (sirve también el frontend)    |
 |  api (controllers, DTOs)                    |
 |  application (casos de uso / servicios)     |
 |  domain (entidades, reglas, puertos)        |
 |  infrastructure (JPA, email, @Scheduled)    |
 +---------------------------------------------+
        |                         |
        v                         v
   PostgreSQL (local)      SMTP real (Gmail/app password)  --> [futuro] WhatsApp Business API
```

Decisiones de alto nivel:

- **Monolito en capas** por tamaño del equipo, plazo y alcance académico (ver [08](08_arquitectura_propuesta.md)).
- La **integridad de la agenda** se garantiza en la base de datos (restricción de exclusión con `btree_gist`), no solo en código.
- Las **notificaciones** pasan por un puerto (`NotificationChannel`), con email como único adaptador del MVP.
- Tareas programadas (recordatorios, vencimientos, envío de pendientes) con `@Scheduled` dentro del mismo proceso; **sin colas de mensajes**.
- Mismo origen en demo y en desarrollo (Vite hace proxy), por lo que no hace falta CORS.

## Integraciones externas

| Servicio | Propósito | Tipo | Etapa |
|----------|-----------|------|-------|
| Servidor SMTP real (ej. Gmail con contraseña de aplicación; credenciales solo por variables de entorno) | Confirmaciones de solicitud, recordatorios, avisos de hueco, ausentes | SMTP | MVP |
| WhatsApp Business API (Meta) | Recordatorios y avisos de hueco | REST / webhook | Posterior |
| Mercado Pago | Señas y cobros | REST / webhook | Posterior |
| ARCA (WSAA + WSFEv1) | Facturación electrónica | SOAP / web service | Posterior |
| Obras sociales / círculos | Validación y liquidación | A definir por entidad | Posterior |
| Red Ley 27.706 (FHIR) | Interoperabilidad de historia clínica | FHIR | Posterior |

## API REST (resumen)

Prefijo `/api/v1`. Tres zonas de acceso: **autenticada** (sesión del odontólogo), **pública** (sin login, con limitación de tasa) y **por token** (enlaces de email). Detalle de contratos a definir en los changes de implementación.

| Zona | Recurso | Endpoints principales |
|------|---------|-----------------------|
| Autenticada | `auth` | `POST /auth/login`, `POST /auth/logout`, `GET /auth/sesion` |
| Autenticada | `turnos` | `GET /turnos` (filtros), `POST /turnos`, `PUT /turnos/{id}/mover`, `POST /turnos/{id}/cancelar`, `POST /turnos/{id}/ausente`, `POST /turnos/{id}/atendido`, `GET /turnos/hoy` (mover es una acción explícita como `cancelar` y `atendido`; C-07 D1) |
| Autenticada | `agenda` | `GET /agenda/disponibilidad?prestacionId=&desde=&hasta=` (huecos libres), `GET /agenda?desde=&hasta=` (turnos de un rango), `GET /bloqueos/{id}/turnos-afectados` (C-07 D1) |
| Autenticada | `lista-espera` | `POST /lista-espera`, `GET /lista-espera`, `DELETE /lista-espera/{id}` |
| Autenticada | `pacientes` | CRUD de pacientes, historial de ausencias |
| Autenticada | `historias-clinicas` | `GET /pacientes/{id}/historia`, `PUT /pacientes/{id}/anamnesis`, `PUT /pacientes/{id}/odontograma/piezas/{fdi}`, `POST /pacientes/{id}/evoluciones` |
| Autenticada | `prestaciones`, `horarios`, `bloqueos`, `consultorio`, `notificaciones` | Configuración y registro de envíos |
| Pública | `publico` | `GET /publico/disponibilidad`, `POST /publico/solicitudes-turno`, `POST /publico/lista-espera` |
| Por token | `acciones` | `GET /acciones/{token}` (muestra, no consume), `POST /acciones/{token}` (confirmar solicitud, confirmar o cancelar turno) |
| Por token | `avisos` | `GET /avisos/{token}` (muestra el hueco), `POST /avisos/{token}/aceptar` (acepta el hueco ofrecido) |
