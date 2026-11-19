# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01 — Plazo de entrega vs. alcance del MVP
**Alcance dice**: MVP con 8 épicas (acceso, configuración, agenda, página pública, lista de espera, recordatorios, ausentismo, HC con odontograma).
**Calendario dice**: entrega el 2026-11-25, siete días después de la fecha de esta documentación (2026-11-18).
**Impacto**: riesgo alto de no completar el MVP completo.
**Resolución propuesta**: confirmar con el equipo el corte real y priorizar por valor: épicas 1-4 (núcleo), luego 5-7, luego 8. Documentar lo no entregado como "etapa posterior". Las correcciones de alcance (un solo odontólogo, sin sillones, sin cuentas de paciente) ya reducen el trabajo.

### IN-02 — Historia clínica del MVP vs. exigencias de la Ley 26.529
**El MVP**: HC editable con autor y fecha.
**El marco legal ([11](11_contexto_de_mercado.md))**: exige inalterabilidad y control de modificaciones.
**Impacto**: el MVP no sería apto para uso real con pacientes.
**Resolución propuesta**: mantener datos ficticios (RN-GL-01) y declarar la brecha; planificar HC *append-only* como etapa posterior.

### IN-03 — Identidad del paciente sin login vs. datos personales sensibles
**La página pública**: pide DNI, email y teléfono a un visitante anónimo, sin verificación fuerte de identidad.
**El marco legal ([11](11_contexto_de_mercado.md))**: la Ley 25.326 exige consentimiento y medidas de seguridad sobre datos personales.
**Impacto**: cualquiera puede ingresar el DNI o email de un tercero; el consentimiento no está modelado.
**Resolución propuesta**: mitigar con confirmación por email y no sobrescribir datos de pacientes existentes (RN-PU-04); usar solo datos ficticios en el MVP (RN-GL-01) y tratar consentimiento y verificación fuerte como requisitos futuros.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Bloquea | Decisor |
|----|-----------|----------|---------|---------|
| PA-01 | Alta | ¿Se confirma el corte de alcance real dado el plazo del 2026-11-25 (7 días)? | Planificación | Equipo / docente |
| PA-02 | Alta | **Anti-abuso de la página pública (Suposición adoptada):** confirmación por email con token + TTL de 30 min + limitación de tasa (5/h por IP, 2 pendientes por DNI/email) + campo señuelo. ¿Se confirma o se exige CAPTCHA/OTP en el MVP? | Épica 4 (RN-PU-02, RN-PU-03, RN-PU-06) | Equipo |
| PA-03 | Media | Si un DNI ya existe con otro email, ¿a qué email se envían los enlaces y cómo se actualiza el contacto? (Suposición: se usa el email registrado y no se sobrescribe; ver RN-PU-04) | Página pública, RN-PU-04 | Equipo |
| PA-04 | Media | ¿Cómo recupera su contraseña el odontólogo (único usuario, sin administrador)? Opciones: enlace por email, o reinicio manual por seed/variable de entorno. | Épica 1 (US-116) | Equipo |
| PA-05 | Media | Valores por defecto de reglas: tolerancia de ausente (15 min), límite de cancelación (2 h), vencimiento de aviso (60 min), anticipación mínima (2 h), retención de solicitud pública (30 min). ¿Se confirman? | Reglas de turnos, página pública y lista de espera | Product Owner (docente/equipo) |
| PA-06 | Media | ¿Los turnos `ATENDIDO` y `AUSENTE` deben seguir ocupando el rango en la restricción de exclusión? | Modelo de turno | Equipo técnico |
| PA-07 | Media | ¿Todas las máquinas del equipo pueden ejecutar Docker para Testcontainers y tienen `btree_gist` disponible en su PostgreSQL local? (SU-07, SU-11) | Scaffolding, TDD | Equipo técnico |
| PA-08 | Media | ¿Existe alguna política por reincidencia de ausencias (aviso, seña, bloqueo), por ejemplo restringir solicitudes públicas de un DNI con muchas ausencias? | Etapa posterior (US-112) | Equipo |
| PA-09 | Baja | Estados de pieza dental y alcance del odontograma (por pieza vs. por cara). ¿Se valida con un odontólogo? | Épica 8 | Equipo / odontólogo asesor |
| PA-10 | Baja | ¿Hay línea base de ausentismo para medir el impacto? (no existe tasa sólida en Argentina) | Métricas | Equipo |
| PA-11 | Baja | ¿Qué costo y plan de WhatsApp Business API se usaría luego (validar rate card oficial de Meta)? | Etapa posterior | Equipo |
| PA-12 | Baja | Validación legal de requisitos futuros (Leyes 25.326, 26.529, 27.706), incluido el consentimiento por la recolección de datos en la página pública. | Uso real | Docente / asesor legal |
| PA-13 | Baja | ¿Cuánto tiempo se conservan las solicitudes y entradas de lista de espera nunca confirmadas y los pacientes creados por ellas (depuración)? | Datos personales, modelo | Equipo |

## Cuestiones resueltas (referencia)

Resueltas por las correcciones del equipo y ya incorporadas a la KB: hosting y despliegue (demo local, DD-11), proveedor SMTP (cuenta real por variables de entorno, SU-10), scheduler en hosting gratuito (`@Scheduled` local, DD-13), cómo se sirve el frontend (DD-12), acceso del administrador a datos clínicos y odontólogo-administrador (no hay rol administrador, DD-01), vinculación ficha-cuenta (los pacientes no tienen cuenta, DD-08) y recuperación de contraseña de pacientes (no hay login de pacientes).
