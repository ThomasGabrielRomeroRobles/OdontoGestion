/** Traducción uniforme de los errores de la API al operar turnos (design D7). */
import type { ApiError } from '../../shared/http/client';
import type { Hueco } from './agenda-api';

export const MENSAJE_RED = 'No se pudo conectar. Reintentá.';
export const MENSAJE_CONFLICTO = 'Ese horario ya no está disponible.';
export const MENSAJE_INEXISTENTE = 'El turno ya no existe.';
export const MENSAJE_CONCURRENTE = 'Otro cambio se aplicó antes sobre este turno. Se recargó la agenda.';
const MENSAJE_INESPERADO = 'Ocurrió un error inesperado.';

/**
 * Mensaje de respaldo de cada `422` según el último segmento de su `type` (C-07 y backend de ausentismo de P3).
 * Solo se usa si el servidor no manda `detail`: el servidor es la autoridad (design D7).
 */
export const MENSAJES_422: Readonly<Record<string, string>> = {
  'ausente-antes-de-tolerancia': 'Todavía no pasó la tolerancia: todavía no se puede marcar ausente.',
  'reversion-fuera-de-plazo': 'Ya pasaron 24 horas desde la marca: la ausencia no se puede revertir.',
  'transicion-invalida': 'El turno cambió de estado y esa acción ya no corresponde.',
};

function mensaje422(error: Extract<ApiError, { kind: 'http' }>): string {
  if (error.detail !== undefined) return error.detail;
  const porTipo = error.code === undefined ? undefined : MENSAJES_422[error.code];
  return porTipo ?? error.title;
}

export interface ErrorTraducido {
  readonly mensaje: string;
  /** Huecos alternativos del 409 (vacío si no hay o vienen mal formados). */
  readonly alternativas: readonly Hueco[];
  /** `true` en un 409: el horario elegido ya no está libre. */
  readonly conflicto: boolean;
  /** Hay que recargar la agenda porque el estado pudo cambiar. */
  readonly recargar: boolean;
  /** Hay que cerrar el detalle (el turno ya no existe). */
  readonly cerrar: boolean;
}

export function esApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null || !('kind' in value)) return false;
  const kind = (value as { kind: unknown }).kind;
  return kind === 'network' || (kind === 'http' && 'status' in value);
}

function esHueco(value: unknown): value is Hueco {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  const { inicio, fin } = v;
  return typeof inicio === 'string' && typeof fin === 'string' && !Number.isNaN(Date.parse(inicio)) && !Number.isNaN(Date.parse(fin));
}

/** `alternativas` del cuerpo del 409, descartando los elementos mal formados. */
export function alternativasDe(error: ApiError): Hueco[] {
  if (error.kind !== 'http') return [];
  const valor = error.extensions['alternativas'];
  return Array.isArray(valor) ? valor.filter(esHueco) : [];
}

/**
 * C-07 emite dos 409: `hueco-no-disponible`, que SIEMPRE trae `alternativas` (puede ser `[]`), y
 * `modificacion-concurrente`, que no la trae. `ApiError` no expone `type`, así que se distinguen por ese miembro.
 */
function esConflictoDeHueco(error: ApiError): boolean {
  return error.kind === 'http' && 'alternativas' in error.extensions;
}

const base = { alternativas: [], conflicto: false, recargar: false, cerrar: false } as const;

export function traducirErrorAccion(error: unknown): ErrorTraducido {
  if (!esApiError(error)) return { ...base, mensaje: MENSAJE_INESPERADO };
  if (error.kind === 'network') return { ...base, mensaje: MENSAJE_RED };
  switch (error.status) {
    case 409:
      if (!esConflictoDeHueco(error)) return { ...base, mensaje: MENSAJE_CONCURRENTE, recargar: true };
      return { ...base, mensaje: error.detail ?? MENSAJE_CONFLICTO, alternativas: alternativasDe(error), conflicto: true };
    case 422:
      return { ...base, mensaje: mensaje422(error), recargar: true };
    case 404:
      return { ...base, mensaje: MENSAJE_INEXISTENTE, recargar: true, cerrar: true };
    default:
      return { ...base, mensaje: error.detail ?? error.title };
  }
}
