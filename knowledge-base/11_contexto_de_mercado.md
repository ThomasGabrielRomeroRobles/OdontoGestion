# Contexto de Mercado

Resumen del informe de relevamiento: [Sistemas odontológicos de turnos](../discovery/reports/Sistemas%20odontol%C3%B3gicos%20de%20turnos.md) (consultado el 2026-11-18).

**Advertencia de fiabilidad:** casi toda la evidencia son afirmaciones comerciales de los proveedores. Los puntajes miden lo que cada proveedor demuestra públicamente, no la calidad real del producto. Ninguna cifra de adopción está verificada por terceros.

## Panorama

El mercado argentino de software odontológico con turnos está fragmentado. Lo disputan siete productos locales (Bilog, DentalTec, Dentia, DentalSoft, DentalCore, Odonto.ar, OdontoGRAMA), una plataforma generalista chilena (AgendaPro) y un odontológico chileno con módulo argentino (Dentalink). En la matriz del informe lideran Dentia (3,90/5), DentalCore (3,80) y DentalSoft (3,50). Bilog (2,98) y DentalTec (2,85) tienen más escala declarada pero publican menos.

**Funciones estándar** (lo que el comprador da por descontado): agenda multiprofesional con vistas y arrastrar y soltar, odontograma interactivo, HC con adjuntos, presupuestos, caja diaria y recordatorio por WhatsApp con confirmación/cancelación que actualiza la agenda.

## Brechas y oportunidades clave

| Brecha del mercado | Cómo la aborda OdontoGestión |
|--------------------|------------------------------|
| **Lista de espera automática**: no evidenciada en ningún producto argentino o latinoamericano (sí en Open Dental, NexHealth, Curve) | Núcleo del MVP (RN-LE-01 a RN-LE-11), con aceptación por enlace de email sin login |
| **Sillón como recurso con control de superposición** en un odontológico nativo: no evidenciado | **Descartado por decisión del equipo** (un solo odontólogo, sin sillones); se mantiene la no superposición en la agenda del odontólogo (RN-AG-05; DD-05). Podría reintroducirse en etapas posteriores |
| **Ley 27.706**: ningún producto la menciona | Documentada como requisito futuro |
| **Garantías del art. 13 Ley 26.529** (inalterabilidad, copia en 48 h, custodia 10 años) | Etapa posterior (RN-HC-05, RN-HC-07) |
| **Circuito integrado** turno → seña → factura ARCA → obra social | Etapa posterior (Mercado Pago, ARCA, obras sociales) |
| **Transparencia** de precios y costo de WhatsApp | Valor de producto a futuro; el MVP académico no tiene precio |

## Marco regulatorio relevante

Inferencias del informe, a validar con asesoría legal:

- **Ley 26.529**: HC informatizada con integridad, autenticidad, inalterabilidad, perdurabilidad y recuperabilidad; copia al paciente en 48 h; custodia mínima de 10 años.
- **Ley 25.326**: datos de salud son sensibles; medidas de seguridad y deber de secreto; inscripción de la base ante la AAIP.
- **Ley 27.706**: Programa Federal de digitalización de HC; interoperabilidad (no hay plazos obligatorios hallados para consultorios privados).
- **ARCA**: RG 5866/2026 (comprobante al concluir la prestación o cobrar) y RG 5893/2026.
- **WhatsApp**: Meta cobra por plantilla; referencia de terceros alrededor de USD 0,03 por mensaje utility. Estimación propia del informe: unos USD 18 por mes por consultorio (2 recordatorios por turno, 300 turnos), a validar con la tarifa oficial.

## Requisitos derivados para el producto (etapas posteriores)

HC *append-only*, auditoría de accesos, autenticación individual por profesional (el MVP ya tiene login individual del odontólogo), copia de resguardo, exportación al paciente en 48 h, custodia de 10 años, recordatorios con datos mínimos y consentimiento por la recolección de datos en la página pública.

## Competidores a vigilar

| Competidor | Por qué | Qué verificar |
|------------|---------|----------------|
| Dentia | Mayor amenaza por precio (ARS 20.000-40.000/mes); IA en WhatsApp, ARCA, 25.326 con DPA | Si agenda con disponibilidad real; quién paga a Meta |
| DentalCore | Techo funcional y normativo (FHIR, log inmutable) | Precios contradictorios; tracción real |
| DentalSoft | Único con control de superposición declarado; precio transparente | Reserva online y Mercado Pago |
| Bilog | Incumbente de mayor escala | Recordatorio automático; ARCA |
| DentalTec | Obras sociales con validación online | Agenda por sillón; reserva del paciente |
| AgendaPro | Única con agenda por sillón (generalista) | Ficha clínica en planes AR |

## Referentes de UX

NexHealth (reserva en un clic, waitlist, recall), Dentally Portal (OTP sobre móvil verificado; referencia para endurecer la página pública, US-113), Open Dental (reserva separada en recall / nuevo / existente / ASAP).

## Etapas posteriores (hoja de ruta de mercado)

Seña con Mercado Pago, facturación ARCA, obras sociales, WhatsApp, recall, recepcionista IA, receta electrónica (ReNaPDiS), periodontograma, FHIR, liquidación multinivel. Ver [01](01_vision_y_objetivos.md) y la épica 9 de [06](06_funcionalidades.md).

## Vacíos del relevamiento

No hay cifra actualizada de odontólogos matriculados (la más citada son 53.011 de un censo antiguo), ni tasa de ausentismo con metodología, ni comisiones vigentes de Mercado Pago, ni nomenclador nacional vigente. Capterra devolvió 403.
