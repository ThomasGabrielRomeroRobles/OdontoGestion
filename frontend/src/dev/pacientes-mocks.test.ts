import { describe, expect, it } from 'vitest';
import { crearPacientesApiMock } from './pacientes-mocks';

describe('crearPacientesApiMock', () => {
  const api = crearPacientesApiMock();
  it('lista, filtra por dni y por texto', async () => {
    expect(await api.buscar({})).toHaveLength(3);
    expect((await api.buscar({ dni: '30.111.222' }))[0]?.apellido).toBe('Gómez');
    expect(await api.buscar({ q: 'quispe' })).toHaveLength(1);
  });
  it('ausencias coincide con cantidadAusencias', async () => {
    expect((await api.ausencias(1)).ausencias).toHaveLength(2);
    expect((await api.ausencias(2)).cantidad).toBe(0);
  });
});
