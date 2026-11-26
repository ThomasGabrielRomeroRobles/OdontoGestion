import { describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';

const http = (status: number, title: string): ApiError => ({ kind: 'http', status, title, fieldErrors: [], extensions: {} });
import type { PacientesApi, PacienteResponse } from './pacientes-api';
import { createPacienteDetallePage, createPacientesListPage, filtroDeBusqueda } from './pacientes-page';

const p = (id: number, apellido: string, ausencias = 0): PacienteResponse => ({
  id, nombre: 'Ana', apellido, dni: '30111222', fechaNacimiento: null, telefono: '1155550101',
  email: `${apellido.toLowerCase()}@ejemplo.test`, direccion: null, cantidadAusencias: ausencias, activo: true,
});
const flush = (): Promise<void> => new Promise((r) => setTimeout(r, 0));
function api(parcial: Partial<PacientesApi>): PacientesApi {
  return {
    buscar: () => Promise.resolve([]),
    obtener: () => Promise.resolve(p(1, 'Gómez')),
    ausencias: () => Promise.resolve({ cantidad: 0, ausencias: [] }),
    ...parcial,
  };
}

describe('filtroDeBusqueda', () => {
  it('distingue DNI, texto y vacío', () => {
    expect(filtroDeBusqueda('30.111.222')).toEqual({ dni: '30.111.222' });
    expect(filtroDeBusqueda('30111222')).toEqual({ dni: '30111222' });
    expect(filtroDeBusqueda(' gómez ')).toEqual({ q: 'gómez' });
    expect(filtroDeBusqueda('')).toEqual({});
  });
});

describe('lista de pacientes', () => {
  it('carga el listado inicial y dibuja filas con badge de ausencias', async () => {
    const buscar = vi.fn().mockResolvedValue([p(1, 'Gómez', 2), p(2, 'Pérez')]);
    const c = document.createElement('div');
    createPacientesListPage(api({ buscar }))(c);
    await flush();
    expect(buscar).toHaveBeenCalledWith({});
    expect(c.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(c.querySelector('tr[data-id="1"] .badge')?.textContent).toBe('2');
    expect(c.querySelector('tr[data-id="1"] a')?.getAttribute('href')).toBe('/pacientes/1');
  });

  it('buscar dispara la consulta con el filtro', async () => {
    const buscar = vi.fn().mockResolvedValue([]);
    const c = document.createElement('div');
    createPacientesListPage(api({ buscar }))(c);
    await flush();
    const input = c.querySelector('input') as HTMLInputElement;
    input.value = 'Gómez';
    c.querySelector('form')?.dispatchEvent(new Event('submit', { cancelable: true }));
    await flush();
    expect(buscar).toHaveBeenLastCalledWith({ q: 'Gómez' });
  });

  it('muestra estado vacío', async () => {
    const c = document.createElement('div');
    createPacientesListPage(api({}))(c);
    await flush();
    expect(c.textContent).toContain('No se encontraron pacientes.');
    expect(c.querySelector('table')?.hidden).toBe(true);
  });

  it('muestra el error de la API', async () => {
    const buscar = vi.fn().mockRejectedValue(http(400, 'Solicitud inválida'));
    const c = document.createElement('div');
    createPacientesListPage(api({ buscar }))(c);
    await flush();
    expect(c.querySelector('[role="alert"]')?.textContent).not.toBe('');
    expect(c.dataset['estadoCarga']).toBe('error');
  });
});

describe('ficha de paciente', () => {
  it('renderiza datos, ausencias y el hueco de historia clínica', async () => {
    const c = document.createElement('div');
    createPacienteDetallePage(
      api({
        ausencias: () =>
          Promise.resolve({ cantidad: 1, ausencias: [{ turnoId: 9, inicio: '2026-11-23T13:00:00Z', prestacionNombre: 'Limpieza' }] }),
      }),
    )(c, '1');
    await flush();
    expect(c.querySelector('h1')?.textContent).toBe('Gómez, Ana');
    expect(c.querySelector('[data-campo="dni"]')?.textContent).toBe('30111222');
    expect(c.querySelector('li[data-turno-id="9"]')?.textContent).toContain('Limpieza');
    expect(c.querySelector('li[data-turno-id="9"]')?.textContent).toContain('23/11/2026 10:00');
    expect(c.textContent).toContain('próximamente (C-05)');
  });

  it('sin ausencias y con error', async () => {
    const c = document.createElement('div');
    createPacienteDetallePage(api({}))(c, '1');
    await flush();
    expect(c.textContent).toContain('Sin ausencias registradas.');
    const c2 = document.createElement('div');
    createPacienteDetallePage(api({ obtener: () => Promise.reject(http(404, 'No encontrado')) }))(c2, '1');
    await flush();
    expect(c2.querySelector('[role="alert"]')?.textContent).not.toBe('');
    const c3 = document.createElement('div');
    createPacienteDetallePage(api({}))(c3, 'abc');
    expect(c3.querySelector('[role="alert"]')?.textContent).toBe('Paciente inválido.');
  });
});
