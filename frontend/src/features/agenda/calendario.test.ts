import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  agruparPorDia,
  diasDeSemana,
  etiquetaDia,
  fechaLarga,
  fechaLocal,
  horaLocal,
  inicioSemana,
  parsearVistaYFecha,
  rangoDia,
  rangoSemana,
  sumarDias,
} from './calendario';
import { turnoFicticio } from './test-fixtures';

describe('calendario en zona del consultorio (3.3)', () => {
  beforeEach(() => vi.stubEnv('TZ', 'Asia/Tokyo'));
  afterEach(() => vi.unstubAllEnvs());

  it('rangoDia del 24/11 va de 03:00Z a 03:00Z del día siguiente', () => {
    expect(rangoDia('2026-11-24')).toEqual({ desde: '2026-11-24T03:00:00Z', hasta: '2026-11-25T03:00:00Z' });
  });

  it('la semana del miércoles 25/11 empieza el lunes 23 y termina el domingo 29', () => {
    expect(inicioSemana('2026-11-25')).toBe('2026-11-23');
    expect(inicioSemana('2026-11-23')).toBe('2026-11-23');
    expect(inicioSemana('2026-11-29')).toBe('2026-11-23');
    expect(diasDeSemana('2026-11-25')).toEqual([
      '2026-11-23',
      '2026-11-24',
      '2026-11-25',
      '2026-11-26',
      '2026-11-27',
      '2026-11-28',
      '2026-11-29',
    ]);
    expect(rangoSemana('2026-11-25')).toEqual({ desde: '2026-11-23T03:00:00Z', hasta: '2026-11-30T03:00:00Z' });
  });

  it('sumarDias cruza meses y años', () => {
    expect(sumarDias('2026-11-30', 1)).toBe('2026-12-01');
    expect(sumarDias('2026-12-28', 7)).toBe('2027-01-04');
    expect(sumarDias('2026-11-23', -1)).toBe('2026-11-22');
  });

  it('2026-11-25T01:00Z cae el martes 24 a las 22:00', () => {
    expect(fechaLocal('2026-11-25T01:00:00Z')).toBe('2026-11-24');
    expect(horaLocal('2026-11-25T01:00:00Z')).toBe('22:00');
  });

  it('fechaLocal acepta un Date (hoy del consultorio)', () => {
    expect(fechaLocal(new Date('2026-11-25T13:00:00Z'))).toBe('2026-11-25');
  });

  it('etiquetaDia muestra día de la semana y fecha corta', () => {
    expect(etiquetaDia('2026-11-23')).toBe('Lun 23/11');
    expect(etiquetaDia('2026-11-29')).toBe('Dom 29/11');
  });

  it('fechaLarga: día de la semana completo y fecha', () => {
    expect(fechaLarga('2026-11-24')).toBe('Martes 24/11/2026');
  });

  it('parsearVistaYFecha: valores válidos se respetan', () => {
    expect(parsearVistaYFecha(new URLSearchParams('vista=dia&fecha=2026-11-24'), '2026-11-25')).toEqual({
      vista: 'dia',
      fecha: '2026-11-24',
      normalizado: false,
    });
  });

  it('parsearVistaYFecha: vista=mes&fecha=2026-13-40 → semana y hoy, normalizado', () => {
    expect(parsearVistaYFecha(new URLSearchParams('vista=mes&fecha=2026-13-40'), '2026-11-25')).toEqual({
      vista: 'semana',
      fecha: '2026-11-25',
      normalizado: true,
    });
  });

  it('parsearVistaYFecha: sin parámetros → semana y hoy, normalizado', () => {
    expect(parsearVistaYFecha(new URLSearchParams(''), '2026-11-25')).toEqual({
      vista: 'semana',
      fecha: '2026-11-25',
      normalizado: true,
    });
  });

  it('parsearVistaYFecha: 31 de febrero es inválido', () => {
    expect(parsearVistaYFecha(new URLSearchParams('vista=dia&fecha=2026-02-31'), '2026-11-25').fecha).toBe('2026-11-25');
  });

  it('agruparPorDia agrupa por día del consultorio y ordena por inicio', () => {
    const t1 = turnoFicticio({ id: 1, inicio: '2026-11-24T14:00:00Z' });
    const t2 = turnoFicticio({ id: 2, inicio: '2026-11-24T12:00:00Z' });
    const t3 = turnoFicticio({ id: 3, inicio: '2026-11-25T01:00:00Z' });
    const t4 = turnoFicticio({ id: 4, inicio: '2026-11-25T12:00:00Z' });
    const grupos = agruparPorDia([t1, t2, t3, t4]);
    expect(grupos.get('2026-11-24')?.map((t) => t.id)).toEqual([2, 1, 3]);
    expect(grupos.get('2026-11-25')?.map((t) => t.id)).toEqual([4]);
  });
});
