import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Horario, HorarioInput } from '../agenda-config-api';
import { boton, enviar, error422, errorDe, escribir, horariosFicticios, input } from '../test-fixtures';
import { createHorariosSection, type HorariosSectionDeps } from './horarios-section';

describe('sección Horarios (9.2)', () => {
  let container: HTMLElement;
  let deps: { [K in keyof HorariosSectionDeps]: ReturnType<typeof vi.fn<HorariosSectionDeps[K]>> };

  beforeEach(async () => {
    container = document.createElement('div');
    deps = {
      listarHorarios: vi.fn<() => Promise<Horario[]>>().mockResolvedValue([...horariosFicticios]),
      crearHorario: vi.fn<(i: HorarioInput) => Promise<Horario>>(),
      actualizarHorario: vi.fn<(id: number, i: HorarioInput) => Promise<Horario>>(),
      eliminarHorario: vi.fn<(id: number) => Promise<void>>(),
      confirmar: vi.fn<(mensaje: string) => boolean>().mockReturnValue(true),
    };
    createHorariosSection(deps)(container);
    await vi.waitFor(() => expect(franjas(1)).toHaveLength(2));
  });

  const dia = (n: number): HTMLElement => {
    const el = container.querySelector<HTMLElement>(`[data-dia="${n}"]`);
    if (el === null) throw new Error(`sin día ${n}`);
    return el;
  };
  const franjas = (n: number): string[] =>
    [...dia(n).querySelectorAll('li[data-id] [data-col="rango"]')].map((s) => s.textContent ?? '');
  const franja = (n: number, rango: string): HTMLElement => {
    const li = [...dia(n).querySelectorAll<HTMLElement>('li[data-id]')].find(
      (el) => el.querySelector('[data-col="rango"]')?.textContent === rango,
    );
    if (li === undefined) throw new Error(`sin franja ${rango}`);
    return li;
  };
  const form = (): HTMLFormElement | null => container.querySelector('form[data-form="horario"]');

  function cargar(diaSemana: number, desde: string, hasta: string): void {
    const select = container.querySelector<HTMLSelectElement>('select[name="diaSemana"]');
    if (select === null) throw new Error('falta el selector de día');
    select.value = String(diaSemana);
    escribir(container, 'horaDesde', desde);
    escribir(container, 'horaHasta', hasta);
  }

  it('agrupa por día de lunes a domingo, en orden, y marca los días sin franjas', () => {
    expect([...container.querySelectorAll('[data-dia] h3')].map((h) => h.textContent)).toEqual([
      'Lunes',
      'Martes',
      'Miércoles',
      'Jueves',
      'Viernes',
      'Sábado',
      'Domingo',
    ]);
    expect(franjas(1)).toEqual(['09:00 – 13:00', '14:00 – 18:00']);
    expect(dia(3).textContent).toContain('Sin atención');
  });

  it('agregar: envía POST y la franja aparece en su día ordenada', async () => {
    deps.crearHorario.mockResolvedValue({ id: 30, diaSemana: 1, horaDesde: '07:00', horaHasta: '08:30' });
    cargar(1, '07:00', '08:30');
    enviar(form());

    await vi.waitFor(() => expect(franjas(1)).toEqual(['07:00 – 08:30', '09:00 – 13:00', '14:00 – 18:00']));
    expect(deps.crearHorario).toHaveBeenCalledWith({ diaSemana: 1, horaDesde: '07:00', horaHasta: '08:30' });
  });

  it('rango invertido: no envía', () => {
    cargar(6, '14:00', '13:00');
    enviar(form());
    expect(deps.crearHorario).not.toHaveBeenCalled();
    expect(errorDe(container, 'horaHasta')).toBe('La hora de fin debe ser posterior a la de inicio.');
  });

  it('solapamiento en el mismo día: no envía y nombra la franja en conflicto', () => {
    cargar(1, '12:00', '15:00');
    enviar(form());
    expect(deps.crearHorario).not.toHaveBeenCalled();
    expect(errorDe(container, 'horaDesde')).toBe('Se superpone con la franja 09:00 – 13:00.');
  });

  it('editar: no choca consigo misma y actualiza la franja', async () => {
    deps.actualizarHorario.mockResolvedValue({ id: 10, diaSemana: 1, horaDesde: '08:00', horaHasta: '13:00' });
    boton(franja(1, '09:00 – 13:00'), 'Editar').click();
    expect(input(container, 'horaDesde').value).toBe('09:00');
    escribir(container, 'horaDesde', '08:00');
    enviar(form());

    await vi.waitFor(() => expect(franjas(1)).toEqual(['08:00 – 13:00', '14:00 – 18:00']));
    expect(deps.actualizarHorario).toHaveBeenCalledWith(10, { diaSemana: 1, horaDesde: '08:00', horaHasta: '13:00' });
  });

  it('eliminar con confirmación: la franja desaparece', async () => {
    deps.eliminarHorario.mockResolvedValue(undefined);
    boton(franja(2, '09:00 – 13:00'), 'Eliminar').click();

    await vi.waitFor(() => expect(dia(2).textContent).toContain('Sin atención'));
    expect(deps.eliminarHorario).toHaveBeenCalledWith(12);
  });

  it('eliminar cancelado no llama a la API', () => {
    deps.confirmar.mockReturnValue(false);
    boton(franja(2, '09:00 – 13:00'), 'Eliminar').click();
    expect(deps.eliminarHorario).not.toHaveBeenCalled();
  });

  it('422 del servidor: error por campo', async () => {
    deps.crearHorario.mockRejectedValue(error422('horaDesde', 'Debe estar alineada a 15 minutos'));
    cargar(6, '09:10', '12:00');
    enviar(form());
    await vi.waitFor(() => expect(errorDe(container, 'horaDesde')).toBe('Debe estar alineada a 15 minutos'));
    expect(input(container, 'horaDesde').value).toBe('09:10');
  });
});
