# OdontoGestión — Base de Conocimiento

Base de conocimiento de OdontoGestión, aplicación web de gestión de turnos e historia clínica para un consultorio odontológico atendido por un único odontólogo. Trabajo académico (equipo de 6 personas), entrega el 2026-11-25. Generada de forma interactiva con el equipo; el informe de mercado es insumo (ver [11](11_contexto_de_mercado.md)).

## Índice de Archivos

| Archivo | Contenido |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor (odontólogo y paciente sin cuenta), alcance del MVP, fuera de alcance, métricas |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack, arquitectura general, integraciones, resumen de API REST (autenticada, pública, por token) |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Actores, modelo de acceso (odontólogo con sesión, paciente por token, visitante público), matriz de permisos, rutas públicas |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | Entidades (incl. `consultorio`), ERD (Mermaid), constraint de agenda, seed ficticio |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas RN-XX por dominio (autenticación, agenda, turnos, página pública, tokens, lista de espera, ausentismo, HC) |
| [06_funcionalidades.md](06_funcionalidades.md) | Historias de usuario por épica (MVP y etapas posteriores) |
| [07_flujos_principales.md](07_flujos_principales.md) | Flujos extremo a extremo con diagramas de secuencia |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Patrones, estructura de directorios, concurrencia, pruebas, seguridad (sesión + CSRF), demo local, variables de entorno |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | Decisiones DD-XX y supuestos SU-XX |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Inconsistencias y preguntas priorizadas; plazo (IN-01/PA-01) y anti-abuso (PA-02) resueltos el 2026-11-19 (DD-16, DD-14) |
| [11_contexto_de_mercado.md](11_contexto_de_mercado.md) | Resumen del informe de mercado, brechas, competidores, marco legal |
| [12_notificaciones.md](12_notificaciones.md) | Email en el MVP, WhatsApp posterior, puerto común |

## Quick Start para Desarrolladores

1. Entender el dominio → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. Entender los datos → [04](04_modelo_de_datos.md)
3. Entender las reglas → [05](05_reglas_de_negocio.md)
4. Entender la arquitectura → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. Implementar → [07](07_flujos_principales.md), [06](06_funcionalidades.md), [12](12_notificaciones.md)
6. Contexto de producto → [11](11_contexto_de_mercado.md)
7. Antes de codificar → [09](09_decisiones_y_supuestos.md), [10](10_preguntas_abiertas.md)

## Resumen Ejecutivo

OdontoGestión ataca el ausentismo y los huecos de agenda de un consultorio con un único odontólogo, que es el **único usuario con login** (email y contraseña, sesión de Spring Security con CSRF). Los pacientes no tienen cuenta: piden turno o se anotan en la lista de espera desde una **página pública** (nombre, DNI, email, teléfono) y se identifican con **enlaces de email de token único** (por turno o por aviso, con vencimiento y de un solo uso). La agenda del odontólogo no admite superposición (garantizada en PostgreSQL con una restricción de exclusión; el primero que reserva gana), una lista de espera avisa por email a todos los pacientes compatibles cuando se libera un hueco, y los recordatorios con enlaces de confirmación (disparados por `@Scheduled`, sin colas) reducen las ausencias. El modelo incluye la entidad `Consultorio` desde el día 1 para crecer a multi-consultorio. La historia clínica simple con odontograma FDI es "si llega" (stretch) para la entrega: el corte por valor (DD-16) hace obligatorios los 11 changes core y deja C-05 como opcional. El anti-abuso de la página pública (token por email, TTL 30 min, 5 solicitudes/h por IP, máx. 2 pendientes por DNI/email, honeypot) está confirmado (DD-14). El stack es TypeScript vanilla (Vite en desarrollo, estáticos servidos por Spring Boot), Spring Boot y PostgreSQL; la demo corre **en local** con SMTP real. Con ~7 días de plazo (corte por valor, DD-16) se prioriza la velocidad con un mínimo de mantenibilidad (capas limpias y `Consultorio`), y se trabaja con TDD estricto (JUnit 5 + Testcontainers). WhatsApp, pagos, ARCA, obras sociales, despliegue público y cumplimiento legal pleno quedan como etapas posteriores.

> Convención: **Suposición:** marca supuestos pendientes de validar.
