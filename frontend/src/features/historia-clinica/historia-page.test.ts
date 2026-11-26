import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { crearHistoriaApiMock } from '../../dev/historia-mocks';
import { clickEnBoton, esperar, requerir } from './dom-test-helpers';
import type { HistoriaApi } from './historia-api';
import { createHistoriaPage } from './historia-page';
import { historia } from './test-fixtures';

const AHORA = (): Date => new Date('2026-11-26T13:00:00Z');

function apiCon(obtener: HistoriaApi['obtener']): HistoriaApi {
  return { ...crearHistoriaApiMock(AHORA), obtener };
}

describe('pantalla de historia clínica', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('carga la ficha y muestra encabezado, anamnesis, odontograma y evoluciones', async () => {
    const obtener = vi.fn<HistoriaApi['obtener']>().mockResolvedValue(historia());
    const container = document.createElement('div');

    createHistoriaPage(apiCon(obtener), AHORA)(container, '7');
    await esperar();

    expect(obtener).toHaveBeenCalledWith(7);
    expect(requerir(container, 'h1').textContent).toBe('Historia clínica — Pérez, Ana');
    expect(container.querySelector('[data-anamnesis]')).not.toBeNull();
    expect(container.querySelectorAll('[data-odontograma] button[data-fdi]')).toHaveLength(32);
    expect(container.textContent).toContain('Sin evoluciones registradas');
  });

  it('con id inválido muestra "Paciente no encontrado" sin pedir datos', () => {
    const obtener = vi.fn<HistoriaApi['obtener']>();
    const container = document.createElement('div');

    createHistoriaPage(apiCon(obtener), AHORA)(container, 'abc');

    expect(container.textContent).toBe('Paciente no encontrado');
    expect(obtener).not.toHaveBeenCalled();
  });

  it('ante un 404 de la API muestra "Paciente no encontrado"', async () => {
    const error: ApiError = { kind: 'http', status: 404, title: 'Recurso no encontrado', fieldErrors: [], extensions: {} };
    const container = document.createElement('div');

    createHistoriaPage(apiCon(vi.fn<HistoriaApi['obtener']>().mockRejectedValue(error)), AHORA)(container, '99');
    await esperar();

    expect(requerir(container, 'h1').textContent).toBe('Paciente no encontrado');
  });

  it('no escribe datos clínicos en storage, consola ni URL al guardar', async () => {
    const setLocal = vi.spyOn(Storage.prototype, 'setItem');
    const consola = ['log', 'info', 'warn', 'error', 'debug'] as const;
    const espias = consola.map((m) => vi.spyOn(console, m));
    history.replaceState(null, '', '/pacientes/7/historia');
    const container = document.createElement('div');
    createHistoriaPage(crearHistoriaApiMock(AHORA), AHORA)(container, '7');
    await esperar();

    requerir<HTMLTextAreaElement>(container, 'textarea[name="alergias"]').value = 'AlergiaFicticiaXYZ';
    requerir<HTMLFormElement>(container, '[data-anamnesis] form').dispatchEvent(new Event('submit', { cancelable: true }));
    requerir<HTMLButtonElement>(container, 'button[data-fdi="16"]').click();
    clickEnBoton(requerir(container, '[data-editor-pieza]'), 'Guardar');
    requerir<HTMLTextAreaElement>(container, 'textarea[name="nota"]').value = 'NotaFicticiaXYZ';
    requerir<HTMLFormElement>(container, '[data-alta-evolucion]').dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    expect(container.textContent).toContain('NotaFicticiaXYZ');
    expect(location.pathname + location.search).toBe('/pacientes/7/historia');
    expect(setLocal).not.toHaveBeenCalled();
    espias.forEach((espia) => expect(espia).not.toHaveBeenCalled());
  });
});
