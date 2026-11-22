/**
 * Validaciones de cliente de la configuración. Solo evitan envíos inútiles:
 * la autoridad es siempre el servidor (`422`).
 */
export type Validacion<T> = { readonly ok: true; readonly value: T } | { readonly ok: false; readonly error: string };

const ENTERO = /^-?\d+$/;
const HORA = /^([01]\d|2[0-3]):([0-5]\d)$/;

function parsearEntero(valor: string): number | null {
  const limpio = valor.trim();
  return ENTERO.test(limpio) ? Number.parseInt(limpio, 10) : null;
}

/** Duración de prestación: entero positivo y múltiplo de la granularidad (si se conoce). */
export function validarDuracion(valor: string, granularidadMin: number | null): Validacion<number> {
  if (valor.trim() === '') return { ok: false, error: 'La duración es obligatoria.' };
  const minutos = parsearEntero(valor);
  if (minutos === null) return { ok: false, error: 'La duración debe ser un número entero de minutos.' };
  if (minutos <= 0) return { ok: false, error: 'La duración debe ser mayor que cero.' };
  if (granularidadMin !== null && granularidadMin > 0 && minutos % granularidadMin !== 0) {
    return { ok: false, error: `La duración debe ser múltiplo de ${granularidadMin} minutos.` };
  }
  return { ok: true, value: minutos };
}

export function validarNombrePrestacion(valor: string): Validacion<string> {
  const nombre = valor.trim();
  return nombre === '' ? { ok: false, error: 'El nombre es obligatorio.' } : { ok: true, value: nombre };
}

/** Parámetro numérico del consultorio: entero mayor que cero. */
export function validarParametro(valor: string): Validacion<number> {
  const n = parsearEntero(valor);
  return n === null || n <= 0 ? { ok: false, error: 'Debe ser un número entero mayor que cero.' } : { ok: true, value: n };
}

export function validarPasswordNueva(nueva: string, repetida: string): Validacion<string> {
  if (nueva === '') return { ok: false, error: 'La nueva contraseña es obligatoria.' };
  if (nueva !== repetida) return { ok: false, error: 'Las contraseñas no coinciden.' };
  return { ok: true, value: nueva };
}

/** Minutos desde medianoche de una hora `HH:mm`, o `null` si no es válida. */
export function minutosDelDia(hora: string): number | null {
  const match = HORA.exec(hora);
  if (match === null) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

export type ValidacionRango =
  | { readonly ok: true }
  | { readonly ok: false; readonly campo: 'horaDesde' | 'horaHasta'; readonly error: string };

export function validarRangoHorario(horaDesde: string, horaHasta: string): ValidacionRango {
  const desde = minutosDelDia(horaDesde);
  if (desde === null) return { ok: false, campo: 'horaDesde', error: 'Ingresá una hora válida (HH:mm).' };
  const hasta = minutosDelDia(horaHasta);
  if (hasta === null) return { ok: false, campo: 'horaHasta', error: 'Ingresá una hora válida (HH:mm).' };
  if (desde >= hasta) return { ok: false, campo: 'horaHasta', error: 'La hora de fin debe ser posterior a la de inicio.' };
  return { ok: true };
}

export interface FranjaHoraria {
  readonly diaSemana: number;
  readonly horaDesde: string;
  readonly horaHasta: string;
}

/**
 * Primera franja existente del mismo día que se solapa con `nueva` (intervalos `[desde, hasta)`),
 * excluyendo la franja `excluirId` (la que se está editando). `null` si no hay solapamiento.
 */
export function buscarSolapamiento<T extends FranjaHoraria & { readonly id: number }>(
  nueva: FranjaHoraria,
  existentes: readonly T[],
  excluirId?: number,
): T | null {
  const desde = minutosDelDia(nueva.horaDesde);
  const hasta = minutosDelDia(nueva.horaHasta);
  if (desde === null || hasta === null) return null;
  for (const franja of existentes) {
    if (franja.id === excluirId || franja.diaSemana !== nueva.diaSemana) continue;
    const otraDesde = minutosDelDia(franja.horaDesde);
    const otraHasta = minutosDelDia(franja.horaHasta);
    if (otraDesde === null || otraHasta === null) continue;
    if (desde < otraHasta && otraDesde < hasta) return franja;
  }
  return null;
}
