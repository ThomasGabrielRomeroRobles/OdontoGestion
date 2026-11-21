import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { formatearInstante, instanteAInputLocal, localAInstante } from './zoned-time';

/** El navegador puede estar en cualquier zona: la conversión debe usar siempre la del consultorio. */
describe('zoned-time (6.4)', () => {
  beforeAll(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
  });
  afterAll(() => {
    vi.unstubAllEnvs();
  });

  it('el entorno de prueba no está en la zona del consultorio', () => {
    expect(new Date(Date.UTC(2026, 0, 1, 0, 0)).getHours()).toBe(9);
  });

  it('local de Buenos Aires → instante UTC', () => {
    expect(localAInstante('2026-12-24T00:00')).toBe('2026-12-24T03:00:00Z');
  });

  it('23:30 local cruza de día en UTC', () => {
    expect(localAInstante('2026-12-31T23:30')).toBe('2027-01-01T02:30:00Z');
  });

  it('instante UTC → valor de input local', () => {
    expect(instanteAInputLocal('2026-12-24T03:00:00Z')).toBe('2026-12-24T00:00');
    expect(instanteAInputLocal('2027-01-01T02:30:00Z')).toBe('2026-12-31T23:30');
  });

  it('formato de presentación dd/MM/yyyy HH:mm en hora del consultorio', () => {
    expect(formatearInstante('2026-12-24T03:00:00Z')).toBe('24/12/2026 00:00');
    expect(formatearInstante('2027-01-01T02:30:00Z')).toBe('31/12/2026 23:30');
  });

  it('valor local inválido → null', () => {
    expect(localAInstante('')).toBeNull();
    expect(localAInstante('2026-13-01T00:00')).toBeNull();
    expect(localAInstante('no-es-fecha')).toBeNull();
  });

  it('ida y vuelta conserva el valor', () => {
    const local = '2026-07-09T08:15';
    const instante = localAInstante(local);
    expect(instante).not.toBeNull();
    if (instante !== null) expect(instanteAInputLocal(instante)).toBe(local);
  });
});
