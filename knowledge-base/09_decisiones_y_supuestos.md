# Decisiones y Supuestos

## Decisiones documentadas

### DD-01 — Un odontólogo, un consultorio, entidad `Consultorio` desde el día 1
**Decisión**: el MVP atiende a un único odontólogo con sus pacientes, pero el modelo incluye la entidad `consultorio` (una sola instancia) y de ella cuelgan odontólogo, pacientes, turnos, lista de espera y configuración. **No** es multi-tenant en el MVP.
**Contexto**: proyecto académico de 6 personas con entrega el 2026-11-25; se quiere poder crecer a multi-consultorio sin reescribir el modelo.
**Alternativas consideradas**: multi-tenant desde el inicio; ausencia total de la entidad (configuración global).
**Justificación**: costo mínimo hoy (una FK `consultorio_id`) y evita una migración estructural más adelante; es parte del mínimo de mantenibilidad (DD-10).
**Trade-offs aceptados**: pasar a varios odontólogos exigirá agregar `usuario_id` a turno y horarios y ajustar la restricción de exclusión (US-114).

### DD-02 — Stack impuesto
**Decisión**: frontend TypeScript vanilla (sin framework de UI); backend Spring Boot + REST + Gradle + Hibernate/JPA + PostgreSQL.
**Contexto**: restricción de la consigna académica.
**Alternativas consideradas**: no aplican (no negociable).
**Justificación**: requisito del trabajo.
**Trade-offs aceptados**: mayor esfuerzo de UI sin framework; hay que armar router, estado y componentes a mano (ver [08](08_arquitectura_propuesta.md)).

