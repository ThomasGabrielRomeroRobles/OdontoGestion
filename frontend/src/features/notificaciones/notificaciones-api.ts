import { apiRequest } from '../../shared/http/client';

/** Contrato de C-03 (design.md D13, `NotificacionResponse` / `PaginaResponse`). Si cambia, solo se ajusta acá. */
export const ESTADOS_NOTIFICACION = ['PENDIENTE', 'ENVIADA', 'FALLIDA'] as const;
export type EstadoNotificacion = (typeof ESTADOS_NOTIFICACION)[number];

export const TIPOS_NOTIFICACION = [
  'CONFIRMAR_SOLICITUD',
  'CONFIRMAR_LISTA_ESPERA',
  'CONFIRMACION_TURNO',
  'RECORDATORIO',
  'AVISO_HUECO',
  'CANCELACION',
  'AUSENTE_REPROGRAMAR',
] as const;
export type TipoNotificacion = (typeof TIPOS_NOTIFICACION)[number];

export interface ReferenciaNotificacion {
  readonly tipo: 'TURNO' | 'AVISO' | 'ENTRADA_LISTA_ESPERA';
  readonly id: number;
}

export interface NotificacionDto {
  readonly id: number;
  readonly tipo: TipoNotificacion;
  readonly canal: 'EMAIL' | 'WHATSAPP';
  readonly destinatario: string;
  readonly estado: EstadoNotificacion;
  readonly intentos: number;
  readonly reintentosManuales: number;
  readonly error: string | null;
  readonly referencia: ReferenciaNotificacion;
  /** Instantes ISO-8601 en UTC. */
  readonly programadaPara: string;
  readonly enviadaEn: string | null;
  readonly creadoEn: string;
}

export interface Pagina<T> {
  readonly contenido: readonly T[];
  readonly pagina: number;
  readonly tamanio: number;
  readonly totalElementos: number;
  readonly totalPaginas: number;
}

export interface FiltrosNotificaciones {
  readonly estado?: EstadoNotificacion;
  readonly tipo?: TipoNotificacion;
  readonly page: number;
  readonly size: number;
}

export function listar(filtros: FiltrosNotificaciones): Promise<Pagina<NotificacionDto>> {
  const query = new URLSearchParams();
  if (filtros.estado !== undefined) query.set('estado', filtros.estado);
  if (filtros.tipo !== undefined) query.set('tipo', filtros.tipo);
  query.set('page', String(filtros.page));
  query.set('size', String(filtros.size));
  return apiRequest<Pagina<NotificacionDto>>('GET', `notificaciones?${query.toString()}`);
}

export function reintentar(id: number): Promise<NotificacionDto> {
  return apiRequest<NotificacionDto>('POST', `notificaciones/${id}/reintentar`);
}

export interface NotificacionesApi {
  readonly listar: typeof listar;
  readonly reintentar: typeof reintentar;
}

export const notificacionesApi: NotificacionesApi = { listar, reintentar };
