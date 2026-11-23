/**
 * Reglas de presentación de la agenda como funciones puras (design D5). El cliente solo habilita
 * u oculta acciones: la API es la autoridad y sus 409/422 se muestran tal como llegan.
 */
import type { EstadoTurno, Turno } from './agenda-api';
import { horaLocal } from './calendario';

export const ESTADOS: readonly EstadoTurno[] = [
  'PENDIENTE_CONFIRMACION',
  'RESERVADO',
  'CONFIRMADO',
  'CANCELADO',
  'AUSENTE',
  'ATENDIDO',
  'EXPIRADO',
];

const ETIQUETAS: Readonly<Record<EstadoTurno, string>> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  RESERVADO: 'Reservado',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  AUSENTE: 'Ausente',
  ATENDIDO: 'Atendido',
  EXPIRADO: 'Expirado',
};

export function etiquetaEstado(estado: EstadoTurno): string {
  return ETIQUETAS[estado];
}

/** Clase CSS por estado (`turno--pendiente-confirmacion`, ...). */
export function claseEstado(estado: EstadoTurno): string {
  return `turno--${estado.toLowerCase().replace(/_/g, '-')}`;
}

/** Estados que todavía ocupan la agenda; `CANCELADO` y `EXPIRADO` se ocultan por defecto (D4). */
export function esOcultoPorDefecto(estado: EstadoTurno): boolean {
  return estado === 'CANCELADO' || estado === 'EXPIRADO';
}

export type Accion = 'mover' | 'cancelar' | 'atendido' | 'ausente' | 'revertir';

export interface AccionDisponible {
  readonly accion: Accion;
  readonly etiqueta: string;
  readonly habilitada: boolean;
  /** Por qué está deshabilitada (solo cuando `habilitada` es `false`). */
  readonly motivo?: string;
}

const MIN = 60_000;
const VENTANA_REVERSION_MS = 24 * 60 * MIN;

const ACTIVOS_CONFIRMABLES: ReadonlySet<EstadoTurno> = new Set(['RESERVADO', 'CONFIRMADO']);
const CANCELABLES: ReadonlySet<EstadoTurno> = new Set(['PENDIENTE_CONFIRMACION', 'RESERVADO', 'CONFIRMADO']);

const habilitada = (accion: Accion, etiqueta: string): AccionDisponible => ({ accion, etiqueta, habilitada: true });

/**
 * Acciones que ofrece el detalle del turno (tabla de design D5). "Marcar ausente" aparece deshabilitado
 * hasta inicio + tolerancia, con el horario desde el que aplica (RN-AS-01).
 */
export function accionesHabilitadas(turno: Turno, ahora: Date, toleranciaMin: number): AccionDisponible[] {
  const t = ahora.getTime();
  const inicio = Date.parse(turno.inicio);
  const acciones: AccionDisponible[] = [];

  if (ACTIVOS_CONFIRMABLES.has(turno.estado) && inicio > t) acciones.push(habilitada('mover', 'Mover'));
  if (CANCELABLES.has(turno.estado)) acciones.push(habilitada('cancelar', 'Cancelar'));
  if (ACTIVOS_CONFIRMABLES.has(turno.estado)) {
    acciones.push(habilitada('atendido', 'Marcar atendido'));
    const desde = inicio + Math.max(0, toleranciaMin) * MIN;
    acciones.push(
      t >= desde
        ? habilitada('ausente', 'Marcar ausente')
        : {
            accion: 'ausente',
            etiqueta: 'Marcar ausente',
            habilitada: false,
            motivo: `Disponible desde las ${horaLocal(new Date(desde))}`,
          },
    );
  }
  const marcadoEn = turno.ausenteMarcadoEn;
  if (turno.estado === 'AUSENTE' && marcadoEn !== null) {
    if (t < Date.parse(marcadoEn) + VENTANA_REVERSION_MS) {
      acciones.push(habilitada('revertir', 'Revertir ausencia'));
    }
  }
  return acciones;
}
