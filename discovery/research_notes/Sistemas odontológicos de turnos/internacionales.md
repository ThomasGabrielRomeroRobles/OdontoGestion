# Software odontológico internacional (no LatAm): competidores e inspiración de producto/UX para un sistema de turnos argentino

> Estado: noviembre 2026. Todas las fuentes fueron consultadas el 2026-11-18.
> Convención: **[comprobado]** = dato verificable en documentación o página de precios oficial (hecho contractual o funcional publicado). **[afirmación comercial]** = claim de marketing del proveedor o de un caso de cliente, no verificado de forma independiente. **[tercero]** = dato de un agregador o competidor (Capterra, Pabau, SoftwarePundit, etc.), con fiabilidad menor. "No evidenciado" = no lo encontré en fuentes verificables durante esta investigación (no quiere decir que no exista).
> Alcance real: hay 8 productos cubiertos con distinta profundidad. Open Dental, Dentally, NexHealth, Weave y Gesden ONE tienen la mejor base documental. Curve, Dentrix Ascend y Doctolib tienen precio no publicado o datos contradictorios. tab32, Carestream CS R4 y Clinic Cloud no se investigaron (sin presupuesto de consultas).

## 1. Open Dental (EE. UU., código abierto): el modelo más transparente en precios y el booking más granular

### Takeaway
Open Dental es el único producto relevado con lista de precios completa y pública. Separa claramente el soporte mensual (USD 199/mes por sede en EE. UU., USD 89 en otros países) de los módulos de "eServices" vendidos à la carte (Web Sched, eConfirmations, texting por mensaje). Es una buena referencia para un modelo de precios modular y para separar funcionalidades de booking (recall, nuevo paciente, paciente existente, lista ASAP).

