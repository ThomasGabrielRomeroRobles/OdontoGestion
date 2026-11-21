import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Prestacion, PrestacionInput } from '../agenda-config-api';
import type { Consultorio } from '../config-api';
import {
  boton,
  consultorioCon,
  consultorioFicticio,
  enviar,
  error422,
  errorDe,
  escribir,
  input,
  prestacionesFicticias,
} from '../test-fixtures';
import { createPrestacionesSection, type PrestacionesSectionDeps } from './prestaciones-section';

describe('sección Prestaciones (9.1)', () => {
  let container: HTMLElement;
  let deps: {
    [K in keyof PrestacionesSectionDeps]: ReturnType<typeof vi.fn<PrestacionesSectionDeps[K]>>;
  };

  beforeEach(() => {
    container = document.createElement('div');
    deps = {
      listarPrestaciones: vi.fn<() => Promise<Prestacion[]>>().mockResolvedValue([...prestacionesFicticias]),
      crearPrestacion: vi.fn<(i: PrestacionInput) => Promise<Prestacion>>(),
      actualizarPrestacion: vi.fn<(id: number, i: PrestacionInput) => Promise<Prestacion>>(),
      desactivarPrestacion: vi.fn<(id: number) => Promise<Prestacion>>(),
      activarPrestacion: vi.fn<(id: number) => Promise<Prestacion>>(),
      obtenerConsultorio: vi.fn<() => Promise<Consultorio>>().mockResolvedValue(consultorioFicticio),
      confirmar: vi.fn<(mensaje: string) => boolean>().mockReturnValue(true),
    };
  });

  async function montar(): Promise<void> {
    createPrestacionesSection(deps)(container);
    await vi.waitFor(() => expect(filas().length).toBeGreaterThan(0));
    await vi.waitFor(() => expect(deps.obtenerConsultorio).toHaveBeenCalled());
  }

  const filas = (): HTMLElement[] => [...container.querySelectorAll<HTMLElement>('li[data-id]')];
  const fila = (nombre: string): HTMLElement => {
    const f = filas().find((li) => li.querySelector('[data-col="nombre"]')?.textContent === nombre);
    if (f === undefined) throw new Error(`sin fila ${nombre}`);
    return f;
  };
  const form = (): HTMLFormElement | null => container.querySelector('form[data-form="prestacion"]');
  const alerta = (): string => container.querySelector('[role="alert"]')?.textContent ?? '';

  it('lista nombre, duración y estado; inactiva ofrece Activar', async () => {
    await montar();
    expect(filas().map((li) => li.querySelector('[data-col="nombre"]')?.textContent)).toEqual([
      'Consulta',
      'Control',
      'Endodoncia',
    ]);
    expect(fila('Consulta').textContent).toContain('30 min');
    expect(fila('Control').textContent).toContain('Inactiva');
    expect(boton(fila('Control'), 'Activar')).toBeDefined();
    expect(boton(fila('Consulta'), 'Desactivar')).toBeDefined();
  });

  it('lista vacía: indica que no hay prestaciones y ofrece crear', async () => {
    deps.listarPrestaciones.mockResolvedValue([]);
    createPrestacionesSection(deps)(container);
    await vi.waitFor(() => expect(container.textContent).toContain('No hay prestaciones cargadas.'));
    expect(form()).not.toBeNull();
  });

  it('alta: se refleja en el listado como activa, ordenada por nombre', async () => {
    deps.crearPrestacion.mockResolvedValue({ id: 9, nombre: 'Blanqueamiento', duracionMin: 60, activa: true });
    await montar();
    escribir(container, 'nombre', 'Blanqueamiento');
    escribir(container, 'duracionMin', '60');
    enviar(form());

    await vi.waitFor(() => expect(fila('Blanqueamiento').textContent).toContain('Activa'));
    expect(deps.crearPrestacion).toHaveBeenCalledWith({ nombre: 'Blanqueamiento', duracionMin: 60 });
    expect(filas()[0]?.querySelector('[data-col="nombre"]')?.textContent).toBe('Blanqueamiento');
    expect(input(container, 'nombre').value).toBe('');
  });

  it('valida la duración con la granularidad del consultorio', async () => {
    deps.obtenerConsultorio.mockResolvedValue(consultorioCon({ granularidadMin: 30 }));
    await montar();
    escribir(container, 'nombre', 'Limpieza');
    escribir(container, 'duracionMin', '45');
    enviar(form());

    expect(deps.crearPrestacion).not.toHaveBeenCalled();
    expect(errorDe(container, 'duracionMin')).toBe('La duración debe ser múltiplo de 30 minutos.');
  });

  it('nombre vacío no envía', async () => {
    await montar();
    escribir(container, 'duracionMin', '30');
    enviar(form());
    expect(deps.crearPrestacion).not.toHaveBeenCalled();
    expect(errorDe(container, 'nombre')).toBe('El nombre es obligatorio.');
  });

  it('desactivar con confirmación: llama a la API y la fila pasa a inactiva', async () => {
    deps.desactivarPrestacion.mockResolvedValue({ id: 3, nombre: 'Endodoncia', duracionMin: 90, activa: false });
    await montar();
    boton(fila('Endodoncia'), 'Desactivar').click();

    await vi.waitFor(() => expect(fila('Endodoncia').textContent).toContain('Inactiva'));
    expect(deps.confirmar).toHaveBeenCalledTimes(1);
    expect(deps.desactivarPrestacion).toHaveBeenCalledWith(3);
  });

  it('desactivar cancelado no llama a la API', async () => {
    deps.confirmar.mockReturnValue(false);
    await montar();
    boton(fila('Endodoncia'), 'Desactivar').click();
    expect(deps.desactivarPrestacion).not.toHaveBeenCalled();
  });

  it('activar una inactiva', async () => {
    deps.activarPrestacion.mockResolvedValue({ id: 2, nombre: 'Control', duracionMin: 15, activa: true });
    await montar();
    boton(fila('Control'), 'Activar').click();
    await vi.waitFor(() => expect(boton(fila('Control'), 'Desactivar')).toBeDefined());
    expect(deps.activarPrestacion).toHaveBeenCalledWith(2);
  });

  it('editar: carga el formulario, envía PUT y actualiza la fila', async () => {
    deps.actualizarPrestacion.mockResolvedValue({ id: 1, nombre: 'Consulta', duracionMin: 45, activa: true });
    await montar();
    boton(fila('Consulta'), 'Editar').click();
    expect(input(container, 'nombre').value).toBe('Consulta');
    expect(input(container, 'duracionMin').value).toBe('30');
    escribir(container, 'duracionMin', '45');
    enviar(form());

    await vi.waitFor(() => expect(fila('Consulta').textContent).toContain('45 min'));
    expect(deps.actualizarPrestacion).toHaveBeenCalledWith(1, { nombre: 'Consulta', duracionMin: 45 });
  });

  it('422 del servidor: error junto al campo y se conservan los valores', async () => {
    deps.crearPrestacion.mockRejectedValue(error422('nombre', 'Ya existe una prestación con ese nombre'));
    await montar();
    escribir(container, 'nombre', 'consulta');
    escribir(container, 'duracionMin', '30');
    enviar(form());

    await vi.waitFor(() => expect(errorDe(container, 'nombre')).toBe('Ya existe una prestación con ese nombre'));
    expect(input(container, 'nombre').value).toBe('consulta');
  });

  it('falla de red al guardar: mensaje de conexión', async () => {
    deps.crearPrestacion.mockRejectedValue({ kind: 'network' });
    await montar();
    escribir(container, 'nombre', 'Blanqueamiento');
    escribir(container, 'duracionMin', '60');
    enviar(form());
    await vi.waitFor(() => expect(alerta()).toContain('No se pudo conectar'));
  });

  it('si no se conoce la granularidad, valida solo entero positivo y deja decidir al servidor', async () => {
    deps.obtenerConsultorio.mockRejectedValue({ kind: 'network' });
    deps.crearPrestacion.mockResolvedValue({ id: 9, nombre: 'Rara', duracionMin: 40, activa: true });
    await montar();
    escribir(container, 'nombre', 'Rara');
    escribir(container, 'duracionMin', '40');
    enviar(form());
    await vi.waitFor(() => expect(deps.crearPrestacion).toHaveBeenCalled());
  });
});
