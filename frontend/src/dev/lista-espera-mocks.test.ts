import { describe, expect, it } from 'vitest';
import type { InscripcionListaEspera } from '../features/lista-espera/lista-espera-api';
import { inscripcionValida } from '../features/lista-espera/test-fixtures';
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

  describe('inscribir: mismos rechazos que el backend (C-10)', () => {
    /** 12:00 del lunes 23/11/2026 en el consultorio. */
    const ahora = (): Date => new Date('2026-11-23T15:00:00Z');
    const api = crearListaEsperaApiMock({ ahora });
    const con = (cambios: Partial<InscripcionListaEspera>): InscripcionListaEspera => ({ ...inscripcionValida, prestacionId: 1, ...cambios });

    it('acepta una inscripción válida con el mensaje genérico', async () => {
      await expect(api.inscribir(con({ fechaDesde: '2026-11-23', fechaHasta: '2026-11-30' }))).resolves.toMatchObject({ tipo: 'aceptada' });
    });

    it('rango invertido → invalida en fechaHasta', async () => {
      await expect(api.inscribir(con({ fechaDesde: '2026-11-30', fechaHasta: '2026-11-24' }))).resolves.toEqual({
        tipo: 'invalida',
        fieldErrors: [{ field: 'fechaHasta', message: 'No puede ser anterior a la fecha desde.' }],
      });
    });

    it('fecha hasta anterior a hoy → invalida en fechaHasta', async () => {
      await expect(api.inscribir(con({ fechaDesde: '2026-11-20', fechaHasta: '2026-11-22' }))).resolves.toEqual({
        tipo: 'invalida',
        fieldErrors: [{ field: 'fechaHasta', message: 'No puede ser anterior a hoy.' }],
      });
    });

    it('prestación que no está entre las públicas → prestacionNoDisponible', async () => {
      await expect(api.inscribir(con({ prestacionId: 99, fechaDesde: '2026-11-23', fechaHasta: '2026-11-30' }))).resolves.toEqual({
        tipo: 'prestacionNoDisponible',
      });
    });
  });
});
