import type { NotificacionDto, Pagina } from './notificaciones-api';

/** Datos ficticios con la forma del contrato de C-03 (design D13, `NotificacionResponse`). */
export const fallida: NotificacionDto = {
  id: 7,
  tipo: 'CONFIRMACION_TURNO',
  canal: 'EMAIL',
  destinatario: 'ana.ficticia@example.com',
  estado: 'FALLIDA',
  intentos: 3,
  reintentosManuales: 0,
  error: 'MailSendException: conexión rechazada',
  referencia: { tipo: 'TURNO', id: 101 },
  programadaPara: '2026-11-20T13:00:00Z',
  enviadaEn: null,
  creadoEn: '2026-11-20T13:00:00Z',
};

export const enviada: NotificacionDto = {
  ...fallida,
  id: 8,
  tipo: 'RECORDATORIO',
  destinatario: 'bruno.ficticio@example.com',
  estado: 'ENVIADA',
  intentos: 1,
  error: null,
  enviadaEn: '2026-11-21T12:00:00Z',
  creadoEn: '2026-11-21T11:59:00Z',
};

export const pendiente: NotificacionDto = {
  ...fallida,
  id: 9,
  tipo: 'CANCELACION',
  destinatario: 'carla.ficticia@example.com',
  estado: 'PENDIENTE',
  intentos: 0,
  error: null,
  creadoEn: '2026-11-22T02:30:00Z',
};

export function pagina(
  contenido: readonly NotificacionDto[],
  extra: Partial<Omit<Pagina<NotificacionDto>, 'contenido'>> = {},
): Pagina<NotificacionDto> {
  return {
    contenido,
    pagina: 0,
    tamanio: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length === 0 ? 0 : 1,
    ...extra,
  };
}
