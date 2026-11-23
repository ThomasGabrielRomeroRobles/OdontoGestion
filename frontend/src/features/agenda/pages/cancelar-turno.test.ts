import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgendaApi, Turno } from '../agenda-api';
import type { ErrorTraducido } from '../errores';
import { errorRed, flush, turnoEn } from '../test-fixtures';
import { crearCancelarTurno } from './cancelar-turno';

describe('diálogo de cancelación (5.4, US-023)', () => {
  let host: HTMLElement;
  let cancelar: ReturnType<typeof vi.fn<AgendaApi['cancelarTurno']>>;
  let alCancelar: ReturnType<typeof vi.fn<(t: Turno) => void>>;
  let alVolver: ReturnType<typeof vi.fn<() => void>>;
  let alError: ReturnType<typeof vi.fn<(e: ErrorTraducido) => void>>;

  beforeEach(() => {
    host = document.createElement('div');
    cancelar = vi.fn<AgendaApi['cancelarTurno']>().mockResolvedValue(turnoEn('CANCELADO'));
    alCancelar = vi.fn<(t: Turno) => void>();
    alVolver = vi.fn<() => void>();
    alError = vi.fn<(e: ErrorTraducido) => void>();
    host.append(crearCancelarTurno({ turno: turnoEn('RESERVADO'), cancelar, alCancelar, alVolver, alError }));
  });

  const motivo = (): HTMLTextAreaElement => host.querySelector<HTMLTextAreaElement>('textarea[name="motivo"]')!;
  const boton = (texto: string): HTMLButtonElement =>
    [...host.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent === texto)!;

  it('con motivo: envía el motivo recortado y avisa el resultado', async () => {
    motivo().value = '  Paciente avisó por teléfono ';
    boton('Confirmar cancelación').click();
    await flush();
    expect(cancelar).toHaveBeenCalledWith(41, 'Paciente avisó por teléfono');
    expect(alCancelar).toHaveBeenCalledWith(turnoEn('CANCELADO'));
  });

  it('sin motivo: envía null', async () => {
    boton('Confirmar cancelación').click();
    await flush();
    expect(cancelar).toHaveBeenCalledWith(41, null);
  });

  it('Volver no envía nada', () => {
    boton('Volver').click();
    expect(alVolver).toHaveBeenCalled();
    expect(cancelar).not.toHaveBeenCalled();
  });

  it('motivo de 201 caracteres: "Máximo 200 caracteres." sin enviar', async () => {
    motivo().value = 'x'.repeat(201);
    boton('Confirmar cancelación').click();
    await flush();
    expect(host.textContent).toContain('Máximo 200 caracteres.');
    expect(cancelar).not.toHaveBeenCalled();
  });

  it('falla de red: mensaje de conexión y el motivo se conserva', async () => {
    cancelar.mockRejectedValue(errorRed);
    motivo().value = 'Viaje';
    boton('Confirmar cancelación').click();
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('No se pudo conectar. Reintentá.');
    expect(motivo().value).toBe('Viaje');
    expect(alCancelar).not.toHaveBeenCalled();
    expect(alError).toHaveBeenCalledWith(expect.objectContaining({ recargar: false, cerrar: false }));
  });
});
