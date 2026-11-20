import { describe, expect, it } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { toFormErrors, NETWORK_MESSAGE } from './form-errors';

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
    };
    expect(toFormErrors(error)).toEqual({
      byField: { duracionMin: 'Debe ser múltiplo de 15', nombre: 'Ya existe' },
      general: null,
    });
  });

  it('error sin campos: usa el detalle si existe, si no el título', () => {
    expect(toFormErrors({ kind: 'http', status: 409, title: 'Conflicto', fieldErrors: [] })).toEqual({
      byField: {},
      general: 'Conflicto',
    });
    expect(
      toFormErrors({ kind: 'http', status: 409, title: 'Conflicto', detail: 'Se superpone', fieldErrors: [] }),
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
    };
    expect(toFormErrors(error).byField).toEqual({ nombre: 'Obligatorio' });
  });
});
