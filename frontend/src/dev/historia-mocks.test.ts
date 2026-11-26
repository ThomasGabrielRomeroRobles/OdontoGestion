import { describe, expect, it } from 'vitest';
import { crearHistoriaApiMock } from './historia-mocks';

describe('historia-mocks', () => {
  it('arranca con ficha vacía de 32 piezas y guarda en memoria', async () => {
    let ahora = new Date('2026-11-26T13:00:00Z');
    const api = crearHistoriaApiMock(() => ahora);

    const vacia = await api.obtener(7);
    expect(vacia.piezas).toHaveLength(32);
    expect(vacia.evoluciones).toEqual([]);

    await api.guardarAnamnesis(7, { ...vacia.anamnesis, alergias: 'Penicilina' });
    await api.guardarPieza(7, 16, { estado: 'CARIES', observacion: '' });
    const e = await api.crearEvolucion(7, { nota: 'Control' });

    const ficha = await api.obtener(7);
    expect(ficha.anamnesis.alergias).toBe('Penicilina');
    expect(ficha.piezas.find((p) => p.fdi === 16)?.estado).toBe('CARIES');
    expect(ficha.evoluciones.map((x) => x.id)).toEqual([e.id]);

    ahora = new Date('2026-11-28T13:00:00Z');
    await expect(api.editarEvolucion(7, e.id, { nota: 'Tarde' })).rejects.toMatchObject({ status: 422 });
    expect((await api.listarEvoluciones(7))[0]?.editable).toBe(false);
  });

  it('rechaza piezas fuera de las permanentes y pacientes inexistentes', async () => {
    const api = crearHistoriaApiMock();
    await expect(api.guardarPieza(7, 51, { estado: 'SANA', observacion: '' })).rejects.toMatchObject({ status: 422 });
    await expect(api.obtener(999_999)).rejects.toMatchObject({ status: 404 });
  });
});
