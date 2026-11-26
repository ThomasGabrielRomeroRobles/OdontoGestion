import { describe, expect, it } from 'vitest';
import {
  ESTADOS_PIEZA,
  FILA_INFERIOR,
  FILA_SUPERIOR,
  PIEZAS_PERMANENTES,
  claseEstado,
  etiquetaEstado,
  fechaCivil,
  hoyEnConsultorio,
} from './modelo';

describe('modelo de la historia clínica', () => {
  it('ordena las filas del odontograma por cuadrante FDI', () => {
    expect(FILA_SUPERIOR).toEqual([18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28]);
    expect(FILA_INFERIOR).toEqual([48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38]);
    expect(PIEZAS_PERMANENTES).toHaveLength(32);
    expect(new Set([...FILA_SUPERIOR, ...FILA_INFERIOR])).toEqual(new Set(PIEZAS_PERMANENTES));
  });

  it('tiene etiqueta y clase para los 9 estados y para "sin registrar"', () => {
    expect(ESTADOS_PIEZA).toHaveLength(9);
    expect(etiquetaEstado('CARIES')).toBe('Caries');
    expect(etiquetaEstado('A_EXTRAER')).toBe('A extraer');
    expect(etiquetaEstado(null)).toBe('Sin registrar');
    expect(claseEstado('OBTURADA')).toBe('pieza--obturada');
    expect(claseEstado(null)).toBe('pieza--sin-registrar');
  });

  it('calcula hoy en la zona del consultorio y formatea fechas civiles', () => {
    expect(hoyEnConsultorio(new Date('2026-11-27T02:00:00Z'))).toBe('2026-11-26');
    expect(hoyEnConsultorio(new Date('2026-11-27T03:00:00Z'))).toBe('2026-11-27');
    expect(fechaCivil('2026-11-05')).toBe('05/11/2026');
  });
});
