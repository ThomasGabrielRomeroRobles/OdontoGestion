import { apiRequest, type ApiError } from '../../shared/http/client';
import { ESTADOS_ENTRADA, type EstadoEntrada as EstadoEntradaLista } from '../lista-espera/lista-espera-api';

/** Contrato de C-09 (`contrato-c09.md`, secciones `GET`/`POST /acciones/{token}`). */
export const ACCIONES = ['CONFIRMAR_SOLICITUD', 'CONFIRMAR_TURNO', 'CANCELAR_TURNO', 'CONFIRMAR_LISTA_ESPERA'] as const;
export type Accion = (typeof ACCIONES)[number];

export const ESTADOS_TURNO = [
  'PENDIENTE_CONFIRMACION',
  'RESERVADO',
  'CONFIRMADO',
  'CANCELADO',
  'AUSENTE',
  'ATENDIDO',
  'EXPIRADO',
] as const;
export type EstadoTurno = (typeof ESTADOS_TURNO)[number];

export type EstadoEntrada = EstadoEntradaLista;

export type MotivoNoDisponible = 'ESTADO_NO_PERMITE' | 'FUERA_DE_LIMITE';

export interface TurnoResumen {
  readonly consultorio: string;
  readonly prestacion: string;
  /** Instantes ISO-8601 UTC. */
  readonly inicio: string;
  readonly fin: string;
  readonly estado: EstadoTurno;
}

/** Inscripción en la lista de espera (C-10): fechas locales `YYYY-MM-DD`; sin datos del paciente. */
export interface EntradaResumen {
  readonly consultorio: string;
  readonly prestacion: string;
  readonly fechaDesde: string;
  readonly fechaHasta: string;
  readonly estado: EstadoEntrada;
}

/** Exactamente uno de `turno` y `entrada` viene informado; el otro es `null`. */
export interface ResumenAccion {
  readonly accion: Accion;
  readonly venceEn: string | null;
  readonly disponible: boolean;
  readonly motivo: MotivoNoDisponible | null;
  readonly limiteCancelacion: string | null;
  readonly turno: TurnoResumen | null;
  readonly entrada: EntradaResumen | null;
}

/** `noDisponible` = `410`: enlace inexistente, vencido o usado (la causa nunca se distingue). */
export type ResultadoResumen =
  | { readonly tipo: 'resumen'; readonly resumen: ResumenAccion }
  | { readonly tipo: 'noDisponible' };

/** `estadoCambiado` = `422`/`409`: el turno ya no admite la acción (o hubo un cambio simultáneo). */
export type ResultadoEjecucion =
  | { readonly tipo: 'ejecutada'; readonly resumen: ResumenAccion }
  | { readonly tipo: 'noDisponible' }
  | { readonly tipo: 'estadoCambiado' };

export interface AccionesApi {
  obtenerResumen(token: string): Promise<ResultadoResumen>;
  ejecutar(token: string, accion: Accion): Promise<ResultadoEjecucion>;
}

function esAccion(valor: unknown): valor is Accion {
  return typeof valor === 'string' && (ACCIONES as readonly string[]).includes(valor);
}

function esEstado(valor: unknown): valor is EstadoTurno {
  return typeof valor === 'string' && (ESTADOS_TURNO as readonly string[]).includes(valor);
}

function esTexto(valor: unknown): valor is string {
  return typeof valor === 'string';
}

function esEntrada(valor: unknown): valor is EntradaResumen {
  if (typeof valor !== 'object' || valor === null) return false;
  const e = valor as Record<string, unknown>;
  return (
    esTexto(e['consultorio']) &&
    esTexto(e['prestacion']) &&
    esTexto(e['fechaDesde']) &&
    esTexto(e['fechaHasta']) &&
    typeof e['estado'] === 'string' &&
    (ESTADOS_ENTRADA as readonly string[]).includes(e['estado'])
  );
}

function esTurno(valor: unknown): valor is TurnoResumen {
  if (typeof valor !== 'object' || valor === null) return false;
  const t = valor as Record<string, unknown>;
  return esTexto(t['consultorio']) && esTexto(t['prestacion']) && esTexto(t['inicio']) && esTexto(t['fin']) && esEstado(t['estado']);
}

/** Estrecha el JSON de la API (`unknown`) a `ResumenAccion`; lo que no cumple el contrato es un error. */
export function esResumenAccion(valor: unknown): valor is ResumenAccion {
  if (typeof valor !== 'object' || valor === null) return false;
  const r = valor as Record<string, unknown>;
  const motivo = r['motivo'];
  return (
    esAccion(r['accion']) &&
    typeof r['disponible'] === 'boolean' &&
    (motivo === null || motivo === 'ESTADO_NO_PERMITE' || motivo === 'FUERA_DE_LIMITE') &&
    (r['venceEn'] === null || esTexto(r['venceEn'])) &&
    (r['limiteCancelacion'] === null || esTexto(r['limiteCancelacion'])) &&
    ((r['turno'] === null && esEntrada(r['entrada'])) || (esTurno(r['turno']) && r['entrada'] === null))
  );
}

function esHttp(error: unknown, ...statuses: readonly number[]): error is Extract<ApiError, { kind: 'http' }> {
  if (typeof error !== 'object' || error === null) return false;
  const e = error as { kind?: unknown; status?: unknown };
  return e.kind === 'http' && typeof e.status === 'number' && statuses.includes(e.status);
}

function rutaDe(token: string): string {
  return `acciones/${encodeURIComponent(token)}`;
}

function comoResumen(cuerpo: unknown): ResumenAccion {
  if (!esResumenAccion(cuerpo)) throw { kind: 'http', status: 502, title: 'Respuesta inesperada', fieldErrors: [], extensions: {} } satisfies ApiError;
  return cuerpo;
}

export async function obtenerResumen(token: string): Promise<ResultadoResumen> {
  try {
    const cuerpo = await apiRequest<unknown>('GET', rutaDe(token));
    return { tipo: 'resumen', resumen: comoResumen(cuerpo) };
  } catch (error) {
    if (esHttp(error, 410)) return { tipo: 'noDisponible' };
    throw error;
  }
}

export async function ejecutar(token: string, accion: Accion): Promise<ResultadoEjecucion> {
  try {
    const cuerpo = await apiRequest<unknown>('POST', rutaDe(token), { accion });
    return { tipo: 'ejecutada', resumen: comoResumen(cuerpo) };
  } catch (error) {
    if (esHttp(error, 410)) return { tipo: 'noDisponible' };
    if (esHttp(error, 422, 409)) return { tipo: 'estadoCambiado' };
    throw error;
  }
}

export const accionesApi: AccionesApi = { obtenerResumen, ejecutar };
