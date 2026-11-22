import { describe, expect, it } from 'vitest';
import type { EstadoTurno } from './agenda-api';
import { accionesHabilitadas, claseEstado, ESTADOS, etiquetaEstado, type Accion } from './modelo';
import { turnoEn } from './test-fixtures';

/** Turno base: inicio 2026-11-24T12:00:00Z (09:00 en el consultorio). */
const INICIO = Date.parse('2026-11-24T12:00:00Z');
const MIN = 60_000;
const en = (ms: number): Date => new Date(ms);

function habilitadas(estado: EstadoTurno, ahora: Date, tolerancia = 15, ausenteMarcadoEn: string | null = null): Accion[] {
  return accionesHabilitadas(turnoEn(estado, { ausenteMarcadoEn }), ahora, tolerancia)
    .filter((a) => a.habilitada)
    .map((a) => a.accion);
}

describe('etiquetas y estilos por estado (3.1)', () => {
  it('los 7 estados tienen etiqueta y clase propias', () => {
    expect(ESTADOS).toHaveLength(7);
    expect(new Set(ESTADOS.map(etiquetaEstado)).size).toBe(7);
    expect(new Set(ESTADOS.map(claseEstado)).size).toBe(7);
  });

  it.each([
    ['PENDIENTE_CONFIRMACION', 'Pendiente de confirmación', 'turno--pendiente-confirmacion'],
    ['RESERVADO', 'Reservado', 'turno--reservado'],
    ['CONFIRMADO', 'Confirmado', 'turno--confirmado'],
    ['CANCELADO', 'Cancelado', 'turno--cancelado'],
    ['AUSENTE', 'Ausente', 'turno--ausente'],
    ['ATENDIDO', 'Atendido', 'turno--atendido'],
    ['EXPIRADO', 'Expirado', 'turno--expirado'],
  ] as const)('%s → "%s" / %s', (estado, etiqueta, clase) => {
    expect(etiquetaEstado(estado)).toBe(etiqueta);
    expect(claseEstado(estado)).toBe(clase);
  });
});

describe('accionesHabilitadas (3.2, design D5)', () => {
  const antes = en(INICIO - 120 * MIN);

  it('RESERVADO futuro: mover, cancelar y atendido', () => {
    expect(habilitadas('RESERVADO', antes)).toEqual(['mover', 'cancelar', 'atendido']);
  });

  it('CONFIRMADO futuro: mover, cancelar y atendido', () => {
    expect(habilitadas('CONFIRMADO', antes)).toEqual(['mover', 'cancelar', 'atendido']);
  });

  it('PENDIENTE_CONFIRMACION: solo cancelar', () => {
    expect(habilitadas('PENDIENTE_CONFIRMACION', antes)).toEqual(['cancelar']);
    expect(accionesHabilitadas(turnoEn('PENDIENTE_CONFIRMACION'), antes, 15).map((a) => a.accion)).toEqual(['cancelar']);
  });

  it.each(['CANCELADO', 'EXPIRADO', 'ATENDIDO'] as const)('%s: ninguna acción', (estado) => {
    expect(accionesHabilitadas(turnoEn(estado), antes, 15)).toEqual([]);
  });

  it('mover desaparece cuando el inicio ya pasó (y justo en el inicio)', () => {
    expect(habilitadas('CONFIRMADO', en(INICIO + 10 * MIN))).not.toContain('mover');
    expect(habilitadas('CONFIRMADO', en(INICIO))).not.toContain('mover');
  });

  it('ausente a las 09:14 con tolerancia 15: visible, deshabilitado y con el horario desde el que aplica', () => {
    const ausente = accionesHabilitadas(turnoEn('RESERVADO'), en(INICIO + 14 * MIN), 15).find((a) => a.accion === 'ausente');
    expect(ausente).toEqual({ accion: 'ausente', etiqueta: 'Marcar ausente', habilitada: false, motivo: 'Disponible desde las 09:15' });
  });

  it('ausente a las 09:15 con tolerancia 15: habilitado', () => {
    expect(habilitadas('RESERVADO', en(INICIO + 15 * MIN))).toContain('ausente');
  });

  it('con tolerancia 0 (fallback) ausente se habilita desde el inicio', () => {
    expect(habilitadas('CONFIRMADO', en(INICIO), 0)).toContain('ausente');
    expect(habilitadas('CONFIRMADO', en(INICIO - MIN), 0)).not.toContain('ausente');
  });

  it('revertir: a las 23 h 59 min sí, a las 24 h no', () => {
    const marcado = '2026-11-24T12:20:00Z';
    const m = Date.parse(marcado);
    expect(habilitadas('AUSENTE', en(m + (24 * 60 - 1) * MIN), 15, marcado)).toEqual(['revertir']);
    expect(accionesHabilitadas(turnoEn('AUSENTE', { ausenteMarcadoEn: marcado }), en(m + 24 * 60 * MIN), 15)).toEqual([]);
  });

  it('revertir: sin ausenteMarcadoEn no se ofrece', () => {
    expect(accionesHabilitadas(turnoEn('AUSENTE'), en(INICIO), 15)).toEqual([]);
  });

  it('etiquetas de las acciones', () => {
    const etiquetas = accionesHabilitadas(turnoEn('RESERVADO'), en(INICIO - MIN), 15).map((a) => a.etiqueta);
    expect(etiquetas).toEqual(['Mover', 'Cancelar', 'Marcar atendido', 'Marcar ausente']);
  });
});