### DD-03 — Login del odontólogo con email + contraseña y sesión de Spring Security
**Decisión**: autenticación con **Spring Security basada en sesión de servidor** (cookie de sesión), con **protección CSRF**. Reemplaza la idea previa de JWT. Solo el odontólogo inicia sesión.
**Contexto**: un único usuario con login y frontend servido por el mismo backend (mismo origen).
**Alternativas consideradas**: JWT de vida corta con refresh; OAuth/SSO.
**Justificación**: la sesión es el mecanismo por defecto de Spring Security, más simple y rápido de hacer correctamente (sin almacenar tokens en el cliente, con revocación inmediata en logout); el mismo origen evita CORS.
**Trade-offs aceptados**: estado de sesión en memoria del servidor (suficiente con una instancia); hay que manejar CSRF en el frontend (cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN`).

### DD-04 — Migraciones con Flyway y SQL explícito
**Decisión**: el esquema se versiona con Flyway; Hibernate en modo `validate`.
**Contexto**: la restricción `EXCLUDE USING gist` no se expresa con anotaciones JPA.
**Alternativas consideradas**: `ddl-auto=update`; Liquibase.
**Justificación**: control del esquema, reproducibilidad entre máquinas del equipo y Testcontainers, soporte para constraints avanzadas.
**Trade-offs aceptados**: mantener SQL a mano; requiere la extensión `btree_gist` (incluida en PostgreSQL local; ver SU-07).

### DD-05 — Agenda del odontólogo; sin sillones
**Decisión**: cada turno ocupa la agenda del odontólogo; **no existen sillones** como recurso. La única regla de no superposición es la del odontólogo.
**Contexto**: corrección del equipo: hay un solo odontólogo; el sillón no agrega valor al MVP. Revierte el diferenciador "agenda por sillón" del informe de mercado ([11](11_contexto_de_mercado.md)).
**Alternativas consideradas**: agenda por profesional y sillón; sillón opcional.
**Justificación**: menos entidades, menos reglas y disponibilidad más simple, clave con 7 días de plazo.
**Trade-offs aceptados**: se pierde el diferenciador de sillón; si un consultorio con varios sillones lo requiere se reintroduce como recurso en una etapa posterior.

### DD-06 — Control de concurrencia en la base de datos
**Decisión**: la no superposición y el "primero gana" (solicitudes y lista de espera) se garantizan con una restricción de exclusión (`EXCLUDE USING gist` sobre `consultorio_id` y `tstzrange`, extensión `btree_gist` en PostgreSQL local) más `@Version` para el resto de las entidades.
**Contexto**: la lista de espera notifica a todos los compatibles a la vez, lo que genera aceptaciones simultáneas por diseño; la página pública permite solicitudes concurrentes.
**Alternativas consideradas**: bloqueo pesimista de filas; bloqueo a nivel de aplicación; cola serializada; notificar de a uno (descartada por el usuario).
**Justificación**: la base es el único punto de verdad; la garantía no depende del código de aplicación.
**Trade-offs aceptados**: hay que traducir violaciones de constraint a `409`; depende de una extensión de PostgreSQL.

### DD-07 — Notificaciones detrás de un puerto; email primero
**Decisión**: puerto `NotificationChannel` con adaptador de email en el MVP; WhatsApp Business API como integración posterior.
**Contexto**: el estándar del mercado es WhatsApp, pero implica costos (Meta cobra por plantilla) y aprobación de plantillas.
**Alternativas consideradas**: WhatsApp en el MVP; `wa.me` manual.
**Justificación**: el email es gratuito y suficiente para validar el flujo; el puerto evita retrabajo.
**Trade-offs aceptados**: menor tasa de apertura que WhatsApp. Ver [12](12_notificaciones.md).

### DD-08 — Pacientes sin cuenta; identificación por token de email y página pública
**Decisión**: los pacientes **no tienen cuenta ni login**. Se identifican mediante enlaces de email con un **token único por turno o por aviso**, con vencimiento, de un solo uso y almacenado con hash. Existe una **página pública** donde piden turno o se anotan en la lista de espera con nombre, DNI, email y teléfono.
**Contexto**: corrección del equipo (reemplaza "paciente siempre con cuenta").
**Alternativas consideradas**: cuenta de paciente con login; OTP por SMS/WhatsApp.
**Justificación**: elimina registro, login y recuperación de contraseña de pacientes; reduce fricción y alcance.
**Trade-offs aceptados**: la identidad del paciente se apoya en la posesión del email y en el DNI declarado (sin verificación fuerte); exige medidas anti-abuso (DD-14); sin historial consultable por el paciente.

### DD-09 — Historia clínica simple en MVP, append-only después
**Decisión**: MVP con datos de paciente, anamnesis básica, odontograma FDI con estado por pieza y notas de evolución (solo el odontólogo); la inalterabilidad se difiere.
**Contexto**: la Ley 26.529 (art. 13) exige integridad, inalterabilidad y control de modificaciones; el alcance académico no permite cumplimiento pleno.
**Alternativas consideradas**: HC append-only desde el inicio.
**Justificación**: foco en el núcleo de turnos; el modelo registra autor y fecha para facilitar la migración.
**Trade-offs aceptados**: el MVP no cumple por sí solo la normativa; se declara explícitamente (RN-GL-02).

### DD-10 — Prioridad: velocidad de entrega con mínimo de mantenibilidad
**Decisión**: con ~7 días de plazo se prioriza la **velocidad de entrega**; el **mínimo no negociable de mantenibilidad** es: capas limpias (api / application / domain / infrastructure), entidad `Consultorio` desde el día 1 y TDD en las reglas de agenda. Monolito en un solo despliegue.
**Contexto**: entrega 2026-11-25, equipo de 6; esta decisión reemplaza la prioridad previa de "mantenibilidad" a secas.
**Alternativas consideradas**: microservicios; arquitectura hexagonal estricta; mantenibilidad máxima con recorte de funciones.
**Justificación**: un monolito en capas es lo más rápido de construir sin hipotecar el futuro; recortar elementos accesorios (CORS, roles, hosting, colas) libera tiempo.
**Trade-offs aceptados**: algunas decisiones se toman por pragmatismo (entidades JPA en `domain`, rate limiting en memoria, sin E2E); acoplamiento por módulo gestionado por convención y tests.

### DD-11 — Demo local; despliegue público como etapa posterior
**Decisión**: la demo corre **en local**: PostgreSQL instalado en la máquina y una cuenta SMTP real (ej. Gmail con contraseña de aplicación), con credenciales solo por variables de entorno y nunca versionadas. **No se requiere despliegue público.** El despliegue en hosting queda como opción de una etapa posterior (US-115).
**Contexto**: resuelve la pregunta de hosting gratuito; evita cold starts, límites de planes y verificación de `btree_gist` en proveedores.
**Alternativas consideradas**: Render/Railway/Fly.io con Neon/Supabase; docker-compose.
**Justificación**: reduce riesgo y tiempo; el scheduler y el SMTP funcionan sin restricciones de hosting.
**Trade-offs aceptados**: la demo requiere una máquina con el sistema levantado; los enlaces de email apuntan a `APP_BASE_URL` local (solo funcionan en esa máquina o red).

### DD-12 — Frontend: TypeScript vanilla con Vite en desarrollo y estáticos en Spring Boot
**Decisión**: el frontend es TypeScript sin framework de UI. En desarrollo se usa el **servidor de Vite con proxy** a la API; en demo/producción el build se copia a `static/` y **Spring Boot lo sirve** como recursos estáticos (mismo origen).
**Contexto**: resuelve la pregunta de cómo se sirve el frontend.
**Alternativas consideradas**: despliegue separado con CORS.
**Justificación**: un solo proceso y un solo origen simplifican CORS, cookies de sesión y CSRF.
**Trade-offs aceptados**: el build del frontend debe integrarse al build de Gradle.

### DD-13 — Scheduler con `@Scheduled`, sin colas de mensajes
**Decisión**: recordatorios, vencimiento de pendientes/avisos/entradas y envío de notificaciones pendientes se ejecutan con `@Scheduled` dentro del proceso; el envío es idempotente. No se usan colas de mensajes.
**Contexto**: una sola instancia local; carga baja.
**Alternativas consideradas**: RabbitMQ/Kafka; Quartz; ShedLock.
**Justificación**: mínima infraestructura; la tabla `notificacion` actúa como outbox.
**Trade-offs aceptados**: si el proceso está caído no se envía nada hasta que vuelva (se retoman los pendientes); con varias instancias habría que agregar ShedLock.

### DD-14 — Anti-abuso de la página pública (Suposición)
**Decisión**: **Suposición** del equipo de documentación: las solicitudes públicas quedan `PENDIENTE_CONFIRMACION` hasta que el paciente confirma por un enlace con token en su email (con TTL de retención del hueco), se aplica limitación de tasa por IP y por DNI/email, campo señuelo y respuestas sin enumeración.
**Contexto**: una página sin login que retiene huecos y dispara emails es vulnerable a abuso (reservas falsas, spam de emails a terceros).
**Alternativas consideradas**: CAPTCHA; OTP; reserva inmediata sin confirmación.
**Justificación**: bajo costo de implementación y alto efecto; CAPTCHA/OTP quedan como mejora (US-113).
**Trade-offs aceptados**: un paso extra para el paciente; un atacante aún puede retener huecos durante el TTL. Registrado en [10](10_preguntas_abiertas.md).

### DD-15 — Pruebas con JUnit 5 y Testcontainers
**Decisión**: TDD estricto con **JUnit 5 + Testcontainers (PostgreSQL)** para las pruebas de integración y de la restricción de exclusión; Mockito para dobles; Vitest en el frontend.
**Contexto**: la restricción `btree_gist` y la concurrencia solo se validan contra PostgreSQL real.
**Alternativas consideradas**: H2 (no soporta exclusión por rango); base local compartida.
**Justificación**: pruebas reproducibles y fieles a producción.
**Trade-offs aceptados**: requiere Docker en las máquinas que corren los tests (ver SU-11).

## Supuestos inferidos

### SU-01 — Login del odontólogo con sesión de Spring Security
**Supuesto**: aceptado por el usuario (ver DD-03); reemplaza el supuesto previo de JWT.
**Origen**: corrección del equipo.
**Riesgo si es falso**: rediseño de autenticación.
**Cómo validar**: revisión con el equipo en el primer change de seguridad.

### SU-02 — Granularidad de 15 minutos
**Supuesto**: aceptado por el usuario; configurable.
**Origen**: discovery.
**Riesgo si es falso**: duraciones de prestación incompatibles.
**Cómo validar**: confirmar duraciones reales con un odontólogo de referencia.

### SU-03 — Recordatorio 24 h antes con enlaces confirmar/cancelar
**Supuesto**: aceptado por el usuario; enlaces con token único por turno.
**Origen**: discovery.
**Riesgo si es falso**: menor impacto en ausentismo.
**Cómo validar**: medir confirmaciones en la demo.

### SU-04 — Datos ficticios únicamente
**Supuesto**: no se manejan datos reales de pacientes.
**Origen**: discovery.
**Riesgo si es falso**: exposición legal bajo las Leyes 25.326 y 26.529.
**Cómo validar**: política del equipo; revisar el seed.

### SU-05 — Leyes 25.326, 26.529 y 27.706 como requisitos futuros
**Supuesto**: se documentan, no se implementan por completo.
**Origen**: discovery e informe de mercado ([11](11_contexto_de_mercado.md)).
**Riesgo si es falso**: un uso real requeriría asesoría legal y rediseño de HC, auditoría, custodia y consentimiento por la recolección de datos en la página pública.
**Cómo validar**: consultar a un asesor legal antes de cualquier uso real.

### SU-06 — Supuestos técnicos adicionales (a confirmar)
**Supuesto**: Java 21, Spring Boot 3.x, Flyway, Vite, Vitest; tolerancia de ausente de 15 min; cancelación del paciente hasta 2 h antes; aviso de hueco vigente 60 min; solicitud pública retenida 30 min; sesión de 30 min de inactividad; estados de pieza dental del MVP listados en [04](04_modelo_de_datos.md).
**Origen**: inferencia del equipo de documentación.
**Riesgo si es falso**: retrabajo menor.
**Cómo validar**: revisión en la primera reunión de planificación.

### SU-07 — Extensión `btree_gist` disponible en el PostgreSQL local
**Supuesto**: el PostgreSQL instalado (módulos contrib incluidos en el instalador oficial) permite `CREATE EXTENSION btree_gist`, que es una extensión "de confianza" desde PostgreSQL 13 (la puede crear el dueño de la base). La imagen de Testcontainers también la incluye.
**Origen**: conocimiento general de PostgreSQL; no verificado en las máquinas del equipo.
**Riesgo si es falso**: DD-06 requeriría alternativa (bloqueo por aplicación o trigger).
**Cómo validar**: ejecutar la migración V1 en una máquina de cada integrante antes del primer change de agenda.

### SU-08 — Plazo ajustado (~7 días)
**Supuesto**: el equipo entrega con el MVP recortado a lo estrictamente listado; la fecha del 2026-11-25 está a 7 días de la fecha de este documento (2026-11-18).
**Origen**: calendario del proyecto.
**Riesgo si es falso**: ninguno; si es verdadero, hay riesgo de no completar todas las épicas.
**Cómo validar**: priorizar en el roadmap; sugerido: épicas 1-4 primero, luego 5-7, luego 8.

### SU-09 — Medida anti-abuso de la página pública
**Supuesto**: confirmación por email con token + TTL de retención + limitación de tasa + campo señuelo es suficiente para el MVP (DD-14).
**Origen**: propuesta del equipo de documentación ante la ausencia de login.
**Riesgo si es falso**: reservas falsas o spam; habría que sumar CAPTCHA u OTP (US-113).
**Cómo validar**: pruebas de abuso básicas en la demo (ráfagas de solicitudes, DNI/email ajenos).

### SU-10 — Cuenta SMTP real con contraseña de aplicación
**Supuesto**: se usa una cuenta real (ej. Gmail con contraseña de aplicación) cuyo límite diario de envío (del orden de centenares de mensajes por día; verificar) alcanza para la demo, incluyendo el envío simultáneo a toda la lista de espera.
**Origen**: decisión de demo local (DD-11).
**Riesgo si es falso**: bloqueos o rechazo de envíos en la demo.
**Cómo validar**: prueba de envío con la cuenta elegida y registro de fallos en `notificacion`.

### SU-11 — Docker disponible para Testcontainers
**Supuesto**: las máquinas del equipo pueden ejecutar Docker (necesario solo para correr los tests de integración; el sistema en sí usa PostgreSQL instalado).
**Origen**: DD-15.
**Riesgo si es falso**: no se pueden correr los tests de integración localmente; habría que usar una base de pruebas dedicada.
**Cómo validar**: ejecutar un test con Testcontainers en cada máquina durante el scaffolding.
