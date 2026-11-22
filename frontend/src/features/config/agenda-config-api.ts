import { apiRequest } from '../../shared/http/client';

/** Contrato de C-06 (design.md D1). Si el backend lo cambia, solo se ajusta este módulo. */
export interface Prestacion {
  readonly id: number;
  readonly nombre: string;
  readonly duracionMin: number;
  readonly activa: boolean;
}

export interface PrestacionInput {
  readonly nombre: string;
  readonly duracionMin: number;
}

/** 1 = lunes … 7 = domingo (ISO-8601). */
export type DiaSemana = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export interface Horario {
  readonly id: number;
  readonly diaSemana: DiaSemana;
  /** Hora local del consultorio, `HH:mm`. */
  readonly horaDesde: string;
  readonly horaHasta: string;
}

export interface HorarioInput {
  readonly diaSemana: DiaSemana;
  readonly horaDesde: string;
  readonly horaHasta: string;
}

export interface Bloqueo {
  readonly id: number;
  /** Instante ISO-8601 en UTC. */
  readonly desde: string;
  readonly hasta: string;
  readonly motivo: string | null;
}

export interface BloqueoInput {
  readonly desde: string;
  readonly hasta: string;
  readonly motivo: string | null;
}

export interface RangoInstantes {
  readonly desde: string;
  readonly hasta: string;
}

export function listarPrestaciones(): Promise<Prestacion[]> {
  return apiRequest<Prestacion[]>('GET', 'prestaciones');
}

export function crearPrestacion(input: PrestacionInput): Promise<Prestacion> {
  return apiRequest<Prestacion>('POST', 'prestaciones', input);
}

export function actualizarPrestacion(id: number, input: PrestacionInput): Promise<Prestacion> {
  return apiRequest<Prestacion>('PUT', `prestaciones/${id}`, input);
}

export function desactivarPrestacion(id: number): Promise<Prestacion> {
  return apiRequest<Prestacion>('POST', `prestaciones/${id}/desactivar`);
}

export function activarPrestacion(id: number): Promise<Prestacion> {
  return apiRequest<Prestacion>('POST', `prestaciones/${id}/activar`);
}

export function listarHorarios(): Promise<Horario[]> {
  return apiRequest<Horario[]>('GET', 'horarios');
}

export function crearHorario(input: HorarioInput): Promise<Horario> {
  return apiRequest<Horario>('POST', 'horarios', input);
}

export function actualizarHorario(id: number, input: HorarioInput): Promise<Horario> {
  return apiRequest<Horario>('PUT', `horarios/${id}`, input);
}

export function eliminarHorario(id: number): Promise<void> {
  return apiRequest<void>('DELETE', `horarios/${id}`);
}

export function listarBloqueos(rango?: RangoInstantes): Promise<Bloqueo[]> {
  const query = rango === undefined ? '' : `?${new URLSearchParams({ desde: rango.desde, hasta: rango.hasta }).toString()}`;
  return apiRequest<Bloqueo[]>('GET', `bloqueos${query}`);
}

export function crearBloqueo(input: BloqueoInput): Promise<Bloqueo> {
  return apiRequest<Bloqueo>('POST', 'bloqueos', input);
}

export function actualizarBloqueo(id: number, input: BloqueoInput): Promise<Bloqueo> {
  return apiRequest<Bloqueo>('PUT', `bloqueos/${id}`, input);
}

export function eliminarBloqueo(id: number): Promise<void> {
  return apiRequest<void>('DELETE', `bloqueos/${id}`);
}

/** Operaciones de agenda inyectables en las vistas (los tests pasan dobles). */
export interface AgendaConfigApi {
  readonly listarPrestaciones: typeof listarPrestaciones;
  readonly crearPrestacion: typeof crearPrestacion;
  readonly actualizarPrestacion: typeof actualizarPrestacion;
  readonly desactivarPrestacion: typeof desactivarPrestacion;
  readonly activarPrestacion: typeof activarPrestacion;
  readonly listarHorarios: typeof listarHorarios;
  readonly crearHorario: typeof crearHorario;
  readonly actualizarHorario: typeof actualizarHorario;
  readonly eliminarHorario: typeof eliminarHorario;
  readonly listarBloqueos: typeof listarBloqueos;
  readonly crearBloqueo: typeof crearBloqueo;
  readonly actualizarBloqueo: typeof actualizarBloqueo;
  readonly eliminarBloqueo: typeof eliminarBloqueo;
}

export const agendaConfigApi: AgendaConfigApi = {
  listarPrestaciones,
  crearPrestacion,
  actualizarPrestacion,
  desactivarPrestacion,
  activarPrestacion,
  listarHorarios,
  crearHorario,
  actualizarHorario,
  eliminarHorario,
  listarBloqueos,
  crearBloqueo,
  actualizarBloqueo,
  eliminarBloqueo,
};
