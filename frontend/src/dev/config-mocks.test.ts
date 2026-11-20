import { describe, expect, it } from 'vitest';
import { crearMocksDesarrollo } from './config-mocks';

describe('mocks de desarrollo (9.4)', () => {
  it('auth: sin sesión hasta hacer login, y logout la cierra', async () => {
    const { auth } = crearMocksDesarrollo();
    await expect(auth.me()).resolves.toBeNull();
    await expect(auth.login('odontologo@consultorio.example', 'demo')).resolves.toEqual({
      email: 'odontologo@consultorio.example',
    });
    await expect(auth.me()).resolves.toEqual({ email: 'odontologo@consultorio.example' });
    await auth.logout();
    await expect(auth.me()).resolves.toBeNull();
  });

  it('prestaciones en memoria: seed ficticio, alta, desactivación y 422 por granularidad', async () => {
    const { agenda } = crearMocksDesarrollo();
    expect((await agenda.listarPrestaciones()).map((p) => p.nombre)).toContain('Consulta');

    const nueva = await agenda.crearPrestacion({ nombre: 'Blanqueamiento', duracionMin: 60 });
    expect(nueva).toMatchObject({ nombre: 'Blanqueamiento', activa: true });
    await expect(agenda.desactivarPrestacion(nueva.id)).resolves.toMatchObject({ activa: false });

    await expect(agenda.crearPrestacion({ nombre: 'Rara', duracionMin: 40 })).rejects.toMatchObject({
      status: 422,
      fieldErrors: [{ field: 'duracionMin' }],
    });
    await expect(agenda.crearPrestacion({ nombre: 'consulta', duracionMin: 30 })).rejects.toMatchObject({
      status: 422,
      fieldErrors: [{ field: 'nombre' }],
    });
  });

  it('horarios en memoria: rechaza solapamiento con 422 y elimina', async () => {
    const { agenda } = crearMocksDesarrollo();
    await expect(agenda.crearHorario({ diaSemana: 1, horaDesde: '12:00', horaHasta: '15:00' })).rejects.toMatchObject({
      status: 422,
    });
    const sabado = await agenda.crearHorario({ diaSemana: 6, horaDesde: '09:00', horaHasta: '12:00' });
    await agenda.eliminarHorario(sabado.id);
    expect((await agenda.listarHorarios()).some((h) => h.id === sabado.id)).toBe(false);
  });

  it('consultorio en memoria: actualizar cambia la granularidad que usan las prestaciones', async () => {
    const { config, agenda } = crearMocksDesarrollo();
    const actual = await config.obtenerConsultorio();
    const { zonaHoraria: _z, ...editable } = actual;
    await config.actualizarConsultorio({ ...editable, granularidadMin: 30 });
    await expect(agenda.crearPrestacion({ nombre: 'Media hora y cuarto', duracionMin: 45 })).rejects.toMatchObject({
      status: 422,
    });
  });

  it('bloqueos y contraseña en memoria', async () => {
    const { agenda, config } = crearMocksDesarrollo();
    const b = await agenda.crearBloqueo({ desde: '2027-01-10T12:00:00Z', hasta: '2027-01-10T15:00:00Z', motivo: null });
    expect(await agenda.listarBloqueos()).toContainEqual(b);
    await expect(config.cambiarPassword({ passwordActual: 'mal', passwordNueva: 'x' })).rejects.toMatchObject({
      status: 422,
      fieldErrors: [{ field: 'passwordActual' }],
    });
    await expect(config.cambiarPassword({ passwordActual: 'demo', passwordNueva: 'nueva' })).resolves.toBeUndefined();
  });
});
