import { apiRequest, type ApiError } from '../../shared/http/client';
import type { FieldError } from '../../shared/http/problem';

/** Contrato de C-10 (`design.md` D2, D5 y D7). Si cambia, solo se ajusta acá y en `test-fixtures.ts`. */
export const ESTADOS_ENTRADA = ['PENDIENTE_CONFIRMACION', 'ACTIVA', 'CUBIERTA', 'CANCELADA', 'VENCIDA'] as const;
export type EstadoEntrada = (typeof ESTADOS_ENTRADA)[number];

export interface InscripcionListaEspera {
  readonly prestacionId: number;
  /** Fechas locales `YYYY-MM-DD`, inclusivas. */
  readonly fechaDesde: string;
  readonly fechaHasta: string;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string;
  /** Campo señuelo (honeypot): una persona no lo completa; se envía tal como esté. */
  readonly sitioWeb: string;
}

/** Hueco ofrecido por un aviso: instantes ISO-8601 UTC; nunca datos del paciente. */
export interface ResumenAviso {
  readonly consultorio: string;
  readonly prestacion: string;
  readonly inicio: string;
  readonly fin: string;
  readonly venceEn: string;
  readonly disponible: boolean;
}

export interface TurnoAceptado {
  readonly prestacion: string;
  readonly inicio: string;
  readonly fin: string;
  readonly estado: string;
}

export interface EntradaListaEspera {
  readonly id: number;
  /** "Apellido, Nombre". */
  readonly paciente: string;
  readonly prestacion: string;
  readonly fechaDesde: string;
  readonly fechaHasta: string;
  readonly estado: EstadoEntrada;
  readonly creadoEn: string;
}

/** Todo lo demás (red, 5xx) se propaga como error para el mensaje genérico. */
export type ResultadoInscripcion =
  | { readonly tipo: 'aceptada'; readonly mensaje: string }
  | { readonly tipo: 'limite'; readonly retryAfterSeconds?: number }
  | { readonly tipo: 'invalida'; readonly fieldErrors: readonly FieldError[] };

/** `noDisponible` = `410`: enlace inexistente, vencido o usado (la causa nunca se distingue). */
export type ResultadoAviso =
  | { readonly tipo: 'resumen'; readonly resumen: ResumenAviso }
  | { readonly tipo: 'noDisponible' };

/** `noDisponible` = `409` (otro paciente ganó o el hueco se ocupó); `vencido` = `410`. */
export type ResultadoAceptacion =
  | { readonly tipo: 'reservado'; readonly turno: TurnoAceptado }
  | { readonly tipo: 'noDisponible' }
  | { readonly tipo: 'vencido' };

export interface ListaEsperaApi {
  inscribir(inscripcion: InscripcionListaEspera): Promise<ResultadoInscripcion>;
  obtenerAviso(token: string): Promise<ResultadoAviso>;
  aceptarAviso(token: string): Promise<ResultadoAceptacion>;
  listar(): Promise<readonly EntradaListaEspera[]>;
  retirar(id: number): Promise<void>;
}

function esTexto(valor: unknown): valor is string {
  return typeof valor === 'string';
}

function esResumenAviso(valor: unknown): valor is ResumenAviso {
  if (typeof valor !== 'object' || valor === null) return false;
  const r = valor as Record<string, unknown>;
  return (
    esTexto(r['consultorio']) &&
    esTexto(r['prestacion']) &&
    esTexto(r['inicio']) &&
    esTexto(r['fin']) &&
    esTexto(r['venceEn']) &&
    typeof r['disponible'] === 'boolean'
  );
}

function esTurnoAceptado(valor: unknown): valor is TurnoAceptado {
  if (typeof valor !== 'object' || valor === null) return false;
  const t = valor as Record<string, unknown>;
  return esTexto(t['prestacion']) && esTexto(t['inicio']) && esTexto(t['fin']) && esTexto(t['estado']);
}

function esEstadoEntrada(valor: unknown): valor is EstadoEntrada {
  return typeof valor === 'string' && (ESTADOS_ENTRADA as readonly string[]).includes(valor);
}

function esEntrada(valor: unknown): valor is EntradaListaEspera {
  if (typeof valor !== 'object' || valor === null) return false;
  const e = valor as Record<string, unknown>;
  return (
    typeof e['id'] === 'number' &&
    esTexto(e['paciente']) &&
    esTexto(e['prestacion']) &&
    esTexto(e['fechaDesde']) &&
    esTexto(e['fechaHasta']) &&
    esEstadoEntrada(e['estado']) &&
    esTexto(e['creadoEn'])
  );
}

function esHttp(error: unknown, ...statuses: readonly number[]): error is Extract<ApiError, { kind: 'http' }> {
  if (typeof error !== 'object' || error === null) return false;
  const e = error as { kind?: unknown; status?: unknown };
  return e.kind === 'http' && typeof e.status === 'number' && (statuses.length === 0 || statuses.includes(e.status));
}

function respuestaInesperada(): ApiError {
  return { kind: 'http', status: 502, title: 'Respuesta inesperada', fieldErrors: [], extensions: {} };
}

function rutaAviso(token: string): string {
  return `avisos/${encodeURIComponent(token)}`;
}

export async function inscribir(inscripcion: InscripcionListaEspera): Promise<ResultadoInscripcion> {
  try {
    const cuerpo = await apiRequest<unknown>('POST', 'publico/lista-espera', inscripcion);
    const mensaje = typeof cuerpo === 'object' && cuerpo !== null ? (cuerpo as Record<string, unknown>)['mensaje'] : undefined;
    return { tipo: 'aceptada', mensaje: esTexto(mensaje) ? mensaje : '' };
  } catch (error) {
    if (!esHttp(error)) throw error;
    if (error.status === 429) {
      return error.retryAfterSeconds === undefined
        ? { tipo: 'limite' }
        : { tipo: 'limite', retryAfterSeconds: error.retryAfterSeconds };
    }
    if (error.status === 400 || (error.status === 422 && error.fieldErrors.length > 0)) {
      return { tipo: 'invalida', fieldErrors: error.fieldErrors };
    }
    throw error;
  }
}

export async function obtenerAviso(token: string): Promise<ResultadoAviso> {
  try {
    const cuerpo = await apiRequest<unknown>('GET', rutaAviso(token));
    if (!esResumenAviso(cuerpo)) throw respuestaInesperada();
    return { tipo: 'resumen', resumen: cuerpo };
  } catch (error) {
    if (esHttp(error, 410)) return { tipo: 'noDisponible' };
    throw error;
  }
}

export async function aceptarAviso(token: string): Promise<ResultadoAceptacion> {
  try {
    const cuerpo = await apiRequest<unknown>('POST', `${rutaAviso(token)}/aceptar`);
    if (!esTurnoAceptado(cuerpo)) throw respuestaInesperada();
    return { tipo: 'reservado', turno: cuerpo };
  } catch (error) {
    if (esHttp(error, 409)) return { tipo: 'noDisponible' };
    if (esHttp(error, 410)) return { tipo: 'vencido' };
    throw error;
  }
}

export async function listar(): Promise<readonly EntradaListaEspera[]> {
  const cuerpo = await apiRequest<unknown>('GET', 'lista-espera');
  if (!Array.isArray(cuerpo) || !cuerpo.every(esEntrada)) throw respuestaInesperada();
  return cuerpo;
}

export async function retirar(id: number): Promise<void> {
  await apiRequest<undefined>('DELETE', `lista-espera/${id}`);
}

export const listaEsperaApi: ListaEsperaApi = { inscribir, obtenerAviso, aceptarAviso, listar, retirar };
