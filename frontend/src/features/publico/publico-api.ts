import { apiRequest, type ApiError } from '../../shared/http/client';
import type { FieldError } from '../../shared/http/problem';

/** Contrato de C-09 (`contrato-c09.md`). Si cambia, solo se ajusta acá y en `test-fixtures.ts`. */
export interface PrestacionPublica {
  readonly id: number;
  readonly nombre: string;
  readonly duracionMin: number;
}

/** Hueco libre `[inicio, fin)`: instantes ISO-8601 en UTC. */
export interface HuecoLibre {
  readonly inicio: string;
  readonly fin: string;
}

export interface SolicitudTurno {
  readonly prestacionId: number;
  readonly inicio: string;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string;
  /** Campo señuelo (honeypot): una persona no lo completa; se envía tal como esté. */
  readonly sitioWeb: string;
}

/** Resultado de una solicitud; todo lo demás (red, 5xx) se propaga como error para el mensaje genérico. */
export type ResultadoSolicitud =
  | { readonly tipo: 'aceptada'; readonly mensaje: string }
  | { readonly tipo: 'conflicto'; readonly alternativas: readonly HuecoLibre[] }
  | { readonly tipo: 'limite'; readonly retryAfterSeconds?: number }
  | { readonly tipo: 'invalida'; readonly fieldErrors: readonly FieldError[] }
  | { readonly tipo: 'horarioInvalido'; readonly fieldErrors: readonly FieldError[] };

export interface PublicoApi {
  listarPrestaciones(): Promise<readonly PrestacionPublica[]>;
  /** Huecos libres de un día (`YYYY-MM-DD`, zona del consultorio). */
  consultarDisponibilidad(prestacionId: number, fecha: string): Promise<readonly HuecoLibre[]>;
  solicitarTurno(solicitud: SolicitudTurno): Promise<ResultadoSolicitud>;
}

interface Aceptada {
  readonly mensaje: string;
}

function esHuecoLibre(valor: unknown): valor is HuecoLibre {
  if (typeof valor !== 'object' || valor === null) return false;
  const v = valor as Record<string, unknown>;
  return typeof v['inicio'] === 'string' && typeof v['fin'] === 'string';
}

/** `alternativas` llega como dato `unknown` del ProblemDetail: se descarta lo que no tenga la forma del contrato. */
export function parseAlternativas(valor: unknown): HuecoLibre[] {
  return Array.isArray(valor) ? valor.filter(esHuecoLibre).map((h) => ({ inicio: h.inicio, fin: h.fin })) : [];
}

export function listarPrestaciones(): Promise<readonly PrestacionPublica[]> {
  return apiRequest<PrestacionPublica[]>('GET', 'publico/prestaciones');
}

export function consultarDisponibilidad(prestacionId: number, fecha: string): Promise<readonly HuecoLibre[]> {
  const query = new URLSearchParams({ prestacionId: String(prestacionId), desde: fecha, hasta: fecha });
  return apiRequest<HuecoLibre[]>('GET', `publico/disponibilidad?${query.toString()}`);
}

function esHttp(error: unknown): error is Extract<ApiError, { kind: 'http' }> {
  return typeof error === 'object' && error !== null && (error as { kind?: unknown }).kind === 'http';
}

export async function solicitarTurno(solicitud: SolicitudTurno): Promise<ResultadoSolicitud> {
  try {
    const respuesta = await apiRequest<Aceptada>('POST', 'publico/solicitudes-turno', solicitud);
    return { tipo: 'aceptada', mensaje: respuesta.mensaje };
  } catch (error) {
    if (!esHttp(error)) throw error;
    if (error.status === 409 && error.code === 'hueco-no-disponible') {
      return { tipo: 'conflicto', alternativas: parseAlternativas(error.extensions['alternativas']) };
    }
    if (error.status === 429) {
      return error.retryAfterSeconds === undefined
        ? { tipo: 'limite' }
        : { tipo: 'limite', retryAfterSeconds: error.retryAfterSeconds };
    }
    if (error.status === 422 && error.fieldErrors.some((e) => e.field === 'inicio')) {
      return { tipo: 'horarioInvalido', fieldErrors: error.fieldErrors };
    }
    if (error.status === 400 || (error.status === 422 && error.fieldErrors.length > 0)) {
      return { tipo: 'invalida', fieldErrors: error.fieldErrors };
    }
    throw error;
  }
}

export const publicoApi: PublicoApi = { listarPrestaciones, consultarDisponibilidad, solicitarTurno };
