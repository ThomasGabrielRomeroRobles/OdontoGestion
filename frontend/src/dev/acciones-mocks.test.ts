import { describe, expect, it } from 'vitest';
import { crearAccionesApiMock } from './acciones-mocks';

describe('mocks de desarrollo de las acciones por token', () => {
  it('los tokens de demostración resuelven su acción y el token "vencido" da 410', async () => {
    const api = crearAccionesApiMock();
    await expect(api.obtenerResumen('cancelar')).resolves.toMatchObject({ resumen: { accion: 'CANCELAR_TURNO', disponible: true } });
    await expect(api.obtenerResumen('confirmar-solicitud')).resolves.toMatchObject({ resumen: { accion: 'CONFIRMAR_SOLICITUD' } });
    await expect(api.obtenerResumen('vencido')).resolves.toEqual({ tipo: 'noDisponible' });
  });

  it('ejecutar consume el token: el segundo intento da noDisponible', async () => {
    const api = crearAccionesApiMock();
    const primero = await api.ejecutar('cancelar', 'CANCELAR_TURNO');
    expect(primero).toMatchObject({ tipo: 'ejecutada', resumen: { turno: { estado: 'CANCELADO' }, disponible: false } });
    await expect(api.ejecutar('cancelar', 'CANCELAR_TURNO')).resolves.toEqual({ tipo: 'noDisponible' });
    await expect(api.obtenerResumen('cancelar')).resolves.toEqual({ tipo: 'noDisponible' });
  });
});
