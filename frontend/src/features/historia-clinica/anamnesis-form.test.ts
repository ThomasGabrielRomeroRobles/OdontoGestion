import { describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { crearAnamnesisForm } from './anamnesis-form';
import { esperar, requerir } from './dom-test-helpers';
import type { HistoriaApi } from './historia-api';
import { AUTOR, historia } from './test-fixtures';

function enviar(vista: HTMLElement): void {
  requerir<HTMLFormElement>(vista, 'form').dispatchEvent(new Event('submit', { cancelable: true }));
}

describe('anamnesis', () => {
  it('guarda y muestra autor y fecha en hora del consultorio', async () => {
    const vacia = historia().anamnesis;
    const guardarAnamnesis = vi.fn<HistoriaApi['guardarAnamnesis']>().mockResolvedValue({
      ...vacia,
      alergias: 'Penicilina',
      actualizadoPor: AUTOR,
      actualizadoEn: '2026-11-26T13:00:00Z',
    });
    const vista = crearAnamnesisForm({ api: { guardarAnamnesis }, pacienteId: 7, anamnesis: vacia });

    requerir<HTMLTextAreaElement>(vista, 'textarea[name="alergias"]').value = ' Penicilina ';
    requerir<HTMLSelectElement>(vista, 'select[name="embarazo"]').value = 'false';
    enviar(vista);
    await esperar();

    expect(guardarAnamnesis).toHaveBeenCalledWith(7, expect.objectContaining({ alergias: 'Penicilina', embarazo: false }));
    expect(requerir(vista, '[data-meta]').textContent).toBe('Actualizada por Laura Gómez el 26/11/2026 10:00');
  });

  it('muestra el error 400 junto al campo y conserva lo cargado', async () => {
    const error: ApiError = {
      kind: 'http',
      status: 400,
      title: 'Solicitud inválida',
      fieldErrors: [{ field: 'observaciones', message: 'el tamaño debe estar entre 0 y 2000' }],
      extensions: {},
    };
    const guardarAnamnesis = vi.fn<HistoriaApi['guardarAnamnesis']>().mockRejectedValue(error);
    const vista = crearAnamnesisForm({ api: { guardarAnamnesis }, pacienteId: 7, anamnesis: historia().anamnesis });

    requerir<HTMLTextAreaElement>(vista, 'textarea[name="observaciones"]').value = 'Texto ficticio';
    enviar(vista);
    await esperar();

    expect(requerir(vista, '[data-error-for="observaciones"]').textContent).toBe('el tamaño debe estar entre 0 y 2000');
    expect(requerir<HTMLTextAreaElement>(vista, 'textarea[name="observaciones"]').value).toBe('Texto ficticio');
  });
});
