import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Accion, AccionesApi, ResultadoEjecucion, ResultadoResumen } from './acciones-api';
import { createAccionesPage } from './acciones-page';
import {
  ejecutado,
  resumenCancelar,
  resumenCancelarFueraDeLimite,
  resumenConfirmarListaEspera,
  resumenConfirmarSolicitud,
  resumenConfirmarTurno,
  resumenListaEsperaActiva,
  resumenNoPermite,
} from './test-fixtures';

const TOKEN = 'tok-secreto-123';

describe('página de acciones por token', () => {
  let container: HTMLElement;
  let obtenerResumen: ReturnType<typeof vi.fn<(token: string) => Promise<ResultadoResumen>>>;
  let ejecutar: ReturnType<typeof vi.fn<(token: string, accion: Accion) => Promise<ResultadoEjecucion>>>;

  beforeEach(() => {
    container = document.createElement('div');
    obtenerResumen = vi.fn<(token: string) => Promise<ResultadoResumen>>();
    ejecutar = vi.fn<(token: string, accion: Accion) => Promise<ResultadoEjecucion>>();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function api(): AccionesApi {
    return { obtenerResumen, ejecutar };
  }

  async function montar(): Promise<void> {
    createAccionesPage(api())(container, TOKEN);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  function boton(): HTMLButtonElement | null {
    return container.querySelector<HTMLButtonElement>('button[data-accion]');
  }

  function texto(selector: string): string {
    return container.querySelector(selector)?.textContent ?? '';
  }

  describe('resumen antes de actuar (13.2)', () => {
    it('cancelación: muestra consultorio, prestación, fecha en hora local y estado, con un único botón y sin POST', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar });
      await montar();

      expect(obtenerResumen).toHaveBeenCalledWith(TOKEN);
      expect(container.textContent).toContain('Consultorio Ficticio');
      expect(container.textContent).toContain('Consulta');
      // 13:00Z = 10:00 en Buenos Aires.
      expect(container.textContent).toContain('23/11/2026 10:00');
      expect(texto('[data-campo="estado"]')).toBe('Reservado');
      expect(container.querySelectorAll('button')).toHaveLength(1);
      expect(boton()?.textContent).toBe('Cancelar turno');
      expect(boton()?.dataset['accion']).toBe('CANCELAR_TURNO');
      expect(ejecutar).not.toHaveBeenCalled();
    });

    it('el texto del botón depende de la acción', async () => {
      obtenerResumen.mockResolvedValueOnce({ tipo: 'resumen', resumen: resumenConfirmarSolicitud });
      await montar();
      expect(boton()?.textContent).toBe('Confirmar solicitud');
      expect(texto('[data-campo="estado"]')).toBe('Pendiente de confirmación');

      container = document.createElement('div');
      obtenerResumen.mockResolvedValueOnce({ tipo: 'resumen', resumen: resumenConfirmarTurno });
      await montar();
      expect(boton()?.textContent).toBe('Confirmar asistencia');
    });

    it('FUERA_DE_LIMITE: explica que no se puede cancelar por este medio, indica el límite y no ofrece botón', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelarFueraDeLimite });
      await montar();

      expect(boton()).toBeNull();
      expect(texto('[data-no-disponible]')).toMatch(/ya no (se )?pod[ée]s cancelar|ya no se puede cancelar/i);
      expect(texto('[data-no-disponible]')).toMatch(/consultorio/i);
      // límite 11:00Z = 08:00 en Buenos Aires.
      expect(texto('[data-no-disponible]')).toContain('23/11/2026 08:00');
    });

    it('ESTADO_NO_PERMITE: dice que el turno ya no admite la acción, sin botón', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenNoPermite });
      await montar();

      expect(boton()).toBeNull();
      expect(texto('[data-no-disponible]')).toMatch(/ya no admite/);
      expect(texto('[data-campo="estado"]')).toBe('Cancelado');
    });

    it('410: página genérica con enlace a /publico y sin datos del turno', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'noDisponible' });
      await montar();

      expect(container.textContent).toMatch(/enlace.*(venci|ya fue usado|utilizado)/i);
      expect(container.textContent).toMatch(/pedir otro turno/i);
      const enlace = container.querySelector<HTMLAnchorElement>('a[href="/publico"]');
      expect(enlace).not.toBeNull();
      expect(enlace?.hasAttribute('data-link')).toBe(true);
      expect(container.querySelector('[data-campo]')).toBeNull();
      expect(boton()).toBeNull();
      expect(container.textContent).not.toContain(TOKEN);
    });

    it('un error al cargar el resumen muestra un mensaje genérico sin el token y sin consola', async () => {
      const consola = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      obtenerResumen.mockRejectedValue({ kind: 'network' });
      await montar();

      expect(container.querySelector('[role="alert"]')?.textContent).not.toBe('');
      expect(container.textContent).not.toContain(TOKEN);
      expect(boton()).toBeNull();
      expect(consola).not.toHaveBeenCalled();
    });
  });

  describe('ejecución (13.3)', () => {
    it('confirmar la solicitud: un POST, muestra turno reservado y no vuelve a ofrecer el botón', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenConfirmarSolicitud });
      ejecutar.mockResolvedValue({ tipo: 'ejecutada', resumen: ejecutado(resumenConfirmarSolicitud, 'RESERVADO') });
      await montar();

      boton()?.click();

      await vi.waitFor(() => expect(texto('[role="status"]')).toMatch(/reservado/i));
      expect(ejecutar).toHaveBeenCalledWith(TOKEN, 'CONFIRMAR_SOLICITUD');
      expect(boton()).toBeNull();
      expect(texto('[data-campo="estado"]')).toBe('Reservado');
    });

    it('confirmar asistencia y cancelar muestran su propio resultado', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenConfirmarTurno });
      ejecutar.mockResolvedValue({ tipo: 'ejecutada', resumen: ejecutado(resumenConfirmarTurno, 'CONFIRMADO') });
      await montar();
      boton()?.click();
      await vi.waitFor(() => expect(texto('[role="status"]')).toMatch(/asistencia confirmada/i));

      container = document.createElement('div');
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar });
      ejecutar.mockResolvedValue({ tipo: 'ejecutada', resumen: ejecutado(resumenCancelar, 'CANCELADO') });
      await montar();
      boton()?.click();
      await vi.waitFor(() => expect(texto('[role="status"]')).toMatch(/turno fue cancelado/i));
      expect(texto('[data-campo="estado"]')).toBe('Cancelado');
    });

    it('doble clic: se envía un solo POST y el botón se deshabilita mientras espera', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar });
      ejecutar.mockReturnValue(new Promise(() => undefined));
      await montar();

      const b = boton();
      b?.click();
      b?.click();

      expect(ejecutar).toHaveBeenCalledTimes(1);
      expect(b?.disabled).toBe(true);
    });

    it('410 al ejecutar: página genérica con enlace a /publico y sin datos del turno', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar });
      ejecutar.mockResolvedValue({ tipo: 'noDisponible' });
      await montar();

      boton()?.click();

      await vi.waitFor(() => expect(container.querySelector('a[href="/publico"]')).not.toBeNull());
      expect(container.querySelector('[data-campo]')).toBeNull();
      expect(boton()).toBeNull();
    });

    it('422/409 al ejecutar: avisa que ya no admite la acción y recarga el resumen', async () => {
      obtenerResumen.mockResolvedValueOnce({ tipo: 'resumen', resumen: resumenCancelar });
      obtenerResumen.mockResolvedValueOnce({ tipo: 'resumen', resumen: resumenNoPermite });
      ejecutar.mockResolvedValue({ tipo: 'estadoCambiado' });
      await montar();

      boton()?.click();

      await vi.waitFor(() => expect(texto('[data-campo="estado"]')).toBe('Cancelado'));
      expect(texto('[role="alert"]')).toMatch(/ya no admite/);
      expect(obtenerResumen).toHaveBeenCalledTimes(2);
      expect(boton()).toBeNull();
    });

    it('si al recargar tras el cambio el enlace ya no existe muestra la página genérica', async () => {
      obtenerResumen.mockResolvedValueOnce({ tipo: 'resumen', resumen: resumenCancelar });
      obtenerResumen.mockResolvedValueOnce({ tipo: 'noDisponible' });
      ejecutar.mockResolvedValue({ tipo: 'estadoCambiado' });
      await montar();

      boton()?.click();

      await vi.waitFor(() => expect(container.querySelector('a[href="/publico"]')).not.toBeNull());
    });

    it('un error inesperado al ejecutar muestra un mensaje genérico y permite reintentar', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar });
      ejecutar.mockRejectedValueOnce({ kind: 'network' });
      ejecutar.mockResolvedValueOnce({ tipo: 'ejecutada', resumen: ejecutado(resumenCancelar, 'CANCELADO') });
      await montar();

      boton()?.click();
      await vi.waitFor(() => expect(texto('[role="alert"]')).not.toBe(''));
      expect(boton()?.disabled).toBe(false);
      expect(container.textContent).not.toContain(TOKEN);

      boton()?.click();
      await vi.waitFor(() => expect(texto('[role="status"]')).toMatch(/cancelado/i));
      expect(ejecutar).toHaveBeenCalledTimes(2);
    });
  });

  describe('confirmación de la lista de espera (C-10)', () => {
    it('muestra el resumen de la inscripción (sin turno) con un único botón y sin POST', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenConfirmarListaEspera });
      await montar();

      expect(texto('h1')).toBe('Confirmá tu inscripción en la lista de espera');
      expect(texto('[data-campo="consultorio"]')).toBe('Consultorio Ficticio');
      expect(texto('[data-campo="prestacion"]')).toBe('Consulta');
      expect(texto('[data-campo="rango"]')).toBe('23/11/2026 al 02/12/2026');
      expect(texto('[data-campo="estado"]')).toBe('Pendiente de confirmación');
      expect(container.querySelector('[data-campo="fecha"]')).toBeNull();
      expect(boton()?.textContent).toBe('Confirmar inscripción');
      expect(ejecutar).not.toHaveBeenCalled();
    });

    it('confirmar envía un POST, muestra la inscripción activa y no vuelve a ofrecer el botón', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenConfirmarListaEspera });
      ejecutar.mockResolvedValue({ tipo: 'ejecutada', resumen: resumenListaEsperaActiva });
      await montar();

      boton()?.click();

      await vi.waitFor(() => expect(texto('[role="status"]')).toBe('Tu inscripción quedó confirmada: te avisaremos si se libera un horario.'));
      expect(ejecutar).toHaveBeenCalledWith(TOKEN, 'CONFIRMAR_LISTA_ESPERA');
      expect(texto('[data-campo="estado"]')).toBe('Activa');
      expect(boton()).toBeNull();
    });

    it('410 muestra la página genérica sin datos de la inscripción', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'noDisponible' });
      await montar();

      expect(texto('h1')).toBe('Enlace no disponible');
      expect(container.querySelector('[data-campo]')).toBeNull();
    });

    it('un resumen de turno sigue mostrando el turno (regresión)', async () => {
      obtenerResumen.mockResolvedValue({ tipo: 'resumen', resumen: resumenConfirmarTurno });
      await montar();

      expect(texto('[data-campo="fecha"]')).toContain('10:00');
      expect(container.querySelector('[data-campo="rango"]')).toBeNull();
    });
  });
});