### Cited Findings
- Empresa/URL: Open Dental Software, opendental.com. Precios en USD; las tarifas en Canadá pueden variar mes a mes por tipo de cambio [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html) (consultado 2026-11-18)
- Único costo inicial: el primer mes de soporte. Contrato inicial de soporte de 12 meses y luego mes a mes. La baja se pide por teléfono. No hay trial gratuito, pero sí una garantía de devolución de 90 días sobre soporte si la clínica deja de usar el software [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Soporte mensual por sede: EE. UU. USD 199, que baja a USD 149 tras 12 meses consecutivos. Canadá USD 164 → 137. **Otros países USD 89** (soporte por email y teléfono). Países en desarrollo y escuelas dentales: gratis con soporte limitado. Cubre hasta 3 profesionales por sede; desde el 4.º, USD 20/mes por profesional (sin contar higienistas) [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Descuentos: por volumen, 4–9 sedes a USD 169/sede. Por prepago: 5 % (6 meses), 10 % (1 año) y 15 % (2–3 años) [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Open Dental Cloud (EE. UU.): se cobra aparte del soporte, mes a mes, y se puede volver a self-hosted en cualquier momento sin penalidad [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- eServices por sede/mes: Web Sched Recall USD 75. Web Sched New Patient y Existing Patient USD 75 cada uno. Web Sched ASAP USD 20 (gratis con Recall). eConfirmations USD 25 (USD 40 con texting por línea fija). eClipboard (formularios en tablet) USD 45 + OCR USD 20. Bundle de eServices USD 165, con texting integrado. ODMobile USD 35. Secure Email USD 30. Análisis de imágenes con IA (BetterDiagnostics) USD 149 [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Gratis con soporte: eReminders, mensajes generales, agradecimiento automático, portal del paciente, portal de pagos, Message-to-Pay y Web Forms [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Texting integrado: número asignado USD 5/mes y línea fija USD 20/mes. Cada SMS saliente cuesta USD 0,04 (EE. UU.). Los entrantes son gratis y los mensajes de más de 160 caracteres cuestan extra [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Capacitación: webinars gratis, online USD 80/h, presencial USD 4.325 el primer día por capacitador. Consultas personalizadas USD 110/h. La conversión de datos se cobra aparte [comprobado] — [Open Dental Fees](https://www.opendental.com/site/fees.html)

### Inferences
- La segmentación del booking online en Recall, Nuevo paciente, Paciente existente y ASAP (lista de espera para adelantar turnos) es un patrón de producto útil: cada flujo tiene reglas y valor comercial distintos.
- El cobro por SMS saliente muestra que en EE. UU. la mensajería se trata como costo variable. En Argentina, el equivalente sería el costo por conversación de la WhatsApp Business API, que conviene trasladar o incluir en el plan de forma explícita.
- El precio para "otros países" (USD 89) y la gratuidad en países en desarrollo hacen de Open Dental un competidor potencial de bajo costo en Argentina, aunque sin localización en español documentada en esta investigación.

### Gaps
- No pude leer el manual de Web Sched: las URLs /site/webschednewpat.html y /manual/webschednewpat.html devolvieron 404. Quedan sin evidenciar el cálculo de slots (operatorios, bloqueos), la verificación del paciente y el embebido web.
- Sin evidenciar: versión en español, presencia en LatAm, certificaciones (HIPAA, SOC 2), reseñas de G2/Capterra y número de clientes.

## 2. Dentally (Reino Unido, Henry Schein One): cloud con precio público por sillón y un portal de paciente integrado

### Takeaway
Dentally publica precios en GBP por cantidad de "surgeries" (gabinetes o sillones) en tres tiers (Starter, Essentials, Pro) con usuarios ilimitados. El booking online, los formularios digitales, el kiosk y el concierge forman el "Dentally Portal", que solo entra desde Essentials. Es buena referencia de UX: booking con OTP sobre móvil verificado y formularios de historia médica que se completan en casa y se sincronizan en tiempo real.

### Cited Findings
- Precios mensuales sin IVA, en GBP. La página dice "Prices below are for 3 surgeries" y muestra un slider de 1 a 9 surgeries. Starter va de £134 a £618, Essentials de £238 a £778 y Pro de £346 a £1.021 según el punto del slider. La correspondencia exacta entre precio y cantidad de sillones no está explícita en el texto extraído [comprobado, con ambigüedad] — [Dentally Pricing](https://dentally.com/en-gb/pricing) (consultado 2026-11-18)
- Todos los tiers incluyen usuarios y almacenamiento ilimitados, integración NHS y chat interno. Multi-site y Dentally Portal (formularios digitales, booking online, kiosk, concierge) recién desde Essentials. Integraciones "custom" y dominio de email propio solo en Pro [comprobado] — [Dentally Pricing](https://dentally.com/en-gb/pricing)
- Notas clínicas con IA: prueba limitada en Starter, 45 transcripciones por surgery/mes en Essentials y 90 en Pro. No disponibles en Irlanda del Norte ni en la República de Irlanda [comprobado] — [Dentally Pricing](https://dentally.com/en-gb/pricing)
- No se ofrece trial gratuito del software [comprobado: no figura] — [Dentally Pricing](https://dentally.com/en-gb/pricing)
- Soporte: chat en Starter, chat y teléfono en Essentials y Pro. Customer success manager en Essentials y dedicado en Pro [comprobado] — [Dentally Pricing](https://dentally.com/en-gb/pricing)
- Booking online: la clínica configura branding, horarios, duraciones por tipo de turno, tipos de tratamiento, "diary sessions" y profesionales. En multi-sede, el paciente elige la sede. Se accede por link directo, email/SMS, web o código QR [comprobado, descripción funcional oficial] — [Dentally Portal](https://dentally.com/en-gb/dentally-portal)
- Seguridad del booking: para reservar y firmar online hacen falta móvil verificado, contraseña y código de un solo uso (OTP) [comprobado] — [Dentally Portal](https://dentally.com/en-gb/dentally-portal)
- Formularios digitales, incluida la historia médica, completados desde casa y sincronizados en tiempo real. "Concierge" permite completarlos en tablet al llegar. "Kiosk" sirve para check-in en tablet, pero solo está optimizado para clínicas con una única sala de espera [comprobado] — [Dentally Portal](https://dentally.com/en-gb/dentally-portal)
- API: un catálogo de terceros afirma que la API de Dentally expone endpoints de disponibilidad y creación de citas, y una referencia de mayo de 2026 menciona un Developer Hub (developer.dentally.co) con autenticación "no documentada públicamente" [tercero, no verificado] — [Jentic: Dentally API](https://jentic.com/apis/dentally.co/dentally); [Supreme Dental Docs: Dentally Capability Reference](https://supreme-dental-docs.pages.dev/3.5.2DentallyCapabilityReference)

### Inferences
- Cobrar por sillón o gabinete en vez de por usuario (con usuarios ilimitados) es un modelo simple que alinea el precio con la capacidad de la clínica. Vale la pena evaluarlo para Argentina.
- Para un MVP argentino es preferible el flujo de identidad "móvil verificado + OTP" a obligar al paciente a crear una cuenta.
- Dejar el booking online fuera del tier más barato es una decisión de monetización: el booking se usa como palanca de upsell.

### Gaps
- Sin evidenciar: precios exactos por número de sillones, recordatorios automáticos (solo hay menciones genéricas), odontograma o perio, certificaciones (GDPR, ISO 27001), número de clientes y reseñas. No hay presencia en España ni LatAm documentada.

## 3. NexHealth (EE. UU.): capa de patient engagement y booking sobre el PMS existente, con API

### Takeaway
NexHealth no es un PMS: se monta sobre unos 80 sistemas (Dentrix, Eaglesoft, Open Dental, etc.) con un "Synchronizer" de lectura y escritura cada pocos minutos. Vende módulos (Scheduling, Forms, Communications, Payments, Verification, Insights) con precio solo por cotización. Es la mejor referencia de "booking omnicanal": widget web, Google Business, redes, QR, SMS y recall con booking embebido.

### Cited Findings
- Precio solo por cotización. Facturación mensual o anual por adelantado, sin penalidad de cancelación (aviso antes del ciclo en el plan mensual, 90 días de aviso en el anual). Ofrecen buy-out de contratos con otros proveedores, con exclusiones [comprobado] — [NexHealth Pricing](https://www.nexhealth.com/pricing) (consultado 2026-11-18)
- Módulos: Scheduling (booking online, recalls en un clic, waitlist, widget), Forms (formularios digitales, widget, app de iPad), Communications (mensajería, campañas, recordatorios, reseñas), Payments (cobro por SMS o email, widget, auto-posteo al ledger), Verification (elegibilidad de seguros) e Insights (producción vs. cobranza, uso de agenda) [comprobado] — [NexHealth Pricing](https://www.nexhealth.com/pricing)
- Todos los paquetes incluyen la integración con el EHR vía Synchronizer, cifrado en reposo "HIPAA-compliant" y soporte de onboarding [afirmación comercial para el cumplimiento] — [NexHealth Pricing](https://www.nexhealth.com/pricing)
- La página lista unos 80 sistemas integrables, pero otra sección dice que la API integra "15 health record systems in 1 sprint". Hay una contradicción no explicada en la propia web [comprobado: inconsistencia] — [NexHealth Pricing](https://www.nexhealth.com/pricing)
- El Synchronizer lee y escribe al PMS "every few minutes" y la web dice que eso reduce dobles reservas [afirmación comercial] — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)
- Flujo de booking: (1) la clínica configura tipos de turno, horarios y calendarios por profesional, con horarios por tipo de turno, duraciones por profesional y agendas rotativas; (2) el paciente accede desde el botón "Book Now" de la web, redes, Google Business, email, SMS o QR; (3) ve disponibilidad en tiempo real y reserva "en un clic" sin portal ni cuenta; (4) los mensajes de recall traen el link de reserva [comprobado, descripción funcional oficial] — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)
- El booking puede capturar datos de seguro, distinguir paciente nuevo de existente y preguntar la fecha del último turno. Hay "Waitlist" para cubrir huecos de último momento, sin detalle publicado [comprobado] — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)
- Limitación: no lee "Perfect Day scheduling" (las plantillas de bloques ideales del PMS) y el equipo las recrea manualmente en el onboarding [comprobado] — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)
- Casos de clientes: Bastida Dental Group dice ahorrar unos USD 2.000/mes en comisiones de booking tras dejar Zocdoc. Perfect Smile reporta más de 35 reservas mensuales desde Google y un 40 % más de reseñas positivas [afirmación comercial] — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)

### Inferences
- Patrones a emular: reservar sin cuenta, mostrar disponibilidad real en vivo, insertar el link de reserva dentro de los recordatorios de recall y usar la waitlist para cubrir cancelaciones.
- Que el agregador más grande tenga que recrear a mano el "Perfect Day" sugiere modelar la agenda por bloques o plantillas desde el diseño, para que el booking online respete esos bloques nativamente.

### Gaps
- Sin evidenciar: precios, cantidad total de clientes, reseñas de G2/Capterra, funciones de IA y presencia en español o LatAm.

## 4. Weave (EE. UU.): comunicación unificada (teléfono, texting, recordatorios, pagos) con precio "desde USD 199/mes"

### Takeaway
Weave es una plataforma de comunicación (telefonía VoIP, two-way texting, recordatorios, reseñas, pagos) más que un PMS. Publica un piso de precio ("from $199 per month") y cupos de SMS masivos por plan, pero no el precio de cada plan.

### Cited Findings
- Planes Pro, Elite ("Most Popular") y Ultimate, "from $199 per month". El precio de cada plan se pide por "Get Pricing" [comprobado: piso publicado] — [Weave Pricing](https://www.getweave.com/pricing/) (consultado 2026-11-18)
- Cupo mensual de SMS masivos: Pro 1.500, Elite 3.000, Ultimate 15.000. Ultimate incluye hasta 15 teléfonos, capacitación 1:1 y analítica premium [comprobado] — [Weave Pricing](https://www.getweave.com/pricing/)
- Funciones listadas (no pude verificar en qué plan entra cada una): two-way texting, recordatorios y confirmaciones, scheduling online, formularios digitales, pagos (Text to Pay, Buy Now Pay Over Time), reseñas, email marketing, recall, "Call Intelligence" con IA, analítica de llamadas, IVR, colas de llamada y fax [comprobado: listado] — [Weave Pricing](https://www.getweave.com/pricing/)
- Casos: Smith Dental reporta +28 % de pacientes nuevos y Beaches Dental un ahorro de USD 3.500/año. La web dice estar "Rated the best all-in-one platform" por G2 sin dar cifras [afirmación comercial] — [Weave Pricing](https://www.getweave.com/pricing/)

### Inferences
- El patrón clave de Weave es la bandeja unificada: texto, llamada y pago en el mismo hilo del paciente. En Argentina eso equivale a una bandeja de WhatsApp integrada con la ficha del paciente y el turno.
- Atar el plan a cupos de mensajes es otro modelo de precio a considerar en un contexto de WhatsApp pago.

### Gaps
- Sin evidenciar: precio de cada plan, condiciones de contrato, certificaciones, cantidad de clientes, reseñas y presencia en LatAm.

## 5. Curve Dental (EE. UU./Canadá): PMS 100 % cloud "all-in-one" sin precios públicos

### Takeaway
Curve vende un PMS cloud (Curve Hero) con imagenología nativa en la nube, perio, claims y elegibilidad de seguros sin clearinghouse, pagos y recordatorios (Curve GRO). Se vende solo por cotización. Los precios que publican terceros están desactualizados o se contradicen.

### Cited Findings
- La página de precios no muestra montos: ofrece "Schedule Demo" o "Get a Personalized Quote". Startups reciben "special pricing" en implementación y licencias, sin cifras [comprobado] — [Curve Dental Pricing](https://curvedental.com/pricing) (consultado 2026-11-18)
- Funciones listadas: recordatorios automáticos por texto y email (Curve GRO), formularios digitales, imagenología digital nativa en la nube, perio charting, pagos online, claims electrónicos ilimitados con ERA y elegibilidad sin clearinghouse, analítica, estados de cuenta, videos educativos, soporte 24/7/365 y menciones a "Ambient AI" y "Curve+ AI Suite" sin detalle [comprobado: listado] — [Curve Dental Pricing](https://curvedental.com/pricing)
- Seguridad: balanceo de carga dinámico, backup continuo, detección de intrusiones y recuperación ante desastres [afirmación comercial] — [Curve Dental Pricing](https://curvedental.com/pricing)
- Se presenta como "the most adopted cloud-based all-in-one software across North America" sin cifra que lo respalde [afirmación comercial] — [Curve Dental Pricing](https://curvedental.com/pricing)
- Hasta +30 % de aceptación de casos y +25 % de facturación. Testimonios con ahorro de USD 8.000/año y 90 % menos de costos de IT [afirmación comercial] — [Curve Dental Pricing](https://curvedental.com/pricing)
- Recordatorios personalizados de turnos y de recare por texto o email [comprobado, página oficial] — [Curve Dental Startup](https://www.curvedental.com/dental-startup)
- Self-scheduling 24/7 y "SmartFill" para cubrir cancelaciones [tercero] — [Toolradar: Curve Dental](https://toolradar.com/tools/curve-dental)
- Precios de terceros contradictorios: SaaSworthy dice USD 3,50 por hora hábil (datos de 11/2020, obsoletos). TechJockey lista cuatro planes (Hero Classic, Hero + Connex, Hero + Image, Super Hero) [tercero, desactualizado] — [SaaSworthy](https://www.saasworthy.com/product/curve-hero/pricing); [TechJockey](https://www.techjockey.com/detail/curve-dental)

### Inferences
- SmartFill (sugerir pacientes para cubrir un hueco que deja una cancelación) es el patrón de "gap filling" que más vale la pena emular, junto con la lista ASAP de Open Dental y la waitlist de NexHealth.

### Gaps
- Sin evidenciar: precio vigente, términos contractuales, cantidad de clientes, puntajes en Capterra/G2 (la ficha de [Capterra: Curve Dental Hero](https://www.capterra.com/p/98688/Curve-Dental-Hero/) existe pero no la abrí) y presencia en español o LatAm.

## 6. Dentrix Ascend (EE. UU., Henry Schein One): PMS cloud con precios muy contradictorios en fuentes de terceros

### Takeaway
No pude verificar el precio oficial porque la URL de precios de dentrixascend.com devolvió 404. Las fuentes de terceros van de USD 19/usuario/mes a USD 698/mes, con un setup reportado de USD 995. Hay que tomarlas con mucha cautela.

### Cited Findings
- Capterra muestra USD 19/usuario/mes en la ficha principal y USD 32/usuario/mes en la página de precios, con trial gratuito [tercero, inconsistente] — [Capterra: Dentrix Ascend](https://www.capterra.com/p/177828/Dentrix-Ascend/); [Capterra: Dentrix Ascend Pricing](https://www.capterra.com/p/177828/Dentrix-Ascend/pricing/)
- SoftwarePundit: USD 500/mes de base (software core + eServices), setup de USD 995 y USD 200/mes extra por imagenología integrada [tercero] — [SoftwarePundit](https://www.softwarepundit.com/node/464)
- TechRadar: setup de USD 995 y USD 500/mes hasta 10 usuarios, y señala que el sitio del proveedor no publica precios [tercero] — [TechRadar review](https://www.techradar.com/reviews/dentrix-ascend)
- Software Advice: "Starting at $698.00 per month" [tercero] — [Software Advice](https://www.softwareadvice.com/ca/dental-charting/dentrix-ascend-profile/)
- Reseñas en Capterra destacan el acceso multi-sede por ser cloud, pero también reportan lentitud y la necesidad de reloguearse o refrescar varias veces al día [tercero, opiniones de usuarios] — [Capterra: Dentrix Ascend](https://www.capterra.com/p/177828/Dentrix-Ascend/)

### Inferences
- La lentitud que reportan los usuarios de un PMS cloud grande indica que el rendimiento percibido de la agenda (carga rápida, sin refrescos) es un diferenciador real.

### Gaps
- Sin evidenciar con fuentes oficiales: precio, funciones de agenda (operatorios, bloques), booking online, certificaciones y número de clientes.

## 7. Gesden ONE / G5 (España, Infomed, Henry Schein One): el referente hispanohablante, con WhatsApp como módulo pago

### Takeaway
Gesden es el PMS dental en español con mayor base declarada (unas 14.000 clínicas en España según un competidor). La versión cloud, Gesden ONE, cuesta desde 75 €/mes sin IVA. WhatsApp para confirmaciones y recalls se cobra por mensaje. G5 es de escritorio, solo Windows, con licencia más mantenimiento.

### Cited Findings
- Gesden ONE + firma digital desde 75 €/mes sin IVA (uso, actualizaciones y mantenimiento incluidos). Con Pack Premium desde 104 €/mes. Pack Premium solo: 49 €/mes, sin alta ni permanencia. Son precios de la oferta "Plan PINO 2026" [tercero, Pabau es competidor; artículo actualizado al 19-10-2026] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- WhatsApp: 0,075 € por mensaje con un mínimo de 350 mensajes/mes (26,25 €). Mensajes de marketing a 0,03 € e internacionales a 0,045 €. Activación con Meta 69 € (única vez) [tercero] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- Licenciamiento de ONE por usuarios concurrentes: Easy (hasta 3), Profesional (hasta 6) y Multicentro (varias clínicas en una base de datos). G5 añade "Grandes clínicas" con usuarios ilimitados [tercero] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- Funciones: odontograma y periodontograma, facturación a mutuas, conexión con laboratorios (Dentbox), financiación de tratamientos (ONE PAY, hasta 4 entidades), almacenamiento cloud de radiografías y CBCT (IMAGE ONE), análisis de radiografías con IA (dentIA), cuadro de mandos y control horario [tercero] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- G5 es de escritorio (solo Windows 10 o superior) y necesita mantenimiento activo para recibir actualizaciones, incluida la adaptación a VeriFactu (G5 v5.54, ONE v1.585.0) [tercero] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- Unas 14.000 clínicas dentales en España usan Gesden. Infomed pertenece al grupo Henry Schein desde 2012 [tercero, cifra no verificada con fuente oficial] — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)
- Cita online: un directorio lista "Cita online" y "Portal paciente" como funciones de Gesden G5 [tercero] — [SoftwareDoIt: Infomed](https://www.softwaredoit.es/infomed/infomed.html)

### Inferences
- Es el benchmark más directo de idioma y modelo regulatorio (facturación fiscal, mutuas ≈ obras sociales) para Argentina, aunque no está documentado en LatAm.
- Que WhatsApp sea un add-on con cargo por mensaje en el líder español deja espacio a un producto argentino que integre WhatsApp como canal nativo de confirmación y reprogramación.

### Gaps
- Sin evidenciar con fuente oficial de Infomed: precios, flujo de cita online, certificaciones (RGPD, ENS), presencia en LatAm y reseñas.

## 8. Doctolib (Francia/Alemania): el estándar europeo de UX de reserva para el paciente, vía marketplace

### Takeaway
Doctolib combina una agenda online para el profesional con un directorio o marketplace por el que reservan los pacientes. Declara unos 520.000 profesionales y 90 millones de pacientes (mayo 2026) en Francia, Alemania, Italia y Países Bajos, y entró al Reino Unido en 2026. No publica tarifas oficiales para dentistas y las cifras de terceros son viejas o se contradicen.

### Cited Findings
- 520.000 profesionales usuarios y 90 millones de pacientes en Francia, Alemania, Italia y Países Bajos, según datos de mayo de 2026. Otra fuente de febrero de 2026 da más de 400.000 profesionales [tercero que reproduce cifras del proveedor; la diferencia no está explicada] — [Siècle Digital (07-05-2026)](https://siecledigital.fr/2026/05/07/doctolib-mise-sur-le-royaume-uni-pour-poursuivre-sa-croissance-avec-le-rachat-de-medicus/); [Maddyness UK (06-05-2026)](https://www.maddyness.com/uk/2026/05/06/doctolib-acquires-its-british-competitor-and-enters-the-uk-market/)
- Entrada al Reino Unido en 2026 con la compra de Medicus [tercero, prensa] — [Maddyness UK](https://www.maddyness.com/uk/2026/05/06/doctolib-acquires-its-british-competitor-and-enters-the-uk-market/)
- Precio por cotización. Cifras relevadas por terceros: unos 69 € TTC/mes (kinesiología), 129 €/mes por agenda online en artículos antiguos, 139 €/mes según Clubic y teleconsulta aparte a 79 €/mes. Nada está confirmado oficialmente para dentistas [tercero, desactualizado o contradictorio] — [Pabau FR: Doctolib tarif](https://pabau.com/fr/blog/doctolib-tarif/); [Clubic](https://www.clubic.com/telecharger-fiche441681-doctolib-pro.html); [Le Quotidien du Médecin](https://cardiologie.lequotidiendumedecin.fr/sante-societe/e-sante/le-geant-doctolib-lance-officiellement-son-service-de-teleconsultation-pour-79-euros-par-mois)
- El abonamiento incluye agenda online, reserva 24/7 y recordatorios por SMS y email. Para el paciente la reserva es gratuita [tercero] — [Pabau FR: Doctolib tarif](https://pabau.com/fr/blog/doctolib-tarif/)
- Para dentistas se mencionan gestión multi-profesional, agenda sincronizada, alojamiento certificado HDS e integración con el software Weclever Dental [tercero] — [Lonasanté: Doctolib Pro 2026](https://www.lonasante.com/?p=11708)

### Inferences
- El diferenciador de Doctolib es la red de pacientes (marketplace con búsqueda por especialidad, ubicación y disponibilidad), no la ficha clínica. En Argentina, el producto equivalente compite contra el WhatsApp de la clínica y no contra un directorio dominante.
- Patrones a emular: reserva sin fricción con disponibilidad visible primero, motivos de consulta que definen la duración del turno y recordatorios con reprogramación en un clic.

### Gaps
- Sin evidenciar: ficha dental específica (odontograma, presupuestos), tarifa oficial para dentistas y cantidad de dentistas usuarios (solo hay un estudio de 2021 con 149 dentistas). Doctolib no tiene versión en español ni opera en España o LatAm según las fuentes revisadas.

## 9. Patrones de UX transversales y presencia hispana (síntesis para el producto argentino)

### Takeaway
En todos los productos se repiten los mismos patrones:
- Reserva sin cuenta, con verificación del móvil por OTP.
- Disponibilidad en vivo según tipo de turno y profesional.
- Booking omnicanal: link, widget, QR, Google y redes.
- Link de reserva incrustado en los recall.
- Lista de espera o ASAP para cubrir huecos.
- Formularios clínicos completados antes de la visita.
- Bandeja unificada de mensajería.

Solo Gesden tiene presencia hispanohablante (en España). Ninguno de los productos estadounidenses o británicos mostró localización para LatAm en las fuentes revisadas.

### Cited Findings
- Identidad del paciente con móvil verificado + OTP y acceso por link, email/SMS, web o QR — [Dentally Portal](https://dentally.com/en-gb/dentally-portal)
- Reserva "en un clic" sin portal, recall con booking embebido, waitlist, y captura de seguro y de paciente nuevo o existente — [NexHealth Online Booking](https://www.nexhealth.com/features/online-booking)
- Booking separado en recall, nuevo paciente, existente y ASAP como productos distintos — [Open Dental Fees](https://www.opendental.com/site/fees.html)
- Gap filling (SmartFill) [tercero] — [Toolradar: Curve Dental](https://toolradar.com/tools/curve-dental)
- Bandeja unificada de texto, voz y pagos con cupos de SMS por plan — [Weave Pricing](https://www.getweave.com/pricing/)
- IA aplicada: notas clínicas por transcripción ([Dentally Pricing](https://dentally.com/en-gb/pricing)), análisis de radiografías ([Open Dental Fees](https://www.opendental.com/site/fees.html); dentIA en [Pabau: Infomed](https://pabau.com/es/blog/precios-de-infomed/)) y análisis de llamadas ([Weave Pricing](https://www.getweave.com/pricing/))
- WhatsApp como canal de confirmación y recall cobrado por mensaje en España — [Pabau: Precios de Infomed 2026](https://pabau.com/es/blog/precios-de-infomed/)

### Inferences
- Para un MVP argentino, los patrones de mayor valor y menor costo son:
  1. Reserva por link o QR sin cuenta, con OTP por WhatsApp o SMS.
  2. Agenda basada en tipos de turno con duración y profesional o sillón asignado, con bloques que el booking online respete nativamente (para evitar el problema del "Perfect Day" de NexHealth).
  3. Confirmar, cancelar o reprogramar desde el recordatorio.
  4. Lista de espera que avise automáticamente cuando se libera un hueco.
  5. Formulario de anamnesis previo a la visita.
- La IA (notas, radiografías) es hoy un diferenciador de los tiers altos. No parece imprescindible para entrar al mercado argentino.

### Gaps
- No investigados por falta de presupuesto de consultas: tab32, Carestream CS R4 y Clinic Cloud (España).
- Sin datos verificados en esta investigación: puntajes y cantidad de reseñas en G2/Capterra para casi todos los productos, certificaciones de seguridad (SOC 2, ISO 27001, HIPAA) verificadas con fuente oficial, y la mecánica de detalle de la agenda (operatorios, overbooking, control de solapamientos), que requiere los manuales o centros de ayuda de cada producto.
