/**
 * SOLO DESARROLLO: API de acciones por token en memoria. Tokens de demostración: `confirmar-solicitud`,
 * `confirmar-turno`, `cancelar`, `confirmar-lista-espera`; cualquier otro (p. ej. `vencido`) responde como un `410`.
 * Un token se consume al ejecutarlo.
 */
import type { Accion, AccionesApi, EstadoTurno, ResumenAccion } from '../features/acciones-token/acciones-api';

const TURNO = {
  consultorio: 'Consultorio Demo',
  prestacion: 'Consulta',
  inicio: '2026-11-23T13:00:00Z',
  fin: '2026-11-23T13:30:00Z',
} as const;

function resumenDe(accion: Accion, estado: EstadoTurno): ResumenAccion {
  return {
    accion,
    venceEn: '2026-11-23T11:00:00Z',
    disponible: true,
    motivo: null,
    limiteCancelacion: accion === 'CANCELAR_TURNO' ? '2026-11-23T11:00:00Z' : null,
    turno: { ...TURNO, estado },
    entrada: null,
  };
}

const RESUMEN_LISTA_ESPERA: ResumenAccion = {
  accion: 'CONFIRMAR_LISTA_ESPERA',
  venceEn: '2026-11-21T12:00:00Z',
  disponible: true,
  motivo: null,
  limiteCancelacion: null,
  turno: null,
  entrada: {
    consultorio: 'Consultorio Demo',
    prestacion: 'Consulta',
    fechaDesde: '2026-11-23',
    fechaHasta: '2026-12-02',
    estado: 'PENDIENTE_CONFIRMACION',
  },
};

const ESTADO_FINAL: Readonly<Record<Exclude<Accion, 'CONFIRMAR_LISTA_ESPERA'>, EstadoTurno>> = {
  CONFIRMAR_SOLICITUD: 'RESERVADO',
  CONFIRMAR_TURNO: 'CONFIRMADO',
  CANCELAR_TURNO: 'CANCELADO',
};

function ejecutado(resumen: ResumenAccion): ResumenAccion {
  const comun = { ...resumen, disponible: false, motivo: 'ESTADO_NO_PERMITE', venceEn: null } as const;
  if (resumen.entrada !== null) return { ...comun, entrada: { ...resumen.entrada, estado: 'ACTIVA' } };
  if (resumen.turno === null || resumen.accion === 'CONFIRMAR_LISTA_ESPERA') return comun;
  return { ...comun, turno: { ...resumen.turno, estado: ESTADO_FINAL[resumen.accion] } };
}

export function crearAccionesApiMock(): AccionesApi {
  const vigentes = new Map<string, ResumenAccion>([
    ['confirmar-solicitud', resumenDe('CONFIRMAR_SOLICITUD', 'PENDIENTE_CONFIRMACION')],
    ['confirmar-turno', resumenDe('CONFIRMAR_TURNO', 'RESERVADO')],
    ['cancelar', resumenDe('CANCELAR_TURNO', 'RESERVADO')],
    ['confirmar-lista-espera', RESUMEN_LISTA_ESPERA],
  ]);
  return {
    obtenerResumen: (token) => {
      const resumen = vigentes.get(token);
      return Promise.resolve(resumen === undefined ? { tipo: 'noDisponible' } : { tipo: 'resumen', resumen });
    },
    ejecutar: (token, accion) => {
      const resumen = vigentes.get(token);
      if (resumen === undefined || resumen.accion !== accion) return Promise.resolve({ tipo: 'noDisponible' });
      vigentes.delete(token);
      return Promise.resolve({ tipo: 'ejecutada', resumen: ejecutado(resumen) });
    },
  };
}
