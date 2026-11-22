import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Turno } from '../agenda-api';
import { crearAgendaDepsDoble, type AgendaDepsDoble } from '../deps-test-helpers';
import type { AgendaDeps } from '../deps';
import { errorHttp, flush, pacienteFicticio, turnoFicticio } from '../test-fixtures';
import { createNuevoTurno } from './nuevo-turno';

describe('nuevo turno (grupo 6, US-021)', () => {
  let host: HTMLElement;
  let deps: AgendaDepsDoble & AgendaDeps;
  let alCrear: ReturnType<typeof vi.fn<(t: Turno) => void>>;
  let cerrar: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(async () => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    host = document.createElement('section');
    document.body.replaceChildren(host);
    deps = crearAgendaDepsDoble(new Date('2026-11-23T13:00:00Z'));
    deps.api.disponibilidad.mockResolvedValue([
      { inicio: '2026-11-24T12:00:00Z', fin: '2026-11-24T12:45:00Z' },
      { inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' },
    ]);
    alCrear = vi.fn<(t: Turno) => void>();
    cerrar = vi.fn<() => void>();
    createNuevoTurno(deps)(host, alCrear, cerrar);
    await flush();
  });

  const campo = (name: string): HTMLInputElement => host.querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  const boton = (texto: string): HTMLButtonElement => {
    const b = [...host.querySelectorAll<HTMLButtonElement>('button')].find((x) => x.textContent === texto);
    if (b === undefined) throw new Error(`no hay botón "${texto}"`);
    return b;
  };
  const errorDe = (name: string): string => host.querySelector(`[data-error-for="${name}"]`)?.textContent ?? '';
  const aviso = (): string => host.querySelector('[data-aviso-turno]')?.textContent ?? '';

  async function buscar(dni: string): Promise<void> {
    campo('dni').value = dni;
    boton('Buscar').click();
    await flush();
  }

  async function elegirHueco(indice = 0): Promise<void> {
    const select = host.querySelector<HTMLSelectElement>('select[name="prestacionId"]')!;
    select.value = '3';
    select.dispatchEvent(new Event('change'));
    campo('fecha').value = '2026-11-24';
    campo('fecha').dispatchEvent(new Event('change'));
    await flush();
    host.querySelectorAll<HTMLButtonElement>('[data-huecos] button')[indice]!.click();
  }

  describe('Paciente del turno por DNI o alta rápida (6.1)', () => {
    it('paciente existente: lo selecciona', async () => {
      deps.pacientes.buscarPorDni.mockResolvedValue([pacienteFicticio]);
      await buscar('30.111.222');
      expect(deps.pacientes.buscarPorDni).toHaveBeenCalledWith('30.111.222');
      expect(host.querySelector('[data-paciente-seleccionado]')?.textContent).toBe('Gómez, Lucía (DNI 30111222)');
    });

    it('paciente inexistente: muestra el alta rápida con el DNI precargado', async () => {
      await buscar('40999888');
      expect(host.querySelector('form[data-form="alta-paciente"]')).not.toBeNull();
      expect(campo('altaDni').value).toBe('40999888');
    });

    it('alta rápida exitosa: el paciente creado queda seleccionado', async () => {
      await buscar('40999888');
      const creado = { ...pacienteFicticio, id: 8, nombre: 'Tomás', apellido: 'Ríos', dni: '40999888' };
      deps.pacientes.crearPaciente.mockResolvedValue(creado);
      campo('nombre').value = 'Tomás';
      campo('apellido').value = 'Ríos';
      campo('telefono').value = '+54 11 5555-0202';
      campo('email').value = 'tomas.rios@ejemplo.test';
      boton('Crear paciente').click();
      await flush();
      expect(deps.pacientes.crearPaciente).toHaveBeenCalledWith({
        nombre: 'Tomás',
        apellido: 'Ríos',
        dni: '40999888',
        telefono: '+54 11 5555-0202',
        email: 'tomas.rios@ejemplo.test',
      });
      expect(host.querySelector('[data-paciente-seleccionado]')?.textContent).toBe('Ríos, Tomás (DNI 40999888)');
    });

    it('DNI inválido: error junto al campo y no se envía', async () => {
      await buscar('12ab');
      expect(errorDe('dni')).toBe('Debe tener 7 u 8 dígitos.');
      expect(deps.pacientes.buscarPorDni).not.toHaveBeenCalled();
    });

    it('alta rápida con campos vacíos o email inválido: errores de cliente sin enviar', async () => {
      await buscar('40999888');
      campo('email').value = 'no-es-email';
      boton('Crear paciente').click();
      await flush();
      expect(errorDe('nombre')).toBe('Es obligatorio.');
      expect(errorDe('email')).toBe('No es un email válido.');
      expect(deps.pacientes.crearPaciente).not.toHaveBeenCalled();
    });

    it('DNI duplicado (409): vuelve a buscar y selecciona al existente', async () => {
      await buscar('30111222');
      deps.pacientes.crearPaciente.mockRejectedValue(errorHttp(409));
      deps.pacientes.buscarPorDni.mockResolvedValue([pacienteFicticio]);
      campo('nombre').value = 'Lucía';
      campo('apellido').value = 'Gómez';
      campo('telefono').value = '+54 11 5555-0101';
      campo('email').value = 'lucia.gomez@ejemplo.test';
      boton('Crear paciente').click();
      await flush();
      expect(deps.pacientes.buscarPorDni).toHaveBeenLastCalledWith('30111222');
      expect(host.querySelector('[data-paciente-seleccionado]')?.textContent).toBe('Gómez, Lucía (DNI 30111222)');
    });

    it('error de campo del servidor (400): junto al campo y se conservan los valores', async () => {
      await buscar('40999888');
      deps.pacientes.crearPaciente.mockRejectedValue(
        errorHttp(400, { fieldErrors: [{ field: 'email', message: 'No es un email válido.' }] }),
      );
      campo('nombre').value = 'Tomás';
      campo('apellido').value = 'Ríos';
      campo('telefono').value = '+54 11 5555-0202';
      campo('email').value = 'tomas@ejemplo';
      boton('Crear paciente').click();
      await flush();
      expect(errorDe('email')).toBe('No es un email válido.');
      expect(campo('nombre').value).toBe('Tomás');
      expect(campo('email').value).toBe('tomas@ejemplo');
    });
  });

  describe('Creación del turno (6.3)', () => {
    beforeEach(async () => {
      deps.pacientes.buscarPorDni.mockResolvedValue([pacienteFicticio]);
      await buscar('30111222');
    });

    it('creación exitosa: envía el POST con inicio UTC y avisa el turno creado', async () => {
      await elegirHueco(0);
      const creado = turnoFicticio();
      deps.api.crearTurno.mockResolvedValue(creado);
      boton('Confirmar turno').click();
      await flush();
      expect(deps.api.crearTurno).toHaveBeenCalledWith({ pacienteId: 7, prestacionId: 3, inicio: '2026-11-24T12:00:00Z' });
      expect(alCrear).toHaveBeenCalledWith(creado);
    });

    it('sin hueco elegido no envía y lo indica', async () => {
      boton('Confirmar turno').click();
      await flush();
      expect(aviso()).toBe('Elegí paciente, prestación y horario.');
      expect(deps.api.crearTurno).not.toHaveBeenCalled();
    });

    it('fuera de horario (422): muestra el mensaje y conserva paciente, prestación y fecha', async () => {
      await elegirHueco(0);
      deps.api.crearTurno.mockRejectedValue(errorHttp(422, { detail: 'Fuera del horario de atención' }));
      boton('Confirmar turno').click();
      await flush();
      expect(aviso()).toBe('Fuera del horario de atención');
      expect(host.querySelector('[data-paciente-seleccionado]')?.textContent).toContain('Gómez, Lucía');
      expect(host.querySelector<HTMLSelectElement>('select[name="prestacionId"]')?.value).toBe('3');
      expect(campo('fecha').value).toBe('2026-11-24');
      expect(alCrear).not.toHaveBeenCalled();
    });

    it('Cerrar avisa al contenedor', () => {
      boton('Cerrar').click();
      expect(cerrar).toHaveBeenCalled();
    });
  });

  describe('Conflicto de horario con alternativas (6.4)', () => {
    beforeEach(async () => {
      deps.pacientes.buscarPorDni.mockResolvedValue([pacienteFicticio]);
      await buscar('30111222');
      await elegirHueco(0);
    });

    const alternativas = (): string[] =>
      [...host.querySelectorAll<HTMLButtonElement>('[data-alternativas] button')].map((b) => b.textContent ?? '');

    it('409 con alternativas: mensaje, opciones y disponibilidad refrescada', async () => {
      deps.api.crearTurno.mockRejectedValue(
        errorHttp(409, {
          extensions: {
            alternativas: [
              { inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' },
              { inicio: '2026-11-24T14:00:00Z', fin: '2026-11-24T14:45:00Z' },
            ],
          },
        }),
      );
      const pedidasAntes = deps.api.disponibilidad.mock.calls.length;
      boton('Confirmar turno').click();
      await flush();
      expect(aviso()).toBe('Ese horario ya no está disponible.');
      expect(alternativas()).toEqual(['10:00', '11:00']);
      expect(deps.api.disponibilidad.mock.calls.length).toBe(pedidasAntes + 1);
    });

    it('elegir una alternativa y confirmar envía ese inicio', async () => {
      deps.api.crearTurno
        .mockRejectedValueOnce(
          errorHttp(409, { extensions: { alternativas: [{ inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' }] } }),
        )
        .mockResolvedValueOnce(turnoFicticio({ inicio: '2026-11-24T13:00:00Z' }));
      boton('Confirmar turno').click();
      await flush();
      host.querySelector<HTMLButtonElement>('[data-alternativas] button')!.click();
      boton('Confirmar turno').click();
      await flush();
      expect(deps.api.crearTurno).toHaveBeenLastCalledWith({ pacienteId: 7, prestacionId: 3, inicio: '2026-11-24T13:00:00Z' });
      expect(alCrear).toHaveBeenCalled();
    });

    it('409 sin alternativas: mensaje y huecos actualizados, sin opciones', async () => {
      deps.api.crearTurno.mockRejectedValue(errorHttp(409));
      deps.api.disponibilidad.mockResolvedValue([{ inicio: '2026-11-24T15:00:00Z', fin: '2026-11-24T15:45:00Z' }]);
      boton('Confirmar turno').click();
      await flush();
      expect(aviso()).toBe('Ese horario ya no está disponible.');
      expect(alternativas()).toEqual([]);
      expect([...host.querySelectorAll('[data-huecos] button')].map((b) => b.textContent)).toEqual(['12:00']);
    });
  });
});
