# Visión y Objetivos

## Propósito del sistema

OdontoGestión es una aplicación web para la gestión de turnos y la historia clínica de **un consultorio odontológico atendido por un único odontólogo** con sus pacientes. Su foco es reducir el **ausentismo** de pacientes y los **huecos** en la agenda, y recuperar los turnos cancelados mediante una lista de espera que notifica por email a los pacientes compatibles.

El **odontólogo es el único usuario que inicia sesión**: él configura el consultorio, administra la agenda y la lista de espera y lleva la historia clínica. Los **pacientes no tienen cuenta ni login**: piden turno o se anotan en la lista de espera desde una **página pública**, y confirman o cancelan mediante **enlaces con token único** enviados por email.

El proyecto es un **trabajo académico** desarrollado por un equipo de 6 personas, con fecha de entrega el **2026-11-25** (unos 7 días de desarrollo). La base de conocimiento documenta el MVP ("núcleo turnos") y las etapas posteriores, de modo que el alcance del MVP quede claramente delimitado y el resto sea una hoja de ruta.

**Problema central:** el consultorio pierde tiempo clínico por pacientes que no se presentan (ausentismo) y por huecos que quedan sin cubrir tras cancelaciones. Hoy la agenda no ofrece esos huecos a quienes querrían un turno antes.

**Prioridad de calidad:** con el plazo disponible manda la **velocidad de entrega**, con un **mínimo no negociable de mantenibilidad**: capas limpias y la entidad `Consultorio` desde el día 1 (ver DD-10 en [09](09_decisiones_y_supuestos.md)).

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| Paciente (sin cuenta) | Pedir turno o anotarse en lista de espera sin llamar por teléfono | Confirmar o cancelar desde el email; aceptar un hueco ofrecido; recibir recordatorios claros |
| Odontólogo (único usuario con login) | Gestionar su agenda sin superposiciones y registrar la atención | Configurar prestaciones, horarios y bloqueos; administrar la lista de espera; marcar ausentes; completar anamnesis, odontograma y evolución |
| Equipo de desarrollo | Entregar un MVP mantenible y testeado en 7 días | Dejar la base (entidad `Consultorio`, puertos) para etapas posteriores (pagos, WhatsApp, multi-consultorio) |

## Alcance v1.0 (MVP "núcleo turnos")

- Agenda del odontólogo con **detección de superposición** garantizada en la base de datos (un solo recurso: el odontólogo).
- Tipos de turno (prestaciones) con duración; granularidad de 15 minutos.
- **Login del odontólogo** con email y contraseña (sesión de Spring Security, protección CSRF).
- **Página pública sin login**: el paciente pide un turno entre los horarios libres o se anota en la lista de espera indicando nombre, DNI, email y teléfono. Medidas anti-abuso (**Suposición**): la solicitud queda pendiente hasta que el paciente la confirma por un enlace enviado a su email, y hay limitación de tasa.
- **Lista de espera**: al liberarse un hueco se notifica por email a **todos** los pacientes compatibles con un enlace de un solo uso y con vencimiento; el primero que acepta se queda con el turno.
- **Recordatorios por email** 24 horas antes, con enlaces para confirmar o cancelar (token único por turno).
- **Gestión de ausentismo**: marcar turno como ausente, registrarlo en el historial del paciente y enviar un email invitando a reprogramar.
- **Historia clínica** básica (solo el odontólogo): datos del paciente, anamnesis básica, odontograma FDI con estado por pieza y notas de evolución.
- Entidad **`Consultorio`** desde el día 1 (instancia única en el MVP).

## Fuera de alcance del MVP (etapas posteriores)

- Cuentas, login o portal de pacientes; roles de Recepcionista y Administrador.
- Sillones como recurso de agenda; varios odontólogos.
- Multi-consultorio y multi-tenant (el modelo queda preparado con `Consultorio`, pero el MVP opera con un solo consultorio).
- Despliegue público en hosting (la demo corre en local; ver DD-11).
- Señas y cobros con Mercado Pago.
- Facturación electrónica ARCA y caja.
- Obras sociales (validación, liquidación, aranceles).
- Notificaciones por WhatsApp Business API (se diseña el puerto, no se implementa el canal).
- Campañas de recall, recepcionista con IA, interoperabilidad FHIR.
- Plan de tratamiento, presupuestos, adjuntos de radiografías, historia clínica *append-only* con auditoría completa, periodontograma, receta electrónica.
- Cumplimiento legal pleno de las Leyes 25.326, 26.529 y 27.706 (se documentan como requisitos futuros; ver [09](09_decisiones_y_supuestos.md)).

## Métricas de éxito

**Suposición:** al ser un trabajo académico, el éxito se mide primero por la entrega funcional; las métricas de negocio son indicativas y se evaluarían con datos ficticios o en una eventual prueba piloto.

| Métrica | Objetivo orientativo |
|---------|----------------------|
| Turnos superpuestos en la agenda del odontólogo | 0 por diseño (garantizado por restricción en base de datos) |
| Huecos liberados que se ofrecen a la lista de espera | 100 % de las cancelaciones con pacientes compatibles |
| Recordatorios enviados 24 h antes | 100 % de turnos confirmables |
| Solicitudes públicas abusivas que ocupan hueco sin confirmar | Acotadas por TTL de confirmación y limitación de tasa |
| Tasa de ausentismo | Reducción respecto de la línea base del consultorio (a medir; ver [10](10_preguntas_abiertas.md)) |
| Cobertura de pruebas en reglas de negocio de agenda | Alta, siguiendo TDD estricto |

> Nota de mercado: no existe una tasa de ausentismo odontológico argentina con metodología sólida; el "hasta 30 %" circulante proviene de una nota comercial. Ver [11](11_contexto_de_mercado.md).
