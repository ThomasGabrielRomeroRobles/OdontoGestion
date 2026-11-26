import { describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import { clickEnBoton, esperar, requerir } from './dom-test-helpers';
import type { HistoriaApi } from './historia-api';
import { crearOdontograma } from './odontograma';
import { AUTOR, historia, pieza } from './test-fixtures';

function piezasCon16(estado: 'CARIES') {
  return historia().piezas.map((p) => (p.fdi === 16 ? pieza(16, { estado }) : p));
}

describe('odontograma', () => {
  it('dibuja 32 piezas en orden FDI y muestra el estado por texto y clase', () => {
    const guardarPieza = vi.fn<HistoriaApi['guardarPieza']>();
    const vista = crearOdontograma({ api: { guardarPieza }, pacienteId: 7, piezas: piezasCon16('CARIES') });

    const botones = [...vista.querySelectorAll<HTMLButtonElement>('button[data-fdi]')];
    expect(botones).toHaveLength(32);
    const superior = [...requerir(vista, '[data-fila="superior"]').querySelectorAll('button')].map((b) => b.dataset['fdi']);
    expect(superior[0]).toBe('18');
    expect(superior.at(-1)).toBe('28');
    const p16 = requerir<HTMLButtonElement>(vista, 'button[data-fdi="16"]');
    expect(p16.textContent).toContain('Caries');
    expect(p16.classList.contains('pieza--caries')).toBe(true);
    const conEstado = botones.filter((b) => !b.classList.contains('pieza--sin-registrar'));
    expect(conEstado.map((b) => b.dataset['fdi'])).toEqual(['16']);
  });

  it('guarda el estado de una pieza y actualiza solo esa pieza', async () => {
    const guardarPieza = vi.fn<HistoriaApi['guardarPieza']>().mockResolvedValue(
      pieza(16, { estado: 'OBTURADA', observacion: 'Resina', actualizadoPor: AUTOR, actualizadoEn: '2026-11-26T13:00:00Z' }),
    );
    const vista = crearOdontograma({ api: { guardarPieza }, pacienteId: 7, piezas: historia().piezas });

    requerir<HTMLButtonElement>(vista, 'button[data-fdi="16"]').click();
    requerir<HTMLSelectElement>(vista, 'select[name="estado"]').value = 'OBTURADA';
    requerir<HTMLTextAreaElement>(vista, 'textarea[name="observacion"]').value = ' Resina ';
    clickEnBoton(vista, 'Guardar');
    await esperar();

    expect(guardarPieza).toHaveBeenCalledWith(7, 16, { estado: 'OBTURADA', observacion: 'Resina' });
    expect(requerir(vista, 'button[data-fdi="16"]').textContent).toContain('Obturada');
    expect(requerir(vista, 'button[data-fdi="17"]').textContent).toContain('Sin registrar');
    expect(requerir(vista, '[data-meta]').textContent).toBe('Actualizada por Laura Gómez el 26/11/2026 10:00');
  });

  it('ante un error muestra el título del problema y conserva el estado anterior', async () => {
    const error: ApiError = { kind: 'http', status: 422, title: 'Regla de negocio incumplida', fieldErrors: [], extensions: {} };
    const guardarPieza = vi.fn<HistoriaApi['guardarPieza']>().mockRejectedValue(error);
    const vista = crearOdontograma({ api: { guardarPieza }, pacienteId: 7, piezas: piezasCon16('CARIES') });

    requerir<HTMLButtonElement>(vista, 'button[data-fdi="16"]').click();
    requerir<HTMLSelectElement>(vista, 'select[name="estado"]').value = 'CORONA';
    clickEnBoton(vista, 'Guardar');
    await esperar();

    expect(requerir(vista, '[data-editor-pieza] [role="alert"]').textContent).toContain('Regla de negocio incumplida');
    expect(requerir(vista, 'button[data-fdi="16"]').textContent).toContain('Caries');
  });
});
