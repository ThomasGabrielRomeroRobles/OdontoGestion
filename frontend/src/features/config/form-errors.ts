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

/** Traduce un error de la API a mensajes por campo (422) o a un mensaje general. */
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
