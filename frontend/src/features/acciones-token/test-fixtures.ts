import type { EstadoTurno, ResumenAccion } from './acciones-api';

/** Fixtures tipadas desde `contrato-c09.md` (C-09). Datos 100 % ficticios. */
const turno = {
  consultorio: 'Consultorio Ficticio',
  prestacion: 'Consulta',
  /** 10:00 del lunes 23/11/2026 en Buenos Aires (UTC-3). */
  inicio: '2026-11-23T13:00:00Z',
  fin: '2026-11-23T13:30:00Z',
} as const;

export const resumenCancelar: ResumenAccion = {
  accion: 'CANCELAR_TURNO',
  venceEn: '2026-11-23T11:00:00Z',
  disponible: true,
  motivo: null,
  limiteCancelacion: '2026-11-23T11:00:00Z',
  turno: { ...turno, estado: 'RESERVADO' },
  entrada: null,
};

export const resumenCancelarFueraDeLimite: ResumenAccion = {
  ...resumenCancelar,
  disponible: false,
  motivo: 'FUERA_DE_LIMITE',
};

export const resumenConfirmarSolicitud: ResumenAccion = {
  accion: 'CONFIRMAR_SOLICITUD',
  venceEn: '2026-11-22T22:30:00Z',
  disponible: true,
  motivo: null,
  limiteCancelacion: null,
  turno: { ...turno, estado: 'PENDIENTE_CONFIRMACION' },
  entrada: null,
};

export const resumenConfirmarTurno: ResumenAccion = {
  accion: 'CONFIRMAR_TURNO',
  venceEn: '2026-11-23T11:00:00Z',
  disponible: true,
  motivo: null,
  limiteCancelacion: null,
  turno: { ...turno, estado: 'RESERVADO' },
  entrada: null,
};

export const resumenNoPermite: ResumenAccion = {
  ...resumenConfirmarTurno,
  disponible: false,
  motivo: 'ESTADO_NO_PERMITE',
  turno: { ...turno, estado: 'CANCELADO' },
};

/** Respuesta del `POST` ya ejecutado: estado nuevo, token usado. */
export function ejecutado(resumen: ResumenAccion, estado: EstadoTurno): ResumenAccion {
  if (resumen.turno === null) throw new Error('ejecutado() espera un resumen de turno');
  return { ...resumen, disponible: false, motivo: 'ESTADO_NO_PERMITE', venceEn: null, turno: { ...resumen.turno, estado } };
}

const TIPO = 'https://odontogestion.example/problemas';

export const problema410 = {
  type: `${TIPO}/token-invalido`,
  title: 'Enlace no disponible',
  status: 410,
  detail: 'El enlace no es válido o ya fue utilizado.',
  instance: 'urn:odontogestion:enlace-no-disponible',
};

export const problema422Transicion = {
  type: `${TIPO}/transicion-invalida`,
  title: 'Transición inválida',
  status: 422,
  detail: 'El turno ya no admite esta acción.',
  instance: 'urn:odontogestion:ruta-por-token',
};

export const problema409Concurrente = {
  type: `${TIPO}/modificacion-concurrente`,
  title: 'Modificación concurrente',
  status: 409,
  detail: 'El turno fue modificado a la vez.',
  instance: 'urn:odontogestion:ruta-por-token',
};

/** Resumen de la confirmación de lista de espera (C-10 D3): `turno` nulo y `entrada` informada. */
export const resumenConfirmarListaEspera: ResumenAccion = {
  accion: 'CONFIRMAR_LISTA_ESPERA',
  venceEn: '2026-11-21T12:00:00Z',
  disponible: true,
  motivo: null,
  limiteCancelacion: null,
  turno: null,
  entrada: {
    consultorio: 'Consultorio Ficticio',
    prestacion: 'Consulta',
    fechaDesde: '2026-11-23',
    fechaHasta: '2026-12-02',
    estado: 'PENDIENTE_CONFIRMACION',
  },
};

/** Respuesta del `POST` de la confirmación ya ejecutada: la entrada quedó activa y el token usado. */
export const resumenListaEsperaActiva: ResumenAccion = {
  ...resumenConfirmarListaEspera,
  disponible: false,
  motivo: 'ESTADO_NO_PERMITE',
  venceEn: null,
  entrada: resumenConfirmarListaEspera.entrada === null ? null : { ...resumenConfirmarListaEspera.entrada, estado: 'ACTIVA' },
};
