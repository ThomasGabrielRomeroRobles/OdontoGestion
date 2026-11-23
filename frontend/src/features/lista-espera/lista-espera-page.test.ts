import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createListaEsperaPage } from './lista-espera-page';
import type { EntradaListaEspera, ListaEsperaApi } from './lista-espera-api';
import { entradaActiva, entradaCubierta, entradaPendiente } from './test-fixtures';

describe('página privada de la lista de espera (/lista-espera)', () => {
  let container: HTMLElement;
  let listar: ReturnType<typeof vi.fn<() => Promise<readonly EntradaListaEspera[]>>>;
  let retirar: ReturnType<typeof vi.fn<(id: number) => Promise<void>>>;
  let confirmar: ReturnType<typeof vi.fn<(m: string) => boolean>>;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    listar = vi.fn<() => Promise<readonly EntradaListaEspera[]>>().mockResolvedValue([entradaPendiente, entradaActiva, entradaCubierta]);
    retirar = vi.fn<(id: number) => Promise<void>>().mockResolvedValue(undefined);
    confirmar = vi.fn<(m: string) => boolean>().mockReturnValue(true);
  });
  afterEach(() => container.remove());

  async function montar(): Promise<void> {
    const api = { listar, retirar } as unknown as ListaEsperaApi;
    createListaEsperaPage(api, confirmar)(container);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  function fila(id: number): HTMLElement {
    const encontrada = container.querySelector<HTMLElement>(`tr[data-id="${id}"]`);
    if (!encontrada) throw new Error(`no existe la fila ${id}`);
    return encontrada;
  }

  it('muestra una fila por entrada con paciente, prestación, rango y estado, sin DNI ni email', async () => {
    await montar();

    expect(container.querySelectorAll('tbody tr')).toHaveLength(3);
    const activa = fila(11);
    expect(activa.querySelector('[data-col="paciente"]')?.textContent).toBe('Ficticia, Ana');
    expect(activa.querySelector('[data-col="prestacion"]')?.textContent).toBe('Consulta');
    expect(activa.querySelector('[data-col="rango"]')?.textContent).toBe('23/11/2026 al 02/12/2026');
    expect(activa.querySelector('[data-col="estado"]')?.textContent).toBe('Activa');
    expect(container.textContent).not.toMatch(/@|\d{2}\.\d{3}\.\d{3}/);
  });

  it('"Retirar" solo aparece en pendientes y activas', async () => {
    await montar();

    expect(fila(11).querySelector('button[data-accion="retirar"]')).not.toBeNull();
    expect(fila(12).querySelector('button[data-accion="retirar"]')).not.toBeNull();
    expect(fila(10).querySelector('button[data-accion="retirar"]')).toBeNull();
  });

  it('retirar con 204 pasa la fila a Cancelada sin recargar y quita el botón', async () => {
    await montar();

    fila(11).querySelector<HTMLButtonElement>('button[data-accion="retirar"]')?.click();

    await vi.waitFor(() => expect(fila(11).querySelector('[data-col="estado"]')?.textContent).toBe('Cancelada'));
    expect(retirar).toHaveBeenCalledWith(11);
    expect(listar).toHaveBeenCalledTimes(1);
    expect(fila(11).querySelector('button[data-accion="retirar"]')).toBeNull();
  });

  it('si la persona no confirma no se retira nada', async () => {
    confirmar.mockReturnValue(false);
    await montar();

    fila(11).querySelector<HTMLButtonElement>('button[data-accion="retirar"]')?.click();

    expect(retirar).not.toHaveBeenCalled();
    expect(fila(11).querySelector('[data-col="estado"]')?.textContent).toBe('Activa');
  });

  it('un error al retirar muestra el mensaje y deja la fila igual', async () => {
    retirar.mockRejectedValue({ kind: 'http', status: 422, title: 'Regla', fieldErrors: [], extensions: {} });
    await montar();

    fila(11).querySelector<HTMLButtonElement>('button[data-accion="retirar"]')?.click();

    await vi.waitFor(() => expect(container.querySelector('[role="alert"]')?.textContent).toContain('No se pudo retirar'));
    expect(fila(11).querySelector('[data-col="estado"]')?.textContent).toBe('Activa');
  });

  it('sin entradas muestra el estado vacío y un error de carga muestra el mensaje', async () => {
    listar.mockResolvedValue([]);
    await montar();
    expect(container.querySelector('[data-vacio]')?.textContent).toContain('No hay personas');

    container.replaceChildren();
    delete container.dataset['estadoCarga'];
    listar.mockRejectedValue({ kind: 'network' });
    await montar();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar');
  });
});
