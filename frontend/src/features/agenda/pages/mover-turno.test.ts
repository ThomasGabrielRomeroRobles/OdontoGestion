import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Turno } from '../agenda-api';
import { crearAgendaDepsDoble, type AgendaDepsDoble } from '../deps-test-helpers';
import type { AgendaDeps } from '../deps';
import type { ErrorTraducido } from '../errores';
import { errorHttp, flush, turnoEn, turnoFicticio } from '../test-fixtures';
import { createMoverTurno } from './mover-turno';
import { createTurnoDetalle } from './turno-detalle';

describe('mover turno (grupo 7, US-022)', () => {
  let host: HTMLElement;
  let deps: AgendaDepsDoble & AgendaDeps;
  let alMovido: ReturnType<typeof vi.fn<(t: Turno) => void>>;
  let alVolver: ReturnType<typeof vi.fn<() => void>>;
  let alError: ReturnType<typeof vi.fn<(e: ErrorTraducido) => void>>;

  beforeEach(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    host = document.createElement('section');
    document.body.replaceChildren(host);
    deps = crearAgendaDepsDoble(new Date('2026-11-23T13:00:00Z'));
    deps.api.disponibilidad.mockResolvedValue([
      { inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' },
      { inicio: '2026-11-24T14:00:00Z', fin: '2026-11-24T14:45:00Z' },
    ]);
    alMovido = vi.fn<(t: Turno) => void>();
    alVolver = vi.fn<() => void>();
    alError = vi.fn<(e: ErrorTraducido) => void>();
    host.append(createMoverTurno(deps)(turnoFicticio(), alMovido, alVolver, alError));
  });

  const boton = (texto: string): HTMLButtonElement => {
    const b = [...host.querySelectorAll<HTMLButtonElement>('button')].find((x) => x.textContent === texto);
    if (b === undefined) throw new Error(`no hay botón "${texto}"`);
    return b;
  };

  async function elegirFecha(dia: string): Promise<void> {
    const fecha = host.querySelector<HTMLInputElement>('input[name="fecha"]')!;
    fecha.value = dia;
    fecha.dispatchEvent(new Event('change'));
    await flush();
  }

  const elegir = (hora: string): void =>
    [...host.querySelectorAll<HTMLButtonElement>('[data-huecos] button')].find((b) => b.textContent === hora)!.click();

  it('pide los huecos con la prestación del turno (C-07 no tiene excluirTurnoId)', async () => {
    await elegirFecha('2026-11-24');
    expect(deps.api.disponibilidad).toHaveBeenCalledWith(3, '2026-11-24');
    expect(host.querySelector('select')).toBeNull();
  });

  it('movimiento exitoso: PUT con el inicio UTC del hueco elegido', async () => {
    await elegirFecha('2026-11-24');
    elegir('11:00');
    const movido = turnoFicticio({ inicio: '2026-11-24T14:00:00Z', fin: '2026-11-24T14:45:00Z' });
    deps.api.moverTurno.mockResolvedValue(movido);
    boton('Confirmar movimiento').click();
    await flush();
    expect(deps.api.moverTurno).toHaveBeenCalledWith(41, '2026-11-24T14:00:00Z');
    expect(alMovido).toHaveBeenCalledWith(movido);
  });

  it('409 con alternativas: mensaje, opciones y huecos refrescados', async () => {
    await elegirFecha('2026-11-24');
    elegir('10:00');
    deps.api.moverTurno.mockRejectedValue(
      errorHttp(409, { extensions: { alternativas: [{ inicio: '2026-11-24T15:00:00Z', fin: '2026-11-24T15:45:00Z' }] } }),
    );
    boton('Confirmar movimiento').click();
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Ese horario ya no está disponible.');
    expect([...host.querySelectorAll('[data-alternativas] button')].map((b) => b.textContent)).toEqual(['12:00']);
    expect(deps.api.disponibilidad).toHaveBeenCalledTimes(2);
  });

  it('sin hueco elegido no envía', async () => {
    boton('Confirmar movimiento').click();
    await flush();
    expect(deps.api.moverTurno).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Elegí fecha y horario.');
  });

  it('422: muestra el mensaje y lo informa al detalle', async () => {
    await elegirFecha('2026-11-24');
    elegir('10:00');
    deps.api.moverTurno.mockRejectedValue(errorHttp(422, { detail: 'Fuera del horario de atención' }));
    boton('Confirmar movimiento').click();
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Fuera del horario de atención');
    expect(alError).toHaveBeenCalledWith(expect.objectContaining({ recargar: true }));
  });

  it('Volver no envía nada', () => {
    boton('Volver').click();
    expect(alVolver).toHaveBeenCalled();
    expect(deps.api.moverTurno).not.toHaveBeenCalled();
  });

  it('integrado al detalle: Mover abre el subpanel y al mover recarga la agenda', async () => {
    const detalleHost = document.createElement('section');
    document.body.append(detalleHost);
    const alCambiar = vi.fn();
    createTurnoDetalle(deps, { mover: createMoverTurno(deps) })(detalleHost, turnoEn('RESERVADO'), alCambiar);
    await flush();
    [...detalleHost.querySelectorAll<HTMLButtonElement>('[data-acciones] button')].find((b) => b.textContent === 'Mover')!.click();
    const fecha = detalleHost.querySelector<HTMLInputElement>('input[name="fecha"]')!;
    fecha.value = '2026-11-24';
    fecha.dispatchEvent(new Event('change'));
    await flush();
    [...detalleHost.querySelectorAll<HTMLButtonElement>('[data-huecos] button')].find((b) => b.textContent === '11:00')!.click();
    deps.api.moverTurno.mockResolvedValue(turnoFicticio({ inicio: '2026-11-24T14:00:00Z', fin: '2026-11-24T14:45:00Z' }));
    [...detalleHost.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent === 'Confirmar movimiento')!.click();
    await flush();
    expect(detalleHost.textContent).toContain('24/11/2026 11:00–11:45');
    expect(alCambiar).toHaveBeenCalledWith({ cerrar: false });
  });
});
