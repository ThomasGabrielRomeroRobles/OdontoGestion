/**
 * Calendario de la agenda en la zona del consultorio (design D3, D4), sin librerías.
 * Las fechas de calendario viajan como `yyyy-MM-dd`; la aritmética de días se hace en UTC puro
 * sobre esa fecha (no depende de la zona del navegador) y la conversión a instantes usa `zoned-time`.
 */
import { localAInstante, partesEnZona } from '../config/zoned-time';
import type { Turno } from './agenda-api';

export type Vista = 'dia' | 'semana';

export interface Rango {
  readonly desde: string;
  readonly hasta: string;
}

const FECHA = /^(\d{4})-(\d{2})-(\d{2})$/;
const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'] as const;
const pad = (n: number): string => String(n).padStart(2, '0');

function aUtc(fecha: string): Date | null {
  const m = FECHA.exec(fecha);
  if (m === null) return null;
  const [anio, mes, dia] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const d = new Date(Date.UTC(anio, mes - 1, dia));
  return d.getUTCFullYear() === anio && d.getUTCMonth() === mes - 1 && d.getUTCDate() === dia ? d : null;
}

function desdeUtc(d: Date): string {
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

function exigirFecha(fecha: string): Date {
  const d = aUtc(fecha);
  if (d === null) throw new RangeError(`Fecha inválida: ${fecha}`);
  return d;
}

export function esFechaValida(fecha: string): boolean {
  return aUtc(fecha) !== null;
}

export function sumarDias(fecha: string, dias: number): string {
  const d = exigirFecha(fecha);
  d.setUTCDate(d.getUTCDate() + dias);
  return desdeUtc(d);
}

/** Lunes de la semana (lunes a domingo) que contiene `fecha`. */
export function inicioSemana(fecha: string): string {
  const diaSemana = exigirFecha(fecha).getUTCDay(); // 0 = domingo
  return sumarDias(fecha, -((diaSemana + 6) % 7));
}

export function diasDeSemana(fecha: string): string[] {
  const lunes = inicioSemana(fecha);
  return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i));
}

function medianoche(fecha: string): string {
  const instante = localAInstante(`${fecha}T00:00`);
  if (instante === null) throw new RangeError(`Fecha inválida: ${fecha}`);
  return instante;
}

/** Instantes UTC `[desde, hasta)` que cubren el día del consultorio. */
export function rangoDia(fecha: string): Rango {
  return { desde: medianoche(fecha), hasta: medianoche(sumarDias(fecha, 1)) };
}

/** Instantes UTC `[desde, hasta)` que cubren la semana (lunes a domingo) del consultorio. */
export function rangoSemana(fecha: string): Rango {
  const lunes = inicioSemana(fecha);
  return { desde: medianoche(lunes), hasta: medianoche(sumarDias(lunes, 7)) };
}

/** Fecha de calendario del consultorio (`yyyy-MM-dd`) de un instante. */
export function fechaLocal(instante: string | Date): string {
  const p = partesEnZona(typeof instante === 'string' ? Date.parse(instante) : instante.getTime());
  return `${p.year}-${pad(p.month)}-${pad(p.day)}`;
}

/** Hora del consultorio (`HH:mm`) de un instante. */
export function horaLocal(instante: string | Date): string {
  const p = partesEnZona(typeof instante === 'string' ? Date.parse(instante) : instante.getTime());
  return `${pad(p.hour)}:${pad(p.minute)}`;
}

/** `Lun 23/11`. */
export function etiquetaDia(fecha: string): string {
  const d = exigirFecha(fecha);
  return `${DIAS[d.getUTCDay()] ?? ''} ${pad(d.getUTCDate())}/${pad(d.getUTCMonth() + 1)}`;
}

const DIAS_LARGOS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'] as const;

/** `Martes 24/11/2026`. */
export function fechaLarga(fecha: string): string {
  return `${DIAS_LARGOS[exigirFecha(fecha).getUTCDay()] ?? ''} ${fechaCorta(fecha)}`;
}

/** `23/11/2026`. */
export function fechaCorta(fecha: string): string {
  const d = exigirFecha(fecha);
  return `${pad(d.getUTCDate())}/${pad(d.getUTCMonth() + 1)}/${d.getUTCFullYear()}`;
}

export interface VistaYFecha {
  readonly vista: Vista;
  readonly fecha: string;
  /** `true` si la URL no traía valores válidos y hay que reemplazarla por la normalizada. */
  readonly normalizado: boolean;
}

export function parsearVistaYFecha(query: URLSearchParams, hoy: string): VistaYFecha {
  const vistaUrl = query.get('vista');
  const fechaUrl = query.get('fecha');
  const vistaOk = vistaUrl === 'dia' || vistaUrl === 'semana';
  const fechaOk = fechaUrl !== null && esFechaValida(fechaUrl);
  return {
    vista: vistaOk ? vistaUrl : 'semana',
    fecha: fechaOk ? fechaUrl : hoy,
    normalizado: !vistaOk || !fechaOk,
  };
}

export function urlAgenda(vista: Vista, fecha: string): string {
  return `/agenda?vista=${vista}&fecha=${fecha}`;
}

/** Turnos por día del consultorio, cada grupo ordenado por inicio. */
export function agruparPorDia(turnos: readonly Turno[]): Map<string, Turno[]> {
  const grupos = new Map<string, Turno[]>();
  const ordenados = [...turnos].sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
  for (const turno of ordenados) {
    const dia = fechaLocal(turno.inicio);
    const grupo = grupos.get(dia);
    if (grupo === undefined) grupos.set(dia, [turno]);
    else grupo.push(turno);
  }
  return grupos;
}
