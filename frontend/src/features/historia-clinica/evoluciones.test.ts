import { describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { clickEnBoton, esperar, requerir } from './dom-test-helpers';
import { crearEvoluciones } from './evoluciones';
import type { HistoriaApi } from './historia-api';
import { evolucion } from './test-fixtures';

const AHORA = (): Date => new Date('2026-11-27T02:00:00Z'); // 26/11 23:00 en el consultorio

function apiDoble() {
  return {
    crearEvolucion: vi.fn<HistoriaApi['crearEvolucion']>(),
    editarEvolucion: vi.fn<HistoriaApi['editarEvolucion']>(),
    listarEvoluciones: vi.fn<HistoriaApi['listarEvoluciones']>(),
  };
}

function enviarAlta(vista: HTMLElement): void {
  requerir<HTMLFormElement>(vista, '[data-alta-evolucion]').dispatchEvent(new Event('submit', { cancelable: true }));
}

describe('evoluciones', () => {
  it('lista en el orden de la API y muestra "Sin evoluciones registradas" si no hay', () => {
    const api = apiDoble();
    const vacia = crearEvoluciones({ api, pacienteId: 7, evoluciones: [], ahora: AHORA });
    expect(vacia.textContent).toContain('Sin evoluciones registradas');

    const vista = crearEvoluciones({
      api,
      pacienteId: 7,
      evoluciones: [evolucion(3, { fecha: '2026-10-05' }), evolucion(1)],
      ahora: AHORA,
    });
    const ids = [...vista.querySelectorAll<HTMLElement>('li[data-evolucion-id]')].map((li) => li.dataset['evolucionId']);
    expect(ids).toEqual(['3', '1']);
    expect(vista.textContent).toContain('05/10/2026 — Laura Gómez');
  });

  it('da de alta con fecha de hoy en el consultorio y agrega al final', async () => {
    const api = apiDoble();
    api.crearEvolucion.mockResolvedValue(evolucion(2, { nota: 'Control post obturación' }));
    const vista = crearEvoluciones({ api, pacienteId: 7, evoluciones: [evolucion(1)], ahora: AHORA });

    expect(requerir<HTMLInputElement>(vista, 'input[name="fecha"]').value).toBe('2026-11-26');
    requerir<HTMLTextAreaElement>(vista, 'textarea[name="nota"]').value = 'Control post obturación';
    enviarAlta(vista);
    await esperar();

    expect(api.crearEvolucion).toHaveBeenCalledWith(7, { nota: 'Control post obturación', fecha: '2026-11-26' });
    const notas = [...vista.querySelectorAll('[data-nota]')].map((n) => n.textContent);
    expect(notas.at(-1)).toBe('Control post obturación');
    expect(requerir<HTMLTextAreaElement>(vista, 'textarea[name="nota"]').value).toBe('');
  });

  it('no llama a la API con una nota en blanco', () => {
    const api = apiDoble();
    const vista = crearEvoluciones({ api, pacienteId: 7, evoluciones: [], ahora: AHORA });

    requerir<HTMLTextAreaElement>(vista, 'textarea[name="nota"]').value = '   ';
    enviarAlta(vista);

    expect(api.crearEvolucion).not.toHaveBeenCalled();
    expect(vista.textContent).toContain('La nota es obligatoria.');
  });

  it('ofrece "Editar" solo en las editables', () => {
    const vista = crearEvoluciones({
      api: apiDoble(),
      pacienteId: 7,
      evoluciones: [evolucion(1, { editable: false }), evolucion(2)],
      ahora: AHORA,
    });
    expect(requerir(vista, 'li[data-evolucion-id="1"]').querySelector('button')).toBeNull();
    expect(requerir(vista, 'li[data-evolucion-id="2"]').textContent).toContain('Editar');
  });

  it('edita una evolución editable', async () => {
    const api = apiDoble();
    api.editarEvolucion.mockResolvedValue(evolucion(2, { nota: 'Corregida', editadaEn: '2026-11-26T20:00:00Z' }));
    const vista = crearEvoluciones({ api, pacienteId: 7, evoluciones: [evolucion(2)], ahora: AHORA });

    clickEnBoton(requerir(vista, 'li[data-evolucion-id="2"]'), 'Editar');
    requerir<HTMLTextAreaElement>(vista, 'textarea[name="nota-edicion"]').value = 'Corregida';
    clickEnBoton(vista, 'Guardar');
    await esperar();

    expect(api.editarEvolucion).toHaveBeenCalledWith(7, 2, { nota: 'Corregida' });
    expect(requerir(vista, 'li[data-evolucion-id="2"]').textContent).toContain('Corregida');
    expect(requerir(vista, 'li[data-evolucion-id="2"]').textContent).toContain('(editada)');
  });

  it('si la ventana venció al guardar muestra el problema, recarga y deja de ofrecer "Editar"', async () => {
    const api = apiDoble();
    const error: ApiError = { kind: 'http', status: 422, title: 'Regla de negocio incumplida', fieldErrors: [], extensions: {} };
    api.editarEvolucion.mockRejectedValue(error);
    api.listarEvoluciones.mockResolvedValue([evolucion(2, { editable: false })]);
    const vista = crearEvoluciones({ api, pacienteId: 7, evoluciones: [evolucion(2)], ahora: AHORA });

    clickEnBoton(requerir(vista, 'li[data-evolucion-id="2"]'), 'Editar');
    requerir<HTMLTextAreaElement>(vista, 'textarea[name="nota-edicion"]').value = 'Tarde';
    clickEnBoton(vista, 'Guardar');
    await esperar();
    await esperar();

    expect(vista.textContent).toContain('Regla de negocio incumplida');
    expect(api.listarEvoluciones).toHaveBeenCalledWith(7);
    expect(requerir(vista, 'li[data-evolucion-id="2"]').querySelector('button')).toBeNull();
  });
});
