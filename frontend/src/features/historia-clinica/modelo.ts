import { partesEnZona } from '../config/zoned-time';
import type { ApiError } from '../../shared/http/client';

/** Estados de pieza del MVP (C-05, KB 04), por pieza completa. */
export const ESTADOS_PIEZA = [
  'SANA',
  'CARIES',
  'OBTURADA',
  'CORONA',
  'ENDODONCIA',
  'AUSENTE',
  'IMPLANTE',
  'FRACTURADA',
  'A_EXTRAER',
] as const;
export type EstadoPieza = (typeof ESTADOS_PIEZA)[number];

const ETIQUETAS: Readonly<Record<EstadoPieza, string>> = {
  SANA: 'Sana',
  CARIES: 'Caries',
  OBTURADA: 'Obturada',
  CORONA: 'Corona',
  ENDODONCIA: 'Endodoncia',
  AUSENTE: 'Ausente',
  IMPLANTE: 'Implante',
  FRACTURADA: 'Fracturada',
  A_EXTRAER: 'A extraer',
};

function cuadrante(c: number, ascendente: boolean): number[] {
  const piezas = [1, 2, 3, 4, 5, 6, 7, 8].map((p) => c * 10 + p);
  return ascendente ? piezas : piezas.reverse();
}

/** Vista del odontólogo frente al paciente: 18→11 | 21→28 arriba y 48→41 | 31→38 abajo. */
export const FILA_SUPERIOR: readonly number[] = [...cuadrante(1, false), ...cuadrante(2, true)];
export const FILA_INFERIOR: readonly number[] = [...cuadrante(4, false), ...cuadrante(3, true)];
/** Las 32 piezas permanentes en el orden de la API (11..18, 21..28, 31..38, 41..48). */
export const PIEZAS_PERMANENTES: readonly number[] = [1, 2, 3, 4].flatMap((c) => cuadrante(c, true));

export function esEstadoPieza(valor: string): valor is EstadoPieza {
  return (ESTADOS_PIEZA as readonly string[]).includes(valor);
}

export function etiquetaEstado(estado: EstadoPieza | null): string {
  return estado === null ? 'Sin registrar' : ETIQUETAS[estado];
}

export function claseEstado(estado: EstadoPieza | null): string {
  return `pieza--${estado === null ? 'sin-registrar' : estado.toLowerCase().replace('_', '-')}`;
}

const pad = (n: number): string => String(n).padStart(2, '0');

/** Fecha civil de hoy (`YYYY-MM-DD`) en la zona del consultorio. */
export function hoyEnConsultorio(ahora: Date): string {
  const p = partesEnZona(ahora.getTime());
  return `${p.year}-${pad(p.month)}-${pad(p.day)}`;
}

/** `YYYY-MM-DD` → `dd/MM/yyyy`. */
export function fechaCivil(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia ?? ''}/${mes ?? ''}/${anio ?? ''}`;
}

function esApiErrorHttp(error: unknown): error is Extract<ApiError, { kind: 'http' }> {
  return typeof error === 'object' && error !== null && (error as { kind?: unknown }).kind === 'http';
}

/** Estado HTTP de un error de la API, o `null` si fue de red u otra cosa. */
export function estadoHttp(error: unknown): number | null {
  return esApiErrorHttp(error) ? error.status : null;
}

/** Título del ProblemDetail (y su detalle si lo trae) para mostrar junto a la acción fallida. */
export function mensajeProblema(error: unknown): string {
  if (!esApiErrorHttp(error)) return 'No se pudo conectar con el servidor. Intentá de nuevo.';
  return error.detail === undefined || error.detail === '' ? error.title : `${error.title}: ${error.detail}`;
}
