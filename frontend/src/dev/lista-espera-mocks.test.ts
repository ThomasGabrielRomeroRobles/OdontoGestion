import { describe, expect, it } from 'vitest';
import { crearListaEsperaApiMock } from './lista-espera-mocks';

describe('mocks de desarrollo de la lista de espera', () => {
  it('los avisos de demostración resuelven su escenario y cualquier otro token da 410', async () => {
    const api = crearListaEsperaApiMock();
    await expect(api.obtenerAviso('aviso')).resolves.toMatchObject({ resumen: { disponible: true } });
    await expect(api.obtenerAviso('aviso-ocupado')).resolves.toMatchObject({ resumen: { disponible: false } });
    await expect(api.obtenerAviso('otro')).resolves.toEqual({ tipo: 'noDisponible' });
    await expect(api.aceptarAviso('aviso')).resolves.toMatchObject({ tipo: 'reservado' });
    await expect(api.aceptarAviso('aviso-ocupado')).resolves.toEqual({ tipo: 'noDisponible' });
    await expect(api.aceptarAviso('aviso-vencido')).resolves.toEqual({ tipo: 'vencido' });
  });

  it('retirar pasa la entrada a CANCELADA en el listado', async () => {
    const api = crearListaEsperaApiMock();
    await api.retirar(3);
    const entradas = await api.listar();
    expect(entradas.find((e) => e.id === 3)?.estado).toBe('CANCELADA');
    expect(entradas.find((e) => e.id === 1)?.estado).toBe('CUBIERTA');
  });
});
