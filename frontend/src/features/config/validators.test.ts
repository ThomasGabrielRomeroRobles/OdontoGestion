import { describe, expect, it } from 'vitest';
import {
  validarDuracion,
  validarNombrePrestacion,
  validarParametro,
  validarPasswordNueva,
  validarRangoHorario,
  buscarSolapamiento,
} from './validators';

describe('validarDuracion (6.1)', () => {
  it.each([
    ['', 15, 'La duración es obligatoria.'],
    ['  ', 15, 'La duración es obligatoria.'],
    ['30.5', 15, 'La duración debe ser un número entero de minutos.'],
    ['abc', 15, 'La duración debe ser un número entero de minutos.'],
    ['0', 15, 'La duración debe ser mayor que cero.'],
    ['-15', 15, 'La duración debe ser mayor que cero.'],
    ['40', 15, 'La duración debe ser múltiplo de 15 minutos.'],
    ['45', 30, 'La duración debe ser múltiplo de 30 minutos.'],
  ])('"%s" con granularidad %i → error', (valor, granularidad, mensaje) => {
    expect(validarDuracion(valor, granularidad)).toEqual({ ok: false, error: mensaje });
  });

  it('válida devuelve el número', () => {
    expect(validarDuracion('60', 15)).toEqual({ ok: true, value: 60 });
    expect(validarDuracion(' 90 ', 30)).toEqual({ ok: true, value: 90 });
  });

  it('granularidad desconocida: solo exige entero positivo (D8)', () => {
    expect(validarDuracion('40', null)).toEqual({ ok: true, value: 40 });
    expect(validarDuracion('0', null)).toEqual({ ok: false, error: 'La duración debe ser mayor que cero.' });
  });
});

describe('validarRangoHorario y buscarSolapamiento (6.2)', () => {
  it('rechaza horas mal formadas', () => {
    expect(validarRangoHorario('9', '13:00')).toEqual({ ok: false, campo: 'horaDesde', error: 'Ingresá una hora válida (HH:mm).' });
    expect(validarRangoHorario('09:00', '25:00')).toEqual({ ok: false, campo: 'horaHasta', error: 'Ingresá una hora válida (HH:mm).' });
  });

  it('rechaza fin no posterior al inicio', () => {
    const esperado = { ok: false, campo: 'horaHasta', error: 'La hora de fin debe ser posterior a la de inicio.' };
    expect(validarRangoHorario('14:00', '13:00')).toEqual(esperado);
    expect(validarRangoHorario('13:00', '13:00')).toEqual(esperado);
  });

  it('acepta rango válido', () => {
    expect(validarRangoHorario('09:00', '13:00')).toEqual({ ok: true });
  });

  const lunes = [
    { id: 1, diaSemana: 1, horaDesde: '09:00', horaHasta: '13:00' },
    { id: 2, diaSemana: 1, horaDesde: '14:00', horaHasta: '18:00' },
    { id: 3, diaSemana: 2, horaDesde: '09:00', horaHasta: '13:00' },
  ] as const;

  it('detecta solapamiento en el mismo día y nombra la franja', () => {
    expect(buscarSolapamiento({ diaSemana: 1, horaDesde: '12:00', horaHasta: '15:00' }, lunes)).toEqual(lunes[0]);
  });

  it('intervalos semiabiertos: contiguos no se solapan', () => {
    expect(buscarSolapamiento({ diaSemana: 1, horaDesde: '13:00', horaHasta: '14:00' }, lunes)).toBeNull();
  });

  it('otro día no se solapa', () => {
    expect(buscarSolapamiento({ diaSemana: 3, horaDesde: '09:00', horaHasta: '13:00' }, lunes)).toBeNull();
  });

  it('en edición excluye la propia franja', () => {
    expect(buscarSolapamiento({ diaSemana: 1, horaDesde: '08:00', horaHasta: '13:30' }, lunes, 1)).toBeNull();
    expect(buscarSolapamiento({ diaSemana: 1, horaDesde: '12:00', horaHasta: '18:00' }, lunes, 2)).toEqual(lunes[0]);
  });
});

describe('validarParametro, nombre y contraseña (6.3)', () => {
  it('parámetro: entero mayor que cero', () => {
    expect(validarParametro('0')).toEqual({ ok: false, error: 'Debe ser un número entero mayor que cero.' });
    expect(validarParametro('')).toEqual({ ok: false, error: 'Debe ser un número entero mayor que cero.' });
    expect(validarParametro('1.5')).toEqual({ ok: false, error: 'Debe ser un número entero mayor que cero.' });
    expect(validarParametro('48')).toEqual({ ok: true, value: 48 });
  });

  it('nombre de prestación obligatorio y recortado', () => {
    expect(validarNombrePrestacion('   ')).toEqual({ ok: false, error: 'El nombre es obligatorio.' });
    expect(validarNombrePrestacion(' Blanqueamiento ')).toEqual({ ok: true, value: 'Blanqueamiento' });
  });

  it('contraseña nueva: obligatoria y coincidente', () => {
    expect(validarPasswordNueva('', '')).toEqual({ ok: false, error: 'La nueva contraseña es obligatoria.' });
    expect(validarPasswordNueva('abc12345', 'abc1234')).toEqual({ ok: false, error: 'Las contraseñas no coinciden.' });
    expect(validarPasswordNueva('abc12345', 'abc12345')).toEqual({ ok: true, value: 'abc12345' });
  });
});
