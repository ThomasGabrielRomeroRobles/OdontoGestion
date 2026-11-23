import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Turno } from '../agenda-api';
import { crearAgendaDepsDoble, type AgendaDepsDoble } from '../deps-test-helpers';
import type { AgendaDeps } from '../deps';
import { errorHttp, flush, turnoEn, turnoFicticio } from '../test-fixtures';
import type { AlCambiar } from './agenda-page';
import { createTurnoDetalle } from './turno-detalle';

const INICIO = Date.parse('2026-11-24T12:00:00Z'); // 09:00 en el consultorio
const MIN = 60_000;

describe('detalle del turno (grupo 5)', () => {
  let host: HTMLElement;
  let deps: AgendaDepsDoble & AgendaDeps;
  let alCambiar: ReturnType<typeof vi.fn<AlCambiar>>;

  beforeEach(() => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    host = document.createElement('section');
    document.body.replaceChildren(host);
    alCambiar = vi.fn<AlCambiar>();
  });

  async function abrir(turno: Turno, ahoraMs: number): Promise<void> {
    deps = crearAgendaDepsDoble(new Date(ahoraMs));
    createTurnoDetalle(deps)(host, turno, alCambiar);
    await flush();
  }

  const botones = (): HTMLButtonElement[] => [...host.querySelectorAll<HTMLButtonElement>('[data-acciones] button')];
  const habilitados = (): string[] => botones().filter((b) => !b.disabled).map((b) => b.textContent ?? '');
  const boton = (texto: string): HTMLButtonElement => {
    const b = [...host.querySelectorAll<HTMLButtonElement>('button')].find((x) => x.textContent === texto);
    if (b === undefined) throw new Error(`no hay botón "${texto}"`);
    return b;
  };
  const aviso = (): string => host.querySelector('[role="alert"]')?.textContent ?? '';

  describe('Acciones habilitadas según el estado (5.2)', () => {
    it('muestra los datos del turno y el historial de ausencias', async () => {
      await abrir(turnoFicticio(), INICIO - 120 * MIN);
      expect(host.textContent).toContain('24/11/2026 09:00–09:45');
      expect(host.textContent).toContain('Gómez, Lucía');
      // C-07 no expone el DNI en el turno (regla dura #10).
      expect(host.textContent).not.toContain('DNI');
      expect(host.textContent).toContain('Limpieza');
      expect(host.textContent).toContain('Reservado');
      expect(deps.api.ausenciasPaciente).toHaveBeenCalledWith(7);
      expect(host.textContent).toContain('Sin ausencias registradas');
    });

    it('reservado futuro: Mover, Cancelar y Marcar atendido', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO - 120 * MIN);
      expect(habilitados()).toEqual(['Mover', 'Cancelar', 'Marcar atendido']);
    });

    it('pendiente: solo Cancelar', async () => {
      await abrir(turnoEn('PENDIENTE_CONFIRMACION'), INICIO - 120 * MIN);
      expect(botones().map((b) => b.textContent)).toEqual(['Cancelar']);
    });

    it('atendido: ninguna acción', async () => {
      await abrir(turnoEn('ATENDIDO'), INICIO + 60 * MIN);
      expect(botones()).toHaveLength(0);
    });

    it('no se mueve un turno ya iniciado', async () => {
      await abrir(turnoEn('CONFIRMADO'), INICIO + 10 * MIN);
      expect(habilitados()).not.toContain('Mover');
    });

    it('antes de la tolerancia: Marcar ausente deshabilitado con "Disponible desde las 09:15"', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO + 14 * MIN);
      const ausente = boton('Marcar ausente');
      expect(ausente.disabled).toBe(true);
      expect(host.textContent).toContain('Disponible desde las 09:15');
    });

    it('justo en la tolerancia: Marcar ausente habilitado', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO + 15 * MIN);
      expect(boton('Marcar ausente').disabled).toBe(false);
    });

    it('si no se puede leer el consultorio, la tolerancia cae a 0', async () => {
      deps = crearAgendaDepsDoble(new Date(INICIO + 1 * MIN));
      deps.obtenerConsultorio.mockRejectedValue(errorHttp(500));
      createTurnoDetalle(deps)(host, turnoEn('RESERVADO'), alCambiar);
      await flush();
      expect(boton('Marcar ausente').disabled).toBe(false);
    });
  });

  describe('Acciones simples (5.3)', () => {
    it('Marcar atendido: envía la acción, muestra "Atendido" sin acciones y recarga la agenda', async () => {
      await abrir(turnoEn('CONFIRMADO'), INICIO + 30 * MIN);
      deps.api.marcarAtendido.mockResolvedValue(turnoEn('ATENDIDO'));
      boton('Marcar atendido').click();
      await flush();
      expect(deps.api.marcarAtendido).toHaveBeenCalledWith(41);
      expect(host.querySelector('[data-estado]')?.textContent).toBe('Atendido');
      expect(botones()).toHaveLength(0);
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: false });
    });

    it('Marcar ausente: confirma, envía, muestra "Ausente" y recarga el historial', async () => {
      await abrir(turnoEn('RESERVADO', { paciente: { ...turnoFicticio().paciente, cantidadAusencias: 1 } }), INICIO + 20 * MIN);
      deps.api.marcarAusente.mockResolvedValue(turnoEn('AUSENTE', { ausenteMarcadoEn: new Date(INICIO + 20 * MIN).toISOString() }));
      deps.api.ausenciasPaciente.mockResolvedValue({
        cantidad: 2,
        ausencias: [{ turnoId: 41, inicio: '2026-11-24T12:00:00Z', prestacionNombre: 'Limpieza' }],
      });
      boton('Marcar ausente').click();
      await flush();
      expect(deps.confirmar).toHaveBeenCalledWith('¿Marcar como ausente a Gómez, Lucía?');
      expect(deps.api.marcarAusente).toHaveBeenCalledWith(41);
      expect(host.querySelector('[data-estado]')?.textContent).toBe('Ausente');
      expect(deps.api.ausenciasPaciente).toHaveBeenCalledTimes(2);
      expect(host.querySelector('[data-cantidad]')?.textContent).toBe('Ausencias: 2');
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: false });
    });

    it('Marcar ausente sin confirmar no envía nada', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO + 20 * MIN);
      deps.confirmar.mockReturnValue(false);
      boton('Marcar ausente').click();
      await flush();
      expect(deps.api.marcarAusente).not.toHaveBeenCalled();
    });

    it('rechazo 422 al marcar ausente: muestra el detail y recarga la agenda', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO + 20 * MIN);
      deps.api.marcarAusente.mockRejectedValue(errorHttp(422, { detail: 'Todavía no pasó la tolerancia' }));
      boton('Marcar ausente').click();
      await flush();
      expect(aviso()).toBe('Todavía no pasó la tolerancia');
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: false });
    });

    it('Revertir ausencia: confirma, envía, muestra "Reservado" y recarga el historial', async () => {
      const marcado = new Date(INICIO + 20 * MIN).toISOString();
      await abrir(turnoEn('AUSENTE', { ausenteMarcadoEn: marcado }), INICIO + 60 * MIN);
      deps.api.revertirAusencia.mockResolvedValue(turnoEn('RESERVADO'));
      boton('Revertir ausencia').click();
      await flush();
      expect(deps.confirmar).toHaveBeenCalledWith('¿Revertir la ausencia de Gómez, Lucía?');
      expect(deps.api.revertirAusencia).toHaveBeenCalledWith(41);
      expect(host.querySelector('[data-estado]')?.textContent).toBe('Reservado');
      expect(deps.api.ausenciasPaciente).toHaveBeenCalledTimes(2);
    });

    it('ausente fuera de la ventana de 24 h: sin acciones', async () => {
      const marcado = new Date(INICIO + 20 * MIN).toISOString();
      await abrir(turnoEn('AUSENTE', { ausenteMarcadoEn: marcado }), INICIO + 20 * MIN + 24 * 60 * MIN);
      expect(botones()).toHaveLength(0);
    });
  });

  describe('Cancelar desde el detalle (5.4)', () => {
    it('Cancelar abre el diálogo; al confirmar se envía y se cierra el panel', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO - 120 * MIN);
      boton('Cancelar').click();
      const motivo = host.querySelector<HTMLTextAreaElement>('textarea[name="motivo"]');
      motivo!.value = 'Paciente avisó por teléfono';
      boton('Confirmar cancelación').click();
      await flush();
      expect(deps.api.cancelarTurno).toHaveBeenCalledWith(41, 'Paciente avisó por teléfono');
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: true });
    });
  });

  describe('Errores al operar un turno (5.5)', () => {
    it('transición inválida: muestra el detail y recarga la agenda', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO - 120 * MIN);
      deps.api.marcarAtendido.mockRejectedValue(errorHttp(422, { detail: 'Transición inválida' }));
      boton('Marcar atendido').click();
      await flush();
      expect(aviso()).toBe('Transición inválida');
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: false });
    });

    it('turno inexistente: cierra el detalle, recarga y deja el mensaje en la agenda', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO - 120 * MIN);
      deps.api.marcarAtendido.mockRejectedValue(errorHttp(404));
      boton('Marcar atendido').click();
      await flush();
      expect(alCambiar).toHaveBeenCalledWith({ cerrar: true, mensaje: 'El turno ya no existe.' });
    });

    it('Cerrar vacía el panel', async () => {
      await abrir(turnoEn('RESERVADO'), INICIO - 120 * MIN);
      boton('Cerrar').click();
      expect(host.childElementCount).toBe(0);
    });
  });
});
