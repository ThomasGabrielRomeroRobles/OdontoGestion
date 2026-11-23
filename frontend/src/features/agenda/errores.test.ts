import { describe, expect, it } from 'vitest';
import { traducirErrorAccion } from './errores';
import { errorHttp, errorRed } from './test-fixtures';

const alt = { inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' };

describe('traducirErrorAccion (3.4, design D7)', () => {
  it('409 con alternativas: mensaje por defecto y alternativas', () => {
    expect(traducirErrorAccion(errorHttp(409, { extensions: { alternativas: [alt] } }))).toEqual({
      mensaje: 'Ese horario ya no está disponible.',
      alternativas: [alt],
      conflicto: true,
      recargar: false,
      cerrar: false,
    });
  });

  it('409 usa el detail si viene', () => {
    expect(traducirErrorAccion(errorHttp(409, { detail: 'Ocupado', extensions: { alternativas: [] } })).mensaje).toBe('Ocupado');
  });

  it('409 descarta alternativas mal formadas', () => {
    const extensions = { alternativas: [alt, { inicio: 3 }, 'x', null, { inicio: 'a' }] };
    expect(traducirErrorAccion(errorHttp(409, { extensions })).alternativas).toEqual([alt]);
  });

  it('409 hueco-no-disponible con alternativas vacías (o no arreglo): conflicto con lista vacía', () => {
    expect(traducirErrorAccion(errorHttp(409, { extensions: { alternativas: [] } }))).toMatchObject({ conflicto: true, alternativas: [] });
    expect(traducirErrorAccion(errorHttp(409, { extensions: { alternativas: 'no' } })).alternativas).toEqual([]);
  });

  it('409 modificacion-concurrente (sin alternativas): no es conflicto de horario, recarga la agenda', () => {
    expect(traducirErrorAccion(errorHttp(409, { detail: 'Otro cambio sobre el turno se aplicó antes.' }))).toEqual({
      mensaje: 'Otro cambio se aplicó antes sobre este turno. Se recargó la agenda.',
      alternativas: [],
      conflicto: false,
      recargar: true,
      cerrar: false,
    });
  });

  it('422: usa detail, si no title, y marca recargar', () => {
    expect(traducirErrorAccion(errorHttp(422, { detail: 'Transición inválida' }))).toEqual({
      mensaje: 'Transición inválida',
      alternativas: [],
      conflicto: false,
      recargar: true,
      cerrar: false,
    });
    expect(traducirErrorAccion(errorHttp(422, { title: 'Regla incumplida' })).mensaje).toBe('Regla incumplida');
  });

  it('404: el turno ya no existe, cierra y recarga', () => {
    expect(traducirErrorAccion(errorHttp(404))).toMatchObject({ mensaje: 'El turno ya no existe.', cerrar: true, recargar: true });
  });

  it('red: mensaje de conexión, sin recargar', () => {
    expect(traducirErrorAccion(errorRed)).toMatchObject({ mensaje: 'No se pudo conectar. Reintentá.', recargar: false, cerrar: false });
  });

  it('otro error HTTP: detail o title, sin recargar', () => {
    expect(traducirErrorAccion(errorHttp(500, { title: 'Error interno' }))).toMatchObject({ mensaje: 'Error interno', recargar: false });
  });

  it('algo que no es ApiError: mensaje genérico', () => {
    expect(traducirErrorAccion(new Error('x')).mensaje).toBe('Ocurrió un error inesperado.');
  });
});
