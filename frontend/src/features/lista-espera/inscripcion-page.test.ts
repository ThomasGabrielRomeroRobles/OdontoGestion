import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PrestacionPublica } from '../publico/publico-api';
import { prestaciones } from '../publico/test-fixtures';
import { createInscripcionPage, MENSAJE_INSCRIPCION } from './inscripcion-page';
import type { InscripcionListaEspera, ListaEsperaApi, ResultadoInscripcion } from './lista-espera-api';
import { inscripcionValida } from './test-fixtures';

describe('página pública de inscripción en la lista de espera', () => {
  let container: HTMLElement;
  let inscribir: ReturnType<typeof vi.fn<(i: InscripcionListaEspera) => Promise<ResultadoInscripcion>>>;
  let listarPrestaciones: ReturnType<typeof vi.fn<() => Promise<readonly PrestacionPublica[]>>>;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    inscribir = vi.fn<(i: InscripcionListaEspera) => Promise<ResultadoInscripcion>>();
    listarPrestaciones = vi.fn<() => Promise<readonly PrestacionPublica[]>>().mockResolvedValue(prestaciones);
  });
  afterEach(() => {
    container.remove();
    vi.useRealTimers();
  });

  function el<T extends Element>(selector: string): T {
    const encontrado = container.querySelector<T>(selector);
    if (!encontrado) throw new Error(`no existe ${selector}`);
    return encontrado;
  }

  function escribir(nombre: string, valor: string): void {
    const input = el<HTMLInputElement>(`input[name="${nombre}"]`);
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  async function montar(): Promise<void> {
    const api = { inscribir } as unknown as ListaEsperaApi;
    createInscripcionPage(api, listarPrestaciones)(container);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  function completar(sobre: Partial<Record<string, string>> = {}): void {
    const select = el<HTMLSelectElement>('select[name="prestacion"]');
    select.value = sobre['prestacion'] ?? '3';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    escribir('fechaDesde', sobre['fechaDesde'] ?? '2026-11-23');
    escribir('fechaHasta', sobre['fechaHasta'] ?? '2026-12-02');
    escribir('nombre', sobre['nombre'] ?? 'Ana');
    escribir('apellido', 'Ficticia');
    escribir('dni', sobre['dni'] ?? '30.123.456');
    escribir('email', 'ana@ejemplo.test');
    escribir('telefono', '+54 11 5555-0000');
  }

  function enviar(): void {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
  }

  it('lista las prestaciones y un envío válido hace un solo POST con el mensaje genérico', async () => {
    let resolver: (r: ResultadoInscripcion) => void = () => undefined;
    inscribir.mockReturnValue(new Promise((r) => (resolver = r)));
    await montar();
    expect(container.querySelectorAll('select[name="prestacion"] option')).toHaveLength(prestaciones.length + 1);
    completar();

    enviar();
    enviar(); // doble envío mientras el primero está en curso
    expect(inscribir).toHaveBeenCalledTimes(1);
    expect(inscribir).toHaveBeenCalledWith(inscripcionValida);

    resolver({ tipo: 'aceptada', mensaje: 'ignorado' });
    await vi.waitFor(() => expect(el('[role="status"]').textContent).toBe(MENSAJE_INSCRIPCION));
    expect(el<HTMLInputElement>('input[name="nombre"]').value).toBe('');
  });

  it('el rango invertido o los datos inválidos no envían ninguna petición y marcan el campo', async () => {
    await montar();
    completar({ fechaDesde: '2026-12-02', fechaHasta: '2026-11-23' });
    enviar();
    expect(inscribir).not.toHaveBeenCalled();
    expect(el('[data-error="fechaHasta"]').textContent).not.toBe('');

    completar({ dni: '12AB' });
    enviar();
    expect(inscribir).not.toHaveBeenCalled();
    expect(el('[data-error="dni"]').textContent).not.toBe('');
  });

  it('sin prestación elegida no envía y avisa', async () => {
    await montar();
    completar({ prestacion: '' });
    enviar();
    expect(inscribir).not.toHaveBeenCalled();
    expect(el('[role="alert"]').textContent).toContain('prestación');
  });

  it('el límite (429) muestra los minutos de espera y bloquea nuevos envíos', async () => {
    inscribir.mockResolvedValue({ tipo: 'limite', retryAfterSeconds: 3600 });
    await montar();
    completar();
    enviar();
    await vi.waitFor(() => expect(el('[role="alert"]').textContent).toContain('60 minutos'));
    enviar();
    expect(inscribir).toHaveBeenCalledTimes(1);
  });

  it('los errores del servidor se marcan por campo', async () => {
    inscribir.mockResolvedValue({ tipo: 'invalida', fieldErrors: [{ field: 'fechaHasta', message: 'No puede ser anterior a hoy.' }] });
    await montar();
    completar();
    enviar();
    await vi.waitFor(() => expect(el('[data-error="fechaHasta"]').textContent).toBe('No puede ser anterior a hoy.'));
  });

  it('el señuelo está oculto a personas y su valor se envía tal cual', async () => {
    inscribir.mockResolvedValue({ tipo: 'aceptada', mensaje: '' });
    await montar();
    const senuelo = el<HTMLInputElement>('input[name="sitioWeb"]');
    expect(senuelo.tabIndex).toBe(-1);
    expect(senuelo.closest('[aria-hidden="true"]')).not.toBeNull();
    completar();
    senuelo.value = 'http://spam.test';
    enviar();
    await vi.waitFor(() => expect(inscribir).toHaveBeenCalled());
    expect(inscribir.mock.calls[0]?.[0].sitioWeb).toBe('http://spam.test');
  });

  it('un error de red muestra el mensaje genérico', async () => {
    inscribir.mockRejectedValue({ kind: 'network' });
    await montar();
    completar();
    enviar();
    await vi.waitFor(() => expect(el('[role="alert"]').textContent).toContain('No pudimos'));
  });
});
