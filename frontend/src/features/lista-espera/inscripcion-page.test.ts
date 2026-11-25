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

  /** 12:00 del lunes 23/11/2026 en el consultorio: las fechas de `completar()` quedan vigentes. */
  const LUNES_MEDIODIA = new Date('2026-11-23T15:00:00Z');

  async function montar(ahora: Date = LUNES_MEDIODIA): Promise<void> {
    const api = { inscribir } as unknown as ListaEsperaApi;
    createInscripcionPage(api, listarPrestaciones, () => ahora)(container);
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

  it('prestación desactivada (422 en prestacionId): el mensaje aparece junto al selector y lo marca inválido', async () => {
    inscribir.mockResolvedValue({ tipo: 'invalida', fieldErrors: [{ field: 'prestacionId', message: 'La prestación está desactivada.' }] });
    await montar();
    completar();
    enviar();
    await vi.waitFor(() => expect(el('[data-error="prestacionId"]').textContent).toBe('La prestación está desactivada.'));
    expect(el('select[name="prestacion"]').getAttribute('aria-invalid')).toBe('true');
  });

  it('sin prestación elegida el aviso también queda junto al selector, y se limpia al reintentar', async () => {
    await montar();
    completar({ prestacion: '' });
    enviar();
    expect(el('[data-error="prestacionId"]').textContent).toBe('Elegí una prestación.');
    expect(el('select[name="prestacion"]').getAttribute('aria-invalid')).toBe('true');

    inscribir.mockResolvedValue({ tipo: 'aceptada', mensaje: '' });
    completar();
    enviar();
    expect(el('[data-error="prestacionId"]').textContent).toBe('');
    expect(el('select[name="prestacion"]').hasAttribute('aria-invalid')).toBe(false);
  });

  it('prestación inexistente (404): lo informa, vuelve a pedir las prestaciones y deja el selector sin elección', async () => {
    inscribir.mockResolvedValue({ tipo: 'prestacionNoDisponible' });
    await montar();
    expect(listarPrestaciones).toHaveBeenCalledTimes(1);
    completar();
    enviar();
    await vi.waitFor(() => expect(el('[data-error="prestacionId"]').textContent).toBe('La prestación elegida ya no está disponible. Elegí otra.'));
    expect(el<HTMLSelectElement>('select[name="prestacion"]').value).toBe('');
    await vi.waitFor(() => expect(listarPrestaciones).toHaveBeenCalledTimes(2));
    // La lista recargada no duplica opciones.
    await vi.waitFor(() => expect(container.querySelectorAll('select[name="prestacion"] option')).toHaveLength(prestaciones.length + 1));
    expect(el<HTMLInputElement>('input[name="nombre"]').value).toBe('Ana');
  });

  it('fecha hasta anterior a hoy: marca el error y no envía ninguna petición', async () => {
    await montar();
    completar({ fechaDesde: '2026-11-20', fechaHasta: '2026-11-22' });
    enviar();
    expect(inscribir).not.toHaveBeenCalled();
    expect(el('[data-error="fechaHasta"]').textContent).toBe('No puede ser anterior a hoy.');
  });

  it('fecha hasta igual a hoy es válida y se envía', async () => {
    inscribir.mockResolvedValue({ tipo: 'aceptada', mensaje: '' });
    await montar();
    completar({ fechaDesde: '2026-11-23', fechaHasta: '2026-11-23' });
    enviar();
    expect(inscribir).toHaveBeenCalledTimes(1);
  });

  it('las fechas ofrecen hoy como mínimo', async () => {
    await montar();
    expect(el<HTMLInputElement>('input[name="fechaDesde"]').min).toBe('2026-11-23');
    expect(el<HTMLInputElement>('input[name="fechaHasta"]').min).toBe('2026-11-23');
  });

  it('"hoy" es el del consultorio: a las 23:30 del 24/11 local (02:30Z del 25) hoy sigue siendo 24/11', async () => {
    inscribir.mockResolvedValue({ tipo: 'aceptada', mensaje: '' });
    await montar(new Date('2026-11-25T02:30:00Z'));
    expect(el<HTMLInputElement>('input[name="fechaHasta"]').min).toBe('2026-11-24');
    completar({ fechaDesde: '2026-11-24', fechaHasta: '2026-11-24' });
    enviar();
    expect(inscribir).toHaveBeenCalledTimes(1);
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
