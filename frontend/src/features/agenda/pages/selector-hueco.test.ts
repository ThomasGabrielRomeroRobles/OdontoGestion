import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Prestacion } from '../../config/agenda-config-api';
import type { AgendaApi, Hueco } from '../agenda-api';
import { prestacionesAgenda } from '../deps-test-helpers';
import { flush } from '../test-fixtures';
import { crearSelectorHueco, type SelectorHueco } from './selector-hueco';

const h = (inicio: string, fin: string): Hueco => ({ inicio, fin });

describe('selector "fecha → huecos" (6.2)', () => {
  let disponibilidad: ReturnType<typeof vi.fn<AgendaApi['disponibilidad']>>;
  let selector: SelectorHueco;

  beforeEach(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    disponibilidad = vi
      .fn<AgendaApi['disponibilidad']>()
      .mockResolvedValue([h('2026-11-24T12:00:00Z', '2026-11-24T12:45:00Z'), h('2026-11-24T13:00:00Z', '2026-11-24T13:45:00Z')]);
  });

  function conLista(): Promise<void> {
    selector = crearSelectorHueco({
      disponibilidad,
      prestaciones: { tipo: 'lista', cargar: () => Promise.resolve<Prestacion[]>([...prestacionesAgenda]) },
    });
    document.body.replaceChildren(selector.elemento);
    return flush();
  }

  const select = (): HTMLSelectElement => selector.elemento.querySelector<HTMLSelectElement>('select[name="prestacionId"]')!;
  const fecha = (): HTMLInputElement => selector.elemento.querySelector<HTMLInputElement>('input[name="fecha"]')!;
  const huecos = (): string[] =>
    [...selector.elemento.querySelectorAll<HTMLButtonElement>('[data-huecos] button')].map((b) => b.textContent ?? '');

  async function elegir(prestacionId: string, dia: string): Promise<void> {
    select().value = prestacionId;
    select().dispatchEvent(new Event('change'));
    fecha().value = dia;
    fecha().dispatchEvent(new Event('change'));
    await flush();
  }

  it('solo ofrece prestaciones activas', async () => {
    await conLista();
    const opciones = [...select().options].filter((o) => o.value !== '').map((o) => o.textContent);
    expect(opciones).toEqual(['Consulta (30 min)', 'Limpieza (45 min)']);
  });

  it('con prestación y fecha pide la disponibilidad y muestra los huecos en hora del consultorio', async () => {
    await conLista();
    await elegir('3', '2026-11-24');
    expect(disponibilidad).toHaveBeenLastCalledWith(3, '2026-11-24');
    expect(huecos()).toEqual(['09:00', '10:00']);
  });

  it('sin huecos: "No hay horarios libres para esa fecha"', async () => {
    disponibilidad.mockResolvedValue([]);
    await conLista();
    await elegir('3', '2026-11-24');
    expect(selector.elemento.textContent).toContain('No hay horarios libres para esa fecha');
  });

  it('elegir un hueco lo deja seleccionado (aria-pressed)', async () => {
    await conLista();
    await elegir('3', '2026-11-24');
    const [primero] = selector.elemento.querySelectorAll<HTMLButtonElement>('[data-huecos] button');
    primero!.click();
    expect(selector.seleccion()).toEqual({ prestacionId: 3, hueco: h('2026-11-24T12:00:00Z', '2026-11-24T12:45:00Z') });
    expect(primero!.getAttribute('aria-pressed')).toBe('true');
  });

  it('alternativas: se muestran como opciones y se pueden elegir', async () => {
    await conLista();
    await elegir('3', '2026-11-24');
    selector.mostrarAlternativas([h('2026-11-24T14:00:00Z', '2026-11-24T14:45:00Z')]);
    const alt = selector.elemento.querySelector<HTMLButtonElement>('[data-alternativas] button')!;
    expect(alt.textContent).toBe('11:00');
    alt.click();
    expect(selector.seleccion()?.hueco.inicio).toBe('2026-11-24T14:00:00Z');
  });

  it('prestación fija (para mover): pide solo prestación y fecha', async () => {
    selector = crearSelectorHueco({ disponibilidad, prestaciones: { tipo: 'fija', id: 3 } });
    document.body.replaceChildren(selector.elemento);
    expect(selector.elemento.querySelector('select')).toBeNull();
    fecha().value = '2026-11-24';
    fecha().dispatchEvent(new Event('change'));
    await flush();
    expect(disponibilidad).toHaveBeenLastCalledWith(3, '2026-11-24');
  });

  it('refrescar vuelve a pedir y limpia la selección', async () => {
    await conLista();
    await elegir('3', '2026-11-24');
    selector.elemento.querySelector<HTMLButtonElement>('[data-huecos] button')!.click();
    await selector.refrescar();
    expect(disponibilidad).toHaveBeenCalledTimes(2);
    expect(selector.seleccion()).toBeNull();
  });
});
