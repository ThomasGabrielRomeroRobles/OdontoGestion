import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Consultorio, ConsultorioEditable } from '../config-api';
import {
  consultorioFicticio,
  diferida,
  enviar,
  error422,
  errorDe,
  escribir,
  input,
} from '../test-fixtures';
import { createConsultorioSection } from './consultorio-section';

describe('sección Consultorio (8.1)', () => {
  let container: HTMLElement;
  let obtener: ReturnType<typeof vi.fn<() => Promise<Consultorio>>>;
  let actualizar: ReturnType<typeof vi.fn<(input: ConsultorioEditable) => Promise<Consultorio>>>;

  beforeEach(() => {
    container = document.createElement('div');
    obtener = vi.fn<() => Promise<Consultorio>>().mockResolvedValue(consultorioFicticio);
    actualizar = vi.fn<(input: ConsultorioEditable) => Promise<Consultorio>>();
  });

  async function montar(): Promise<void> {
    createConsultorioSection({ obtenerConsultorio: obtener, actualizarConsultorio: actualizar })(container);
    await vi.waitFor(() => expect(input(container, 'nombre').value).toBe('Consultorio Demo'));
  }

  const form = (): HTMLFormElement | null => container.querySelector('form');

  it('carga inicial: los campos muestran los valores de la API', async () => {
    await montar();
    expect(input(container, 'granularidadMin').value).toBe('15');
    expect(input(container, 'recordatorioHorasAntes').value).toBe('24');
    expect(input(container, 'emailContacto').value).toBe('contacto@consultorio.example');
    expect(container.textContent).toContain('America/Argentina/Buenos_Aires');
  });

  it('informa que los cambios de parámetros no son retroactivos', async () => {
    await montar();
    expect(container.textContent).toContain('aplican a operaciones nuevas y no a turnos ya creados');
  });

  it('guardado exitoso: envía PUT con el valor nuevo y confirma', async () => {
    actualizar.mockResolvedValue({ ...consultorioFicticio, recordatorioHorasAntes: 48 });
    await montar();
    escribir(container, 'recordatorioHorasAntes', '48');
    enviar(form());

    await vi.waitFor(() => expect(container.querySelector('[role="status"]')?.textContent).toBe('Cambios guardados.'));
    const { zonaHoraria: _z, ...esperado } = { ...consultorioFicticio, recordatorioHorasAntes: 48 };
    expect(actualizar).toHaveBeenCalledWith(esperado);
  });

  it('parámetro 0: no envía e indica que debe ser mayor que cero', async () => {
    await montar();
    escribir(container, 'granularidadMin', '0');
    enviar(form());

    expect(actualizar).not.toHaveBeenCalled();
    expect(errorDe(container, 'granularidadMin')).toBe('Debe ser un número entero mayor que cero.');
  });

  it('nombre vacío: no envía', async () => {
    await montar();
    escribir(container, 'nombre', '  ');
    enviar(form());

    expect(actualizar).not.toHaveBeenCalled();
    expect(errorDe(container, 'nombre')).toBe('El nombre es obligatorio.');
  });

  it('422 del servidor: el error aparece junto al campo y se conservan los valores', async () => {
    actualizar.mockRejectedValue(error422('emailContacto', 'Email inválido'));
    await montar();
    escribir(container, 'emailContacto', 'no-es-email');
    enviar(form());

    await vi.waitFor(() => expect(errorDe(container, 'emailContacto')).toBe('Email inválido'));
    expect(input(container, 'emailContacto').value).toBe('no-es-email');
  });

  it('el botón queda deshabilitado mientras se guarda', async () => {
    const pendiente = diferida<Consultorio>();
    actualizar.mockReturnValue(pendiente.promise);
    await montar();
    const guardar = container.querySelector<HTMLButtonElement>('button[type="submit"]');
    enviar(form());

    expect(guardar?.disabled).toBe(true);
    pendiente.resolve(consultorioFicticio);
    await vi.waitFor(() => expect(guardar?.disabled).toBe(false));
  });

  it('si la carga falla muestra el error general', async () => {
    obtener.mockRejectedValue({ kind: 'network' });
    createConsultorioSection({ obtenerConsultorio: obtener, actualizarConsultorio: actualizar })(container);

    await vi.waitFor(() =>
      expect(container.querySelector('[role="alert"]')?.textContent).toContain('No se pudo conectar con el servidor'),
    );
  });
});
