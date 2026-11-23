/**
 * SOLO DESARROLLO: API de acciones por token en memoria. Tokens de demostración: `confirmar-solicitud`,
 * `confirmar-turno`, `cancelar`; cualquier otro (p. ej. `vencido`) responde como un `410`. Un token se consume al ejecutarlo.
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
  };
}

const ESTADO_FINAL: Readonly<Record<Accion, EstadoTurno>> = {
  CONFIRMAR_SOLICITUD: 'RESERVADO',
  CONFIRMAR_TURNO: 'CONFIRMADO',
  CANCELAR_TURNO: 'CANCELADO',
};

export function crearAccionesApiMock(): AccionesApi {
  const vigentes = new Map<string, ResumenAccion>([
    ['confirmar-solicitud', resumenDe('CONFIRMAR_SOLICITUD', 'PENDIENTE_CONFIRMACION')],
    ['confirmar-turno', resumenDe('CONFIRMAR_TURNO', 'RESERVADO')],
    ['cancelar', resumenDe('CANCELAR_TURNO', 'RESERVADO')],
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
      return Promise.resolve({
        tipo: 'ejecutada',
        resumen: {
          ...resumen,
          disponible: false,
          motivo: 'ESTADO_NO_PERMITE',
          venceEn: null,
          turno: { ...resumen.turno, estado: ESTADO_FINAL[accion] },
        },
      });
    },
  };
}
