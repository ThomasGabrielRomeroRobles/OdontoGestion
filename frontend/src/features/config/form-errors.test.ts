import { describe, expect, it } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { quitarPrefijoCampos, toFormErrors, NETWORK_MESSAGE } from './form-errors';

describe('quitarPrefijoCampos (D10)', () => {
  it('quita el prefijo de los campos anidados y conserva los planos', () => {
    const errores = toFormErrors({
      kind: 'http',
      status: 422,
      title: 'Regla de negocio incumplida',
      fieldErrors: [
        { field: 'parametros.granularidadMin', message: 'Debe ser mayor que cero.' },
        { field: 'parametros.toleranciaAusenteMin', message: 'Debe ser mayor que cero.' },
        { field: 'nombre', message: 'Es obligatorio.' },
      ],
      extensions: {},
    });
    expect(quitarPrefijoCampos(errores, 'parametros.')).toEqual({
      byField: {
        granularidadMin: 'Debe ser mayor que cero.',
        toleranciaAusenteMin: 'Debe ser mayor que cero.',
        nombre: 'Es obligatorio.',
      },
      general: null,
    });
  });

  it('solo quita el prefijo al inicio y deja intacto el error general', () => {
    const errores = { byField: { 'otro.parametros.x': 'm' }, general: 'g' };
    expect(quitarPrefijoCampos(errores, 'parametros.')).toEqual(errores);
  });

  it('si el campo plano ya tiene error, ese mensaje gana', () => {
    const errores = { byField: { granularidadMin: 'plano', 'parametros.granularidadMin': 'anidado' }, general: null };
    expect(quitarPrefijoCampos(errores, 'parametros.').byField).toEqual({ granularidadMin: 'plano' });
  });
});

describe('toFormErrors', () => {
  it('422 con errores de campo: los agrupa por campo y no hay error general', () => {
    const error: ApiError = {
      kind: 'http',
      status: 422,
      title: 'Datos inválidos',
      fieldErrors: [
        { field: 'duracionMin', message: 'Debe ser múltiplo de 15' },
        { field: 'nombre', message: 'Ya existe' },
      ],
      extensions: {},
    };
    expect(toFormErrors(error)).toEqual({
      byField: { duracionMin: 'Debe ser múltiplo de 15', nombre: 'Ya existe' },
      general: null,
    });
  });

  it('error sin campos: usa el detalle si existe, si no el título', () => {
    expect(toFormErrors({ kind: 'http', status: 409, title: 'Conflicto', fieldErrors: [], extensions: {} })).toEqual({
      byField: {},
      general: 'Conflicto',
    });
    expect(
      toFormErrors({ kind: 'http', status: 409, title: 'Conflicto', detail: 'Se superpone', fieldErrors: [], extensions: {} }),
    ).toEqual({ byField: {}, general: 'Se superpone' });
  });

  it('falla de red: mensaje de conexión', () => {
    expect(toFormErrors({ kind: 'network' })).toEqual({ byField: {}, general: NETWORK_MESSAGE });
  });

  it('valor desconocido: mensaje genérico', () => {
    expect(toFormErrors(new Error('x'))).toEqual({ byField: {}, general: 'Ocurrió un error inesperado.' });
  });

  it('el primer mensaje por campo gana', () => {
    const error: ApiError = {
      kind: 'http',
      status: 422,
      title: 'Datos inválidos',
      fieldErrors: [
        { field: 'nombre', message: 'Obligatorio' },
        { field: 'nombre', message: 'Muy largo' },
      ],
      extensions: {},
    };
    expect(toFormErrors(error).byField).toEqual({ nombre: 'Obligatorio' });
  });
});
