import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bloqueo, BloqueoInput, RangoInstantes } from '../agenda-config-api';
import { bloqueosFicticios, boton, enviar, error422, errorDe, escribir, input } from '../test-fixtures';
import { createBloqueosSection, type BloqueosSectionDeps } from './bloqueos-section';

describe('sección Bloqueos (9.3)', () => {
  let container: HTMLElement;
  let deps: { [K in keyof BloqueosSectionDeps]: ReturnType<typeof vi.fn<BloqueosSectionDeps[K]>> };

  beforeEach(async () => {
    container = document.createElement('div');
    deps = {
      listarBloqueos: vi.fn<(r?: RangoInstantes) => Promise<Bloqueo[]>>().mockResolvedValue([...bloqueosFicticios]),
      crearBloqueo: vi.fn<(i: BloqueoInput) => Promise<Bloqueo>>(),
      actualizarBloqueo: vi.fn<(id: number, i: BloqueoInput) => Promise<Bloqueo>>(),
      eliminarBloqueo: vi.fn<(id: number) => Promise<void>>(),
      confirmar: vi.fn<(mensaje: string) => boolean>().mockReturnValue(true),
    };
    createBloqueosSection(deps)(container);
    await vi.waitFor(() => expect(filas()).toHaveLength(1));
  });

  const filas = (): HTMLElement[] => [...container.querySelectorAll<HTMLElement>('li[data-id]')];
  const form = (): HTMLFormElement | null => container.querySelector('form[data-form="bloqueo"]');

  function cargar(desde: string, hasta: string, motivo = ''): void {
    escribir(container, 'desde', desde);
    escribir(container, 'hasta', hasta);
    escribir(container, 'motivo', motivo);
  }

  it('lista vigentes y futuros (sin rango) mostrando la hora del consultorio', () => {
    expect(deps.listarBloqueos).toHaveBeenCalledWith();
    expect(filas()[0]?.textContent).toContain('24/12/2026 00:00');
    expect(filas()[0]?.textContent).toContain('26/12/2026 00:00');
    expect(filas()[0]?.textContent).toContain('Fiestas');
  });

  it('con motivo muestra el separador " · " antes del motivo', () => {
    expect(filas()[0]?.textContent).toContain('26/12/2026 00:00 · Fiestas');
  });

  it.each([
    ['""', ''],
    ['null', null],
  ])('motivo %s se trata como "sin motivo": no muestra el separador', async (_caso, motivo) => {
    const otro = document.createElement('div');
    deps.listarBloqueos.mockResolvedValue([{ ...bloqueosFicticios[0]!, motivo }]);
    createBloqueosSection(deps)(otro);
    await vi.waitFor(() => expect(otro.querySelectorAll('li[data-id]')).toHaveLength(1));

    const texto = otro.querySelector('li[data-id] span')?.textContent;
    expect(texto).toBe('24/12/2026 00:00 → 26/12/2026 00:00');
    expect(otro.querySelector('li[data-id]')?.textContent).not.toContain('·');
  });

  it('editar un bloqueo sin motivo ("") deja el campo motivo vacío', async () => {
    const otro = document.createElement('div');
    deps.listarBloqueos.mockResolvedValue([{ ...bloqueosFicticios[0]!, motivo: '' }]);
    createBloqueosSection(deps)(otro);
    await vi.waitFor(() => expect(otro.querySelectorAll('li[data-id]')).toHaveLength(1));

    boton(otro.querySelector<HTMLElement>('li[data-id]')!, 'Editar').click();
    expect(input(otro, 'motivo').value).toBe('');
  });

  it('alta: convierte la hora local a instantes UTC', async () => {
    deps.crearBloqueo.mockResolvedValue({ id: 21, desde: '2027-01-10T12:00:00Z', hasta: '2027-01-10T15:00:00Z', motivo: null });
    cargar('2027-01-10T09:00', '2027-01-10T12:00');
    enviar(form());

    await vi.waitFor(() => expect(filas()).toHaveLength(2));
    expect(deps.crearBloqueo).toHaveBeenCalledWith({
      desde: '2027-01-10T12:00:00Z',
      hasta: '2027-01-10T15:00:00Z',
      motivo: null,
    });
  });

  it('motivo opcional: se envía recortado si existe', async () => {
    deps.crearBloqueo.mockResolvedValue({ id: 22, desde: '2027-01-10T12:00:00Z', hasta: '2027-01-10T15:00:00Z', motivo: 'Curso' });
    cargar('2027-01-10T09:00', '2027-01-10T12:00', '  Curso ');
    enviar(form());
    await vi.waitFor(() => expect(deps.crearBloqueo).toHaveBeenCalled());
    expect(deps.crearBloqueo.mock.calls[0]?.[0].motivo).toBe('Curso');
  });

  it('motivo de más de 200 caracteres: no envía', () => {
    cargar('2027-01-10T09:00', '2027-01-10T12:00', 'x'.repeat(201));
    enviar(form());
    expect(deps.crearBloqueo).not.toHaveBeenCalled();
    expect(errorDe(container, 'motivo')).toBe('El motivo admite hasta 200 caracteres.');
  });

  it('rango invertido: no envía', () => {
    cargar('2027-01-10T12:00', '2027-01-10T09:00');
    enviar(form());
    expect(deps.crearBloqueo).not.toHaveBeenCalled();
    expect(errorDe(container, 'hasta')).toBe('El fin debe ser posterior al inicio.');
  });

  it('fecha vacía: no envía', () => {
    cargar('', '2027-01-10T09:00');
    enviar(form());
    expect(deps.crearBloqueo).not.toHaveBeenCalled();
    expect(errorDe(container, 'desde')).toBe('Ingresá fecha y hora.');
  });

  it('editar: precarga en hora local y envía PUT', async () => {
    deps.actualizarBloqueo.mockResolvedValue({ ...bloqueosFicticios[0]!, motivo: 'Vacaciones' });
    boton(filas()[0]!, 'Editar').click();
    expect(input(container, 'desde').value).toBe('2026-12-24T00:00');
    expect(input(container, 'motivo').value).toBe('Fiestas');
    escribir(container, 'motivo', 'Vacaciones');
    enviar(form());

    await vi.waitFor(() => expect(filas()[0]?.textContent).toContain('Vacaciones'));
    expect(deps.actualizarBloqueo).toHaveBeenCalledWith(20, {
      desde: '2026-12-24T03:00:00Z',
      hasta: '2026-12-26T03:00:00Z',
      motivo: 'Vacaciones',
    });
  });

  it('eliminar con confirmación', async () => {
    deps.eliminarBloqueo.mockResolvedValue(undefined);
    boton(filas()[0]!, 'Eliminar').click();
    await vi.waitFor(() => expect(filas()).toHaveLength(0));
    expect(deps.eliminarBloqueo).toHaveBeenCalledWith(20);
    expect(container.textContent).toContain('No hay bloqueos vigentes ni futuros.');
  });

  it('eliminar cancelado no llama a la API', () => {
    deps.confirmar.mockReturnValue(false);
    boton(filas()[0]!, 'Eliminar').click();
    expect(deps.eliminarBloqueo).not.toHaveBeenCalled();
  });

  it('422 del servidor: error por campo', async () => {
    deps.crearBloqueo.mockRejectedValue(error422('hasta', 'Debe ser posterior a desde'));
    cargar('2027-01-10T09:00', '2027-01-10T12:00');
    enviar(form());
    await vi.waitFor(() => expect(errorDe(container, 'hasta')).toBe('Debe ser posterior a desde'));
  });
});
