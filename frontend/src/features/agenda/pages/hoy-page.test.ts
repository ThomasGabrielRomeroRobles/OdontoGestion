import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RouteContext } from '../../../shared/router/router';
import type { AgendaApi } from '../agenda-api';
import { crearAgendaDepsDoble, type AgendaDepsDoble } from '../deps-test-helpers';
import type { AgendaDeps } from '../deps';
import { errorRed, flush, turnoEn, turnoFicticio } from '../test-fixtures';
import { createHoyPage } from './hoy-page';
import { createTurnoDetalle } from './turno-detalle';

describe('vista /agenda/hoy (8.1, US-024)', () => {
  let container: HTMLElement;
  let deps: AgendaDepsDoble & AgendaDeps;

  beforeEach(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    container = document.createElement('main');
    document.body.replaceChildren(container);
    deps = crearAgendaDepsDoble(new Date('2026-11-24T13:00:00Z'));
  });

  async function abrir(): Promise<void> {
    history.replaceState(null, '', '/agenda/hoy');
    const context: RouteContext = { url: new URL(location.href), navigate: vi.fn() };
    createHoyPage(deps, createTurnoDetalle(deps))(container, context);
    await flush();
  }

  const items = (): HTMLElement[] => [...container.querySelectorAll<HTMLElement>('[data-turno-id]')];

  it('lista los turnos de hoy con hora, paciente, prestación y estado', async () => {
    deps.api.turnosHoy.mockResolvedValue([
      turnoFicticio({ id: 1 }),
      turnoFicticio({
        id: 2,
        inicio: '2026-11-24T15:00:00Z',
        fin: '2026-11-24T15:30:00Z',
        estado: 'CONFIRMADO',
        paciente: { id: 8, nombre: 'Tomás', apellido: 'Ríos', dni: '40999888', cantidadAusencias: 0 },
        prestacion: { id: 1, nombre: 'Consulta', duracionMin: 30 },
      }),
    ]);
    await abrir();
    expect(container.querySelector('h1')?.textContent).toBe('Turnos de hoy');
    expect(container.textContent).toContain('Martes 24/11/2026');
    expect(items().map((i) => i.textContent)).toEqual([
      expect.stringContaining('09:00–09:45 Gómez, Lucía · Limpieza Reservado'),
      expect.stringContaining('12:00–12:30 Ríos, Tomás · Consulta Confirmado'),
    ]);
    expect(container.querySelector('a[href="/agenda"]')).not.toBeNull();
  });

  it('sin turnos: "No hay turnos para hoy"', async () => {
    await abrir();
    expect(container.textContent).toContain('No hay turnos para hoy');
  });

  it('acción desde hoy: marcar atendido recarga la lista y muestra "Atendido"', async () => {
    const hoy = vi
      .fn<AgendaApi['turnosHoy']>()
      .mockResolvedValueOnce([turnoEn('CONFIRMADO')])
      .mockResolvedValue([turnoEn('ATENDIDO')]);
    deps.api.turnosHoy = hoy;
    deps.api.marcarAtendido.mockResolvedValue(turnoEn('ATENDIDO'));
    await abrir();
    items()[0]!.querySelector('button')!.click();
    await flush();
    [...container.querySelectorAll<HTMLButtonElement>('[data-acciones] button')].find((b) => b.textContent === 'Marcar atendido')!.click();
    await flush();
    expect(hoy).toHaveBeenCalledTimes(2);
    expect(container.querySelector('[data-turno-id="41"] [data-estado]')?.textContent).toBe('Atendido');
  });

  it('falla de red: mensaje y Reintentar', async () => {
    deps.api.turnosHoy.mockRejectedValueOnce(errorRed).mockResolvedValue([turnoFicticio()]);
    await abrir();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe('No se pudo conectar. Reintentá.');
    [...container.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent === 'Reintentar')!.click();
    await flush();
    expect(items()).toHaveLength(1);
  });
});
