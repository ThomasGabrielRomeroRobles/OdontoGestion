import { describe, expect, it } from 'vitest';
import { MENSAJE_ACEPTADA } from '../features/publico/publico-page';
import { solicitudValida } from '../features/publico/test-fixtures';
import { crearPublicoApiMock } from './publico-mocks';

describe('mocks de desarrollo de la página pública', () => {
  it('lista prestaciones ficticias', async () => {
    const api = crearPublicoApiMock();
    const prestaciones = await api.listarPrestaciones();
    expect(prestaciones.length).toBeGreaterThan(0);
    expect(prestaciones[0]).toMatchObject({ id: expect.any(Number), nombre: expect.any(String) });
  });

  it('la disponibilidad devuelve huecos del día pedido en UTC, sin repetirse entre días', async () => {
    const api = crearPublicoApiMock();
    const lunes = await api.consultarDisponibilidad(1, '2026-11-23');
    const martes = await api.consultarDisponibilidad(1, '2026-11-24');
    expect(lunes[0]?.inicio).toBe('2026-11-23T12:00:00Z');
    expect(martes[0]?.inicio).toBe('2026-11-24T12:00:00Z');
    expect(lunes.every((h) => h.inicio.startsWith('2026-11-23'))).toBe(true);
  });

  it('una solicitud común se acepta y un DNI de demostración fuerza el conflicto con alternativas', async () => {
    const api = crearPublicoApiMock();
    await expect(api.solicitarTurno(solicitudValida)).resolves.toEqual({ tipo: 'aceptada', mensaje: MENSAJE_ACEPTADA });
    const conflicto = await api.solicitarTurno({ ...solicitudValida, dni: '99.999.999' });
    expect(conflicto.tipo).toBe('conflicto');
  });
});
