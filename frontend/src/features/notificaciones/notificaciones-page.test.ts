import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import type { FiltrosNotificaciones, NotificacionDto, NotificacionesApi, Pagina } from './notificaciones-api';
import { createNotificacionesPage } from './notificaciones-page';
import { enviada, fallida, pagina, pendiente } from './test-fixtures';

type ListarMock = ReturnType<typeof vi.fn<(f: FiltrosNotificaciones) => Promise<Pagina<NotificacionDto>>>>;
type ReintentarMock = ReturnType<typeof vi.fn<(id: number) => Promise<NotificacionDto>>>;

describe('página Registro de envíos', () => {
  let container: HTMLElement;
  let listar: ListarMock;
  let reintentar: ReintentarMock;

  beforeEach(() => {
    container = document.createElement('div');
    listar = vi.fn<(f: FiltrosNotificaciones) => Promise<Pagina<NotificacionDto>>>();
    reintentar = vi.fn<(id: number) => Promise<NotificacionDto>>();
  });

  function api(): NotificacionesApi {
    return { listar, reintentar };
  }

  function filas(): HTMLTableRowElement[] {
    return [...container.querySelectorAll<HTMLTableRowElement>('tbody tr')];
  }

  function fila(id: number): HTMLTableRowElement {
    const encontrada = container.querySelector<HTMLTableRowElement>(`tbody tr[data-id="${id}"]`);
    if (!encontrada) throw new Error(`no hay fila ${id}`);
    return encontrada;
  }

  function celda(id: number, columna: string): string {
    return fila(id).querySelector(`[data-col="${columna}"]`)?.textContent ?? '';
  }

  function botonReintentar(id: number): HTMLButtonElement | null {
    return fila(id).querySelector<HTMLButtonElement>('button[data-accion="reintentar"]');
  }

  function select(nombre: string): HTMLSelectElement {
    const el = container.querySelector<HTMLSelectElement>(`select[name="${nombre}"]`);
    if (!el) throw new Error(`no hay select ${nombre}`);
    return el;
  }

  function botonPagina(accion: 'anterior' | 'siguiente'): HTMLButtonElement {
    const el = container.querySelector<HTMLButtonElement>(`button[data-pagina="${accion}"]`);
    if (!el) throw new Error(`no hay botón ${accion}`);
    return el;
  }

  async function montar(primera: Pagina<NotificacionDto>): Promise<void> {
    listar.mockResolvedValueOnce(primera);
    createNotificacionesPage(api())(container);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  describe('listado (10.2)', () => {
    it('consulta la primera página sin filtros', async () => {
      await montar(pagina([fallida]));
      expect(listar).toHaveBeenCalledWith({ page: 0, size: 20 });
    });

    it('muestra una fila por notificación con tipo, destinatario, estado, intentos, error y fecha en Buenos Aires', async () => {
      await montar(pagina([fallida, enviada, pendiente]));

      expect(filas()).toHaveLength(3);
      expect(celda(7, 'tipo')).toBe('Confirmación de turno');
      expect(celda(7, 'destinatario')).toBe('ana.ficticia@example.com');
      expect(celda(7, 'estado')).toBe('FALLIDA');
      expect(celda(7, 'intentos')).toBe('3');
      expect(celda(7, 'error')).toBe('MailSendException: conexión rechazada');
      // 13:00 UTC = 10:00 en Buenos Aires (UTC-3).
      expect(celda(7, 'creada')).toBe('20/11/2026 10:00');
      expect(celda(8, 'enviada')).toBe('21/11/2026 09:00');
      expect(celda(8, 'error')).toBe('');
      expect(celda(9, 'enviada')).toBe('');
      // 02:30 UTC del 22 = 23:30 del 21 en Buenos Aires: la fecha cambia de día.
      expect(celda(9, 'creada')).toBe('21/11/2026 23:30');
    });

    it('con una página vacía muestra que no hay envíos registrados', async () => {
      await montar(pagina([]));

      expect(filas()).toHaveLength(0);
      expect(container.textContent).toContain('No hay envíos registrados');
    });

    it('solo las FALLIDA muestran Reintentar', async () => {
      await montar(pagina([fallida, enviada, pendiente]));

      expect(botonReintentar(7)).not.toBeNull();
      expect(botonReintentar(8)).toBeNull();
      expect(botonReintentar(9)).toBeNull();
    });

    it('si la carga falla muestra el error de la API', async () => {
      const error: ApiError = { kind: 'http', status: 500, title: 'Error interno', fieldErrors: [] };
      listar.mockRejectedValueOnce(error);
      createNotificacionesPage(api())(container);

      await vi.waitFor(() => expect(container.querySelector('[role="alert"]')?.textContent).toBe('Error interno'));
    });
  });

  describe('filtros y paginación (10.3)', () => {
    it('elegir FALLIDA consulta con estado=FALLIDA desde la primera página', async () => {
      await montar(pagina([fallida, enviada], { totalPaginas: 3, totalElementos: 50 }));
      listar.mockResolvedValueOnce(pagina([fallida]));

      select('estado').value = 'FALLIDA';
      select('estado').dispatchEvent(new Event('change'));

      await vi.waitFor(() => expect(listar).toHaveBeenLastCalledWith({ estado: 'FALLIDA', page: 0, size: 20 }));
      await vi.waitFor(() => expect(filas()).toHaveLength(1));
    });

    it('filtrar por tipo se combina con el estado', async () => {
      await montar(pagina([fallida]));
      listar.mockResolvedValue(pagina([fallida]));

      select('estado').value = 'FALLIDA';
      select('estado').dispatchEvent(new Event('change'));
      select('tipo').value = 'RECORDATORIO';
      select('tipo').dispatchEvent(new Event('change'));

      await vi.waitFor(() =>
        expect(listar).toHaveBeenLastCalledWith({ estado: 'FALLIDA', tipo: 'RECORDATORIO', page: 0, size: 20 }),
      );
    });

    it('"Siguiente" consulta la página 1 conservando los filtros', async () => {
      await montar(pagina([fallida]));
      listar.mockResolvedValueOnce(pagina([fallida], { pagina: 0, totalPaginas: 2, totalElementos: 21 }));
      select('estado').value = 'FALLIDA';
      select('estado').dispatchEvent(new Event('change'));
      await vi.waitFor(() => expect(botonPagina('siguiente').disabled).toBe(false));

      listar.mockResolvedValueOnce(pagina([fallida], { pagina: 1, totalPaginas: 2, totalElementos: 21 }));
      botonPagina('siguiente').click();

      await vi.waitFor(() => expect(listar).toHaveBeenLastCalledWith({ estado: 'FALLIDA', page: 1, size: 20 }));
      await vi.waitFor(() => expect(botonPagina('siguiente').disabled).toBe(true));
      expect(botonPagina('anterior').disabled).toBe(false);
      expect(container.textContent).toContain('Página 2 de 2');
    });

    it('en la única página los botones de paginación están deshabilitados', async () => {
      await montar(pagina([fallida]));

      expect(botonPagina('anterior').disabled).toBe(true);
      expect(botonPagina('siguiente').disabled).toBe(true);
    });
  });

  describe('reintento (10.4)', () => {
    it('con 200 la fila pasa a PENDIENTE y el botón desaparece', async () => {
      await montar(pagina([fallida, enviada]));
      reintentar.mockResolvedValueOnce({ ...fallida, estado: 'PENDIENTE', reintentosManuales: 1 });

      botonReintentar(7)?.click();

      await vi.waitFor(() => expect(celda(7, 'estado')).toBe('PENDIENTE'));
      expect(reintentar).toHaveBeenCalledWith(7);
      expect(botonReintentar(7)).toBeNull();
      expect(listar).toHaveBeenCalledTimes(1);
    });

    it('con 422 muestra el detail del ProblemDetail y la fila no cambia', async () => {
      await montar(pagina([fallida]));
      const error: ApiError = {
        kind: 'http',
        status: 422,
        title: 'Regla de negocio',
        detail: 'La notificación ya se reintentó 3 veces manualmente.',
        fieldErrors: [],
      };
      reintentar.mockRejectedValueOnce(error);

      botonReintentar(7)?.click();

      await vi.waitFor(() =>
        expect(container.querySelector('[role="alert"]')?.textContent).toBe(
          'La notificación ya se reintentó 3 veces manualmente.',
        ),
      );
      expect(celda(7, 'estado')).toBe('FALLIDA');
      expect(botonReintentar(7)?.disabled).toBe(false);
    });

    it('un error de red muestra un mensaje genérico', async () => {
      await montar(pagina([fallida]));
      reintentar.mockRejectedValueOnce({ kind: 'network' } satisfies ApiError);

      botonReintentar(7)?.click();

      await vi.waitFor(() =>
        expect(container.querySelector('[role="alert"]')?.textContent).toBe(
          'No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo.',
        ),
      );
    });
  });
});
