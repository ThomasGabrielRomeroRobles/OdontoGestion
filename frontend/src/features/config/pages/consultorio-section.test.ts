import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Consultorio, ConsultorioEditable } from '../config-api';
import {
  consultorioCon,
  consultorioFicticio,
  diferida,
  enviar,
  error400,
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
  const alerta = (): string => container.querySelector('[role="alert"]')?.textContent ?? '';

  /** Cuerpo que el formulario debe enviar con los datos ficticios sin cambios (contrato real de C-02). */
  function cuerpoEsperado(parametros: Partial<Consultorio['parametros']> = {}): ConsultorioEditable {
    const { zonaHoraria: _zona, ...editable } = consultorioCon(parametros);
    return editable;
  }

  it('carga inicial: los campos muestran los valores de la API, incluidos los parámetros anidados', async () => {
    await montar();
    expect(input(container, 'granularidadMin').value).toBe('15');
    expect(input(container, 'limiteCancelacionHoras').value).toBe('2');
    expect(input(container, 'recordatorioHorasAntes').value).toBe('24');
    expect(input(container, 'emailContacto').value).toBe('contacto@consultorio.example');
    expect(container.textContent).toContain('America/Argentina/Buenos_Aires');
  });

  it('muestra la tolerancia de ausente con su valor', async () => {
    obtener.mockResolvedValue(consultorioCon({ toleranciaAusenteMin: 20 }));
    await montar();
    const tolerancia = input(container, 'toleranciaAusenteMin');
    expect(tolerancia.value).toBe('20');
    expect(container.querySelector(`label[for="${tolerancia.id}"]`)?.textContent).toBe('Tolerancia de ausente (min)');
  });

  it('informa que los cambios de parámetros no son retroactivos', async () => {
    await montar();
    expect(container.textContent).toContain('aplican a operaciones nuevas y no a turnos ya creados');
  });

  it('guardado exitoso: envía PUT con los 7 parámetros anidados en `parametros` y confirma', async () => {
    actualizar.mockResolvedValue(consultorioCon({ recordatorioHorasAntes: 48 }));
    await montar();
    escribir(container, 'recordatorioHorasAntes', '48');
    enviar(form());

    await vi.waitFor(() => expect(container.querySelector('[role="status"]')?.textContent).toBe('Cambios guardados.'));
    expect(actualizar).toHaveBeenCalledWith(cuerpoEsperado({ recordatorioHorasAntes: 48 }));
    const enviado = actualizar.mock.calls[0]?.[0];
    expect(Object.keys(enviado?.parametros ?? {})).toHaveLength(7);
    expect(enviado).not.toHaveProperty('granularidadMin');
    expect(enviado).not.toHaveProperty('zonaHoraria');
  });

  it('envía la tolerancia de ausente editada', async () => {
    actualizar.mockResolvedValue(consultorioCon({ toleranciaAusenteMin: 25 }));
    await montar();
    escribir(container, 'toleranciaAusenteMin', '25');
    enviar(form());

    await vi.waitFor(() => expect(actualizar).toHaveBeenCalledWith(cuerpoEsperado({ toleranciaAusenteMin: 25 })));
  });

  it('parámetro 0: no envía e indica que debe ser mayor que cero', async () => {
    await montar();
    escribir(container, 'granularidadMin', '0');
    enviar(form());

    expect(actualizar).not.toHaveBeenCalled();
    expect(errorDe(container, 'granularidadMin')).toBe('Debe ser un número entero mayor que cero.');
  });

  it.each(['0', '', '1.5'])('tolerancia de ausente "%s": no envía', async (valor) => {
    await montar();
    escribir(container, 'toleranciaAusenteMin', valor);
    enviar(form());

    expect(actualizar).not.toHaveBeenCalled();
    expect(errorDe(container, 'toleranciaAusenteMin')).toBe('Debe ser un número entero mayor que cero.');
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

  it('422 con `parametros.granularidadMin`: se muestra junto al input y no en el aviso general (D10)', async () => {
    actualizar.mockRejectedValue(error422('parametros.granularidadMin', 'Debe ser mayor que cero.'));
    await montar();
    enviar(form());

    await vi.waitFor(() => expect(errorDe(container, 'granularidadMin')).toBe('Debe ser mayor que cero.'));
    expect(alerta()).toBe('');
  });

  it('422 con varios parámetros: cada error junto a su input, incluida la tolerancia', async () => {
    actualizar.mockRejectedValue({
      kind: 'http',
      status: 422,
      title: 'Regla de negocio incumplida',
      fieldErrors: [
        { field: 'parametros.limiteCancelacionHoras', message: 'Debe ser mayor que cero.' },
        { field: 'parametros.toleranciaAusenteMin', message: 'Debe ser mayor que cero.' },
      ],
      extensions: {},
    });
    await montar();
    enviar(form());

    await vi.waitFor(() => expect(errorDe(container, 'toleranciaAusenteMin')).toBe('Debe ser mayor que cero.'));
    expect(errorDe(container, 'limiteCancelacionHoras')).toBe('Debe ser mayor que cero.');
    expect(alerta()).toBe('');
  });

  it('400 con `parametros.recordatorioHorasAntes`: también se muestra junto al input', async () => {
    actualizar.mockRejectedValue(error400('parametros.recordatorioHorasAntes', 'Es obligatorio.'));
    await montar();
    enviar(form());

    await vi.waitFor(() => expect(errorDe(container, 'recordatorioHorasAntes')).toBe('Es obligatorio.'));
    expect(alerta()).toBe('');
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

    await vi.waitFor(() => expect(alerta()).toContain('No se pudo conectar con el servidor'));
  });
});
