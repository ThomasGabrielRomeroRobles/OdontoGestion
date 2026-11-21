import type { ApiError } from '../../shared/http/client';

export const NETWORK_MESSAGE = 'No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo.';
const UNEXPECTED_MESSAGE = 'Ocurrió un error inesperado.';

export interface FormErrors {
  readonly byField: Readonly<Record<string, string>>;
  readonly general: string | null;
}

function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null || !('kind' in value)) return false;
  const kind = (value as { kind: unknown }).kind;
  return kind === 'network' || (kind === 'http' && 'status' in value && 'fieldErrors' in value);
}

/**
 * Quita `prefijo` de los nombres de campo (p. ej. `parametros.granularidadMin` → `granularidadMin`)
 * para ubicar los errores de objetos anidados junto a su input plano (design.md D10).
 * Si el campo plano ya tenía su propio error, ese mensaje gana.
 */
export function quitarPrefijoCampos(errores: FormErrors, prefijo: string): FormErrors {
  const byField: Record<string, string> = {};
  for (const [campo, mensaje] of Object.entries(errores.byField)) {
    if (!campo.startsWith(prefijo)) byField[campo] = mensaje;
  }
  for (const [campo, mensaje] of Object.entries(errores.byField)) {
    if (!campo.startsWith(prefijo)) continue;
    const plano = campo.slice(prefijo.length);
    if (!(plano in byField)) byField[plano] = mensaje;
  }
  return { byField, general: errores.general };
}

/** Traduce un error de la API a mensajes por campo (400/422) o a un mensaje general. */
export function toFormErrors(error: unknown): FormErrors {
  if (!isApiError(error)) return { byField: {}, general: UNEXPECTED_MESSAGE };
  if (error.kind === 'network') return { byField: {}, general: NETWORK_MESSAGE };

  const byField: Record<string, string> = {};
  for (const { field, message } of error.fieldErrors) {
    if (!(field in byField)) byField[field] = message;
  }
  if (Object.keys(byField).length > 0) return { byField, general: null };
  return { byField, general: error.detail ?? error.title };
}
