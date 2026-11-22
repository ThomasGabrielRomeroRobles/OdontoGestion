import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgendaApi } from '../agenda-api';
import { errorRed, flush } from '../test-fixtures';
import { crearAusencias } from './ausencias';

describe('componente de ausencias (5.1, US-061)', () => {
  beforeEach(() => vi.stubEnv('TZ', 'Asia/Tokyo'));

  it('muestra cantidad y fechas en hora del consultorio, la más reciente primero', async () => {
    const api = vi.fn<AgendaApi['ausenciasPaciente']>().mockResolvedValue({
      cantidad: 2,
      ausencias: [
        { turnoId: 12, inicio: '2026-10-02T17:30:00Z', prestacionNombre: 'Control' },
        { turnoId: 30, inicio: '2026-11-10T12:00:00Z', prestacionNombre: 'Consulta' },
      ],
    });
    const { elemento } = crearAusencias(api, 7);
    await flush();
    expect(api).toHaveBeenCalledWith(7);
    expect(elemento.querySelector('[data-cantidad]')?.textContent).toBe('Ausencias: 2');
    expect([...elemento.querySelectorAll('li')].map((li) => li.textContent)).toEqual([
      '10/11/2026 09:00 — Consulta',
      '02/10/2026 14:30 — Control',
    ]);
  });

  it('sin ausencias: "Sin ausencias registradas"', async () => {
    const api = vi.fn<AgendaApi['ausenciasPaciente']>().mockResolvedValue({ cantidad: 0, ausencias: [] });
    const { elemento } = crearAusencias(api, 7);
    await flush();
    expect(elemento.textContent).toContain('Sin ausencias registradas');
    expect(elemento.querySelectorAll('li')).toHaveLength(0);
  });

  it('recargar vuelve a pedir y reemplaza el contenido', async () => {
    const api = vi
      .fn<AgendaApi['ausenciasPaciente']>()
      .mockResolvedValueOnce({ cantidad: 0, ausencias: [] })
      .mockResolvedValueOnce({ cantidad: 1, ausencias: [{ turnoId: 41, inicio: '2026-11-24T12:00:00Z', prestacionNombre: 'Limpieza' }] });
    const ausencias = crearAusencias(api, 7);
    await flush();
    await ausencias.recargar();
    expect(ausencias.elemento.querySelector('[data-cantidad]')?.textContent).toBe('Ausencias: 1');
  });

  it('si falla la carga lo informa', async () => {
    const api = vi.fn<AgendaApi['ausenciasPaciente']>().mockRejectedValue(errorRed);
    const { elemento } = crearAusencias(api, 7);
    await flush();
    expect(elemento.textContent).toContain('No se pudieron cargar las ausencias.');
  });
});
