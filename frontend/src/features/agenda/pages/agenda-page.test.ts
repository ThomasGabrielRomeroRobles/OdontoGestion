import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RouteContext } from '../../../shared/router/router';
import type { AgendaApi } from '../agenda-api';
import { crearAgendaDepsDoble, type AgendaDepsDoble } from '../deps-test-helpers';
import { errorRed, flush, turnoEn, turnoFicticio } from '../test-fixtures';
import { createAgendaPage, type AlCambiar, type PanelesAgenda } from './agenda-page';
import { createTurnoDetalle } from './turno-detalle';

/** Miércoles 25/11/2026 10:00 en el consultorio. */
const AHORA = new Date('2026-11-25T13:00:00Z');

describe('vista /agenda (grupo 4)', () => {
  let container: HTMLElement;
  let deps: AgendaDepsDoble;
  let navigate: ReturnType<typeof vi.fn<(to: string) => void>>;

  beforeEach(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    document.body.replaceChildren();
    container = document.createElement('main');
    document.body.append(container);
    deps = crearAgendaDepsDoble(AHORA);
    navigate = vi.fn<(to: string) => void>();
  });
  afterEach(() => vi.unstubAllEnvs());

  async function abrir(url: string, api: Partial<AgendaApi> = {}, paneles: PanelesAgenda = {}): Promise<void> {
    Object.assign(deps.api, api);
    history.replaceState(null, '', url);
    const context: RouteContext = { url: new URL(location.href), navigate, params: {} };
    createAgendaPage(deps, paneles)(container, context);
    await flush();
  }

  const items = (): HTMLElement[] => [...container.querySelectorAll<HTMLElement>('[data-turno-id]')];
  const columnas = (): HTMLElement[] => [...container.querySelectorAll<HTMLElement>('[data-dia]')];
  const boton = (texto: string): HTMLButtonElement => {
    const b = [...container.querySelectorAll<HTMLButtonElement>('button')].find((x) => x.textContent === texto);
    if (b === undefined) throw new Error(`no hay botón "${texto}"`);
    return b;
  };

  describe('Acceso a la agenda', () => {
    it('sin parámetros: vista semana de hoy (lunes 23 a domingo 29/11)', async () => {
      await abrir('/agenda');
      expect(columnas().map((c) => c.dataset['dia'])).toEqual([
        '2026-11-23',
        '2026-11-24',
        '2026-11-25',
        '2026-11-26',
        '2026-11-27',
        '2026-11-28',
        '2026-11-29',
      ]);
      expect(deps.api.listarAgenda).toHaveBeenCalledWith('2026-11-23T03:00:00Z', '2026-11-30T03:00:00Z');
      expect(container.querySelector('h1')?.textContent).toBe('Agenda');
    });

    it('parámetros inválidos: reemplaza la URL por la semana de hoy sin agregar historial', async () => {
      history.pushState(null, '', '/otra');
      const largo = history.length;
      await abrir('/agenda?vista=mes&fecha=2026-13-40');
      expect(location.pathname + location.search).toBe('/agenda?vista=semana&fecha=2026-11-25');
      expect(history.length).toBe(largo);
      expect(navigate).not.toHaveBeenCalled();
      expect(columnas()).toHaveLength(7);
    });
  });

  describe('Turnos agrupados por día del consultorio', () => {
    it('vista día: pide el rango del día', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-11-24');
      expect(deps.api.listarAgenda).toHaveBeenCalledWith('2026-11-24T03:00:00Z', '2026-11-25T03:00:00Z');
      expect(columnas().map((c) => c.dataset['dia'])).toEqual(['2026-11-24']);
    });

    it('un turno a la 01:00Z del 25 aparece el martes 24 a las 22:00', async () => {
      const tarde = turnoFicticio({ inicio: '2026-11-25T01:00:00Z', fin: '2026-11-25T01:45:00Z' });
      await abrir('/agenda?vista=semana&fecha=2026-11-23', { listarAgenda: vi.fn().mockResolvedValue([tarde]) });
      const martes = container.querySelector<HTMLElement>('[data-dia="2026-11-24"]');
      expect(martes?.querySelector('[data-turno-id="41"]')?.textContent).toContain('22:00–22:45');
    });

    it('ordena por hora', async () => {
      const once = turnoFicticio({ id: 2, inicio: '2026-11-24T14:00:00Z', fin: '2026-11-24T14:30:00Z' });
      const nueve = turnoFicticio({ id: 1 });
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: vi.fn().mockResolvedValue([once, nueve]) });
      expect(items().map((i) => i.dataset['turnoId'])).toEqual(['1', '2']);
    });

    it('día sin turnos: "Sin turnos"', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-11-24');
      expect(container.querySelector('[data-dia="2026-11-24"]')?.textContent).toContain('Sin turnos');
    });
  });

  describe('Navegación por fechas y vistas', () => {
    it('Siguiente en semana avanza 7 días', async () => {
      await abrir('/agenda?vista=semana&fecha=2026-11-23');
      boton('Siguiente').click();
      expect(navigate).toHaveBeenCalledWith('/agenda?vista=semana&fecha=2026-11-30');
    });

    it('Anterior en día retrocede 1 día', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-11-24');
      boton('Anterior').click();
      expect(navigate).toHaveBeenCalledWith('/agenda?vista=dia&fecha=2026-11-23');
    });

    it('cambio a vista Día conserva la fecha', async () => {
      await abrir('/agenda?vista=semana&fecha=2026-11-23');
      boton('Día').click();
      expect(navigate).toHaveBeenCalledWith('/agenda?vista=dia&fecha=2026-11-23');
    });

    it('Hoy va a la fecha del consultorio', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-10-01');
      boton('Hoy').click();
      expect(navigate).toHaveBeenCalledWith('/agenda?vista=dia&fecha=2026-11-25');
    });

    it('enlaza a Turnos de hoy', async () => {
      await abrir('/agenda');
      const link = container.querySelector<HTMLAnchorElement>('a[href="/agenda/hoy"]');
      expect(link?.dataset['link']).toBe('');
    });
  });

  describe('Estado del turno visible (4.3)', () => {
    it('pendiente: hora, paciente, prestación, etiqueta y estilo', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-11-24', {
        listarAgenda: vi.fn().mockResolvedValue([turnoEn('PENDIENTE_CONFIRMACION')]),
      });
      const item = items()[0];
      expect(item?.textContent).toContain('09:00–09:45');
      expect(item?.textContent).toContain('Gómez, Lucía');
      expect(item?.textContent).toContain('Limpieza');
      expect(item?.querySelector('[data-estado]')?.textContent).toBe('Pendiente de confirmación');
      expect(item?.classList.contains('turno--pendiente-confirmacion')).toBe(true);
    });

    it('estilos distintos por estado', async () => {
      const turnos = (['RESERVADO', 'CONFIRMADO', 'ATENDIDO', 'AUSENTE'] as const).map((estado, i) =>
        turnoEn(estado, { id: i + 1, inicio: `2026-11-24T1${i + 2}:00:00Z`, fin: `2026-11-24T1${i + 2}:30:00Z` }),
      );
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: vi.fn().mockResolvedValue(turnos) });
      const clases = items().map((i) => [...i.classList].find((c) => c.startsWith('turno--')));
      expect(new Set(clases).size).toBe(4);
      expect(new Set(items().map((i) => i.querySelector('[data-estado]')?.textContent)).size).toBe(4);
    });

    it('cancelados y expirados ocultos por defecto; el control los muestra', async () => {
      const reservado = turnoEn('RESERVADO', { id: 1 });
      const cancelado = turnoEn('CANCELADO', { id: 2 });
      const expirado = turnoEn('EXPIRADO', { id: 3 });
      await abrir('/agenda?vista=dia&fecha=2026-11-24', {
        listarAgenda: vi.fn().mockResolvedValue([reservado, cancelado, expirado]),
      });
      expect(items().map((i) => i.dataset['turnoId'])).toEqual(['1']);

      const control = container.querySelector<HTMLInputElement>('input[name="mostrarCancelados"]');
      expect(control?.labels?.[0]?.textContent).toBe('Mostrar cancelados y expirados');
      control!.checked = true;
      control!.dispatchEvent(new Event('change'));
      expect(items()).toHaveLength(3);
      expect(container.querySelector('[data-turno-id="2"] [data-estado]')?.textContent).toBe('Cancelado');
    });

    it('indicador de ausencias cuando el paciente tiene más de 0', async () => {
      const con = turnoFicticio({ id: 1, paciente: { ...turnoFicticio().paciente, cantidadAusencias: 2 } });
      const sin = turnoFicticio({ id: 2, inicio: '2026-11-24T15:00:00Z', fin: '2026-11-24T15:45:00Z' });
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: vi.fn().mockResolvedValue([con, sin]) });
      expect(container.querySelector('[data-turno-id="1"]')?.textContent).toContain('2 aus.');
      expect(container.querySelector('[data-turno-id="2"]')?.textContent).not.toContain('aus.');
    });
  });

  describe('Falla al cargar la agenda (4.4)', () => {
    it('muestra el mensaje de conexión y Reintentar repite la solicitud', async () => {
      const listar = vi.fn<AgendaApi['listarAgenda']>().mockRejectedValueOnce(errorRed).mockResolvedValue([turnoFicticio()]);
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: listar });
      expect(container.querySelector('[role="alert"]')?.textContent).toBe('No se pudo conectar. Reintentá.');
      boton('Reintentar').click();
      await flush();
      expect(listar).toHaveBeenCalledTimes(2);
      expect(items()).toHaveLength(1);
      expect(container.querySelector('[role="alert"]')?.textContent).toBe('');
    });

    it('Actualizar vuelve a pedir el rango', async () => {
      await abrir('/agenda?vista=dia&fecha=2026-11-24');
      boton('Actualizar').click();
      await flush();
      expect(deps.api.listarAgenda).toHaveBeenCalledTimes(2);
    });
  });

  describe('Panel de detalle', () => {
    it('activar un turno abre el detalle en el panel', async () => {
      const detalle = vi.fn<NonNullable<PanelesAgenda['detalle']>>();
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: vi.fn().mockResolvedValue([turnoFicticio()]) }, { detalle });
      container.querySelector<HTMLButtonElement>('[data-turno-id="41"] button')!.click();
      expect(detalle).toHaveBeenCalledWith(container.querySelector('[data-panel]'), turnoFicticio(), expect.any(Function));
    });

    it('al cambiar con cerrar y mensaje: vacía el panel, recarga y deja el mensaje', async () => {
      let cambiar: AlCambiar = () => undefined;
      const detalle: NonNullable<PanelesAgenda['detalle']> = (host, _t, alCambiar) => {
        host.textContent = 'detalle abierto';
        cambiar = alCambiar;
      };
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: vi.fn().mockResolvedValue([turnoFicticio()]) }, { detalle });
      container.querySelector<HTMLButtonElement>('[data-turno-id="41"] button')!.click();
      cambiar({ cerrar: true, mensaje: 'El turno ya no existe.' });
      await flush();
      expect(container.querySelector('[data-panel]')?.textContent).toBe('');
      expect(deps.api.listarAgenda).toHaveBeenCalledTimes(2);
      expect(container.querySelector('[role="alert"]')?.textContent).toBe('El turno ya no existe.');
    });

    it('con el detalle real: marcar atendido recarga la agenda con el estado actual', async () => {
      const listar = vi
        .fn<AgendaApi['listarAgenda']>()
        .mockResolvedValueOnce([turnoEn('CONFIRMADO')])
        .mockResolvedValue([turnoEn('ATENDIDO')]);
      deps.api.marcarAtendido.mockResolvedValue(turnoEn('ATENDIDO'));
      await abrir('/agenda?vista=dia&fecha=2026-11-24', { listarAgenda: listar }, { detalle: createTurnoDetalle(deps) });
      container.querySelector<HTMLButtonElement>('[data-turno-id="41"] button')!.click();
      await flush();
      [...container.querySelectorAll<HTMLButtonElement>('[data-acciones] button')].find((b) => b.textContent === 'Marcar atendido')!.click();
      await flush();
      expect(container.querySelector('[data-turno-id="41"] [data-estado]')?.textContent).toBe('Atendido');
    });
  });

  describe('Nuevo turno', () => {
    it('abre el panel y, al crear, navega a la vista día del turno', async () => {
      let crear: (t: ReturnType<typeof turnoFicticio>) => void = () => undefined;
      const nuevoTurno: NonNullable<PanelesAgenda['nuevoTurno']> = (host, alCrear) => {
        host.textContent = 'form nuevo turno';
        crear = alCrear;
      };
      await abrir('/agenda?vista=semana&fecha=2026-11-23', {}, { nuevoTurno });
      boton('Nuevo turno').click();
      expect(container.querySelector('[data-panel]')?.textContent).toBe('form nuevo turno');
      crear(turnoFicticio({ inicio: '2026-11-26T15:00:00Z' }));
      expect(container.querySelector('[data-panel]')?.textContent).toBe('');
      expect(navigate).toHaveBeenCalledWith('/agenda?vista=dia&fecha=2026-11-26');
    });
  });
});
