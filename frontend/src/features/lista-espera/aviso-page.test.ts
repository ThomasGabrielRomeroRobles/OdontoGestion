import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAvisoPage } from './aviso-page';
import type { ListaEsperaApi, ResultadoAceptacion, ResultadoAviso } from './lista-espera-api';
import { resumenAviso } from './test-fixtures';

const reservado: ResultadoAceptacion = {
  tipo: 'reservado',
  turno: { prestacion: 'Consulta', inicio: resumenAviso.inicio, fin: resumenAviso.fin, estado: 'RESERVADO' },
};

describe('página pública de aviso de hueco (/avisos/:token)', () => {
  let container: HTMLElement;
  let obtenerAviso: ReturnType<typeof vi.fn<(t: string) => Promise<ResultadoAviso>>>;
  let aceptarAviso: ReturnType<typeof vi.fn<(t: string) => Promise<ResultadoAceptacion>>>;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    obtenerAviso = vi.fn<(t: string) => Promise<ResultadoAviso>>().mockResolvedValue({ tipo: 'resumen', resumen: resumenAviso });
    aceptarAviso = vi.fn<(t: string) => Promise<ResultadoAceptacion>>();
  });
  afterEach(() => container.remove());

  async function montar(token = 'tok-abc'): Promise<void> {
    const api = { obtenerAviso, aceptarAviso } as unknown as ListaEsperaApi;
    createAvisoPage(api)(container, token);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  function el<T extends Element>(selector: string): T {
    const encontrado = container.querySelector<T>(selector);
    if (!encontrado) throw new Error(`no existe ${selector}`);
    return encontrado;
  }

  it('abrir el enlace solo consulta: muestra el hueco y no envía ningún POST', async () => {
    await montar();

    expect(obtenerAviso).toHaveBeenCalledWith('tok-abc');
    expect(aceptarAviso).not.toHaveBeenCalled();
    expect(el('[data-campo="prestacion"]').textContent).toBe('Consulta');
    expect(el('[data-campo="consultorio"]').textContent).toBe('Consultorio Ficticio');
    expect(el('[data-campo="fecha"]').textContent).toContain('10:00');
    expect(el('button[data-accion="aceptar"]').textContent).toBe('Quiero este turno');
    expect(container.textContent).not.toContain('tok-abc');
  });

  it('aceptar con 200 muestra el turno reservado y quita el botón', async () => {
    aceptarAviso.mockResolvedValue(reservado);
    await montar();

    el<HTMLButtonElement>('button[data-accion="aceptar"]').click();

    await vi.waitFor(() => expect(el('[role="status"]').textContent).toContain('quedó reservado'));
    expect(container.querySelector('button[data-accion="aceptar"]')).toBeNull();
    expect(aceptarAviso).toHaveBeenCalledTimes(1);
  });

  it('el doble clic envía un solo POST', async () => {
    let resolver: (r: ResultadoAceptacion) => void = () => undefined;
    aceptarAviso.mockReturnValue(new Promise((r) => (resolver = r)));
    await montar();
    const boton = el<HTMLButtonElement>('button[data-accion="aceptar"]');

    boton.click();
    boton.click();

    expect(aceptarAviso).toHaveBeenCalledTimes(1);
    expect(boton.disabled).toBe(true);
    resolver(reservado);
    await vi.waitFor(() => expect(container.querySelector('button[data-accion="aceptar"]')).toBeNull());
  });

  it('409 explica que el horario ya no está disponible y no ofrece reintentar', async () => {
    aceptarAviso.mockResolvedValue({ tipo: 'noDisponible' });
    await montar();

    el<HTMLButtonElement>('button[data-accion="aceptar"]').click();

    await vi.waitFor(() => expect(el('[data-resultado="noDisponible"]').textContent).toContain('ya no está disponible'));
    expect(container.querySelector('button[data-accion="aceptar"]')).toBeNull();
  });

  it('410 al aceptar dice que el aviso venció', async () => {
    aceptarAviso.mockResolvedValue({ tipo: 'vencido' });
    await montar();

    el<HTMLButtonElement>('button[data-accion="aceptar"]').click();

    await vi.waitFor(() => expect(el('[data-resultado="vencido"]').textContent).toContain('venció'));
  });

  it('un aviso con disponible falso se muestra sin botón', async () => {
    obtenerAviso.mockResolvedValue({ tipo: 'resumen', resumen: { ...resumenAviso, disponible: false } });
    await montar();

    expect(container.querySelector('button')).toBeNull();
    expect(el('[data-resultado="noDisponible"]').textContent).toContain('ya no está disponible');
  });

  it('410 al abrir muestra el enlace no disponible con el mismo texto sin importar la causa', async () => {
    obtenerAviso.mockResolvedValue({ tipo: 'noDisponible' });
    await montar();

    expect(el('h1').textContent).toBe('Enlace no disponible');
    expect(container.querySelector('button')).toBeNull();
  });

  it('un error de red al abrir muestra el mensaje genérico', async () => {
    obtenerAviso.mockRejectedValue({ kind: 'network' });
    await montar();

    expect(el('[role="alert"]').textContent).toContain('No pudimos');
  });

  it('un error al aceptar rehabilita el botón', async () => {
    aceptarAviso.mockRejectedValue({ kind: 'network' });
    await montar();
    const boton = el<HTMLButtonElement>('button[data-accion="aceptar"]');

    boton.click();

    await vi.waitFor(() => expect(boton.disabled).toBe(false));
    expect(el('[role="alert"]').textContent).toContain('No pudimos');
  });
});
