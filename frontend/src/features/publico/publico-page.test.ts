import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PublicoApi, ResultadoSolicitud, SolicitudTurno } from './publico-api';
import { createPublicoPage } from './publico-page';
import { alternativas, huecos, mensajeGenerico, prestaciones, solicitudValida } from './test-fixtures';

type SolicitarMock = ReturnType<typeof vi.fn<(s: SolicitudTurno) => Promise<ResultadoSolicitud>>>;
type DisponibilidadMock = ReturnType<typeof vi.fn<PublicoApi['consultarDisponibilidad']>>;

describe('página pública de solicitud de turno', () => {
  let container: HTMLElement;
  let listarPrestaciones: ReturnType<typeof vi.fn<PublicoApi['listarPrestaciones']>>;
  let consultarDisponibilidad: DisponibilidadMock;
  let solicitarTurno: SolicitarMock;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    listarPrestaciones = vi.fn<PublicoApi['listarPrestaciones']>().mockResolvedValue(prestaciones);
    consultarDisponibilidad = vi.fn<PublicoApi['consultarDisponibilidad']>().mockResolvedValue(huecos);
    solicitarTurno = vi.fn<(s: SolicitudTurno) => Promise<ResultadoSolicitud>>();
  });
  afterEach(() => {
    container.remove();
    vi.useRealTimers();
  });

  function api(): PublicoApi {
    return { listarPrestaciones, consultarDisponibilidad, solicitarTurno };
  }

  function el<T extends Element>(selector: string): T {
    const encontrado = container.querySelector<T>(selector);
    if (!encontrado) throw new Error(`no existe ${selector}`);
    return encontrado;
  }

  function escribir(nombre: string, valor: string): void {
    const input = el<HTMLInputElement>(`input[name="${nombre}"]`);
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  async function montar(): Promise<void> {
    createPublicoPage(api())(container);
    await vi.waitFor(() => expect(container.dataset['estadoCarga']).toBe('listo'));
  }

  async function elegirDia(prestacionId = '3', fecha = '2026-11-23'): Promise<void> {
    const select = el<HTMLSelectElement>('select[name="prestacion"]');
    select.value = prestacionId;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    const dia = el<HTMLInputElement>('input[name="fecha"]');
    dia.value = fecha;
    dia.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => expect(container.dataset['estadoHuecos']).toBe('listo'));
  }

  async function elegirHueco(indice = 0): Promise<void> {
    const botones = container.querySelectorAll<HTMLButtonElement>('button[data-hueco]');
    botones[indice]?.click();
    await vi.waitFor(() => expect(container.querySelector('form')?.hidden).toBe(false));
  }

  function completarDatos(): void {
    escribir('nombre', solicitudValida.nombre);
    escribir('apellido', solicitudValida.apellido);
    escribir('dni', solicitudValida.dni);
    escribir('email', solicitudValida.email);
    escribir('telefono', solicitudValida.telefono);
  }

  function enviar(): void {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  async function flujoHastaFormulario(): Promise<void> {
    await montar();
    await elegirDia();
    await elegirHueco();
    completarDatos();
  }

  describe('prestación, día y huecos (12.3)', () => {
    it('carga las prestaciones públicas como opciones', async () => {
      await montar();

      const opciones = [...container.querySelectorAll<HTMLOptionElement>('select[name="prestacion"] option')];
      expect(opciones.map((o) => o.textContent)).toEqual(expect.arrayContaining(['Consulta (30 min)', 'Limpieza (45 min)']));
      expect(consultarDisponibilidad).not.toHaveBeenCalled();
    });

    it('al elegir prestación y día consulta la disponibilidad y muestra los huecos en hora de Buenos Aires', async () => {
      await montar();
      await elegirDia('3', '2026-11-23');

      expect(consultarDisponibilidad).toHaveBeenCalledWith(3, '2026-11-23');
      const textos = [...container.querySelectorAll('button[data-hueco]')].map((b) => b.textContent);
      // 13:00Z y 13:15Z son las 10:00 y 10:15 en Buenos Aires (UTC-3).
      expect(textos).toEqual(['23/11/2026 10:00', '23/11/2026 10:15']);
    });

    it('otra prestación y otro día consultan otra disponibilidad', async () => {
      await montar();
      await elegirDia('4', '2026-12-01');

      expect(consultarDisponibilidad).toHaveBeenLastCalledWith(4, '2026-12-01');
    });

    it('sin huecos muestra "sin horarios disponibles" y no muestra el formulario', async () => {
      consultarDisponibilidad.mockResolvedValue([]);
      await montar();
      await elegirDia();

      expect(el<HTMLElement>('[data-sin-huecos]').hidden).toBe(false);
      expect(el<HTMLElement>('[data-sin-huecos]').textContent).toMatch(/sin horarios disponibles/i);
      expect(el<HTMLFormElement>('form').hidden).toBe(true);
    });

    it('el formulario aparece recién al elegir un hueco', async () => {
      await montar();
      await elegirDia();
      expect(el<HTMLFormElement>('form').hidden).toBe(true);

      await elegirHueco(1);

      expect(el<HTMLFormElement>('form').hidden).toBe(false);
      expect(el('[data-hueco-elegido]').textContent).toContain('10:15');
    });

    it('si falla la carga de prestaciones muestra un mensaje genérico', async () => {
      listarPrestaciones.mockRejectedValue({ kind: 'network' });
      await montar();

      expect(el('[role="alert"]').textContent).not.toBe('');
      expect(container.querySelector('select[name="prestacion"] option[value="3"]')).toBeNull();
    });
  });

  describe('formulario con señuelo (12.3)', () => {
    it('el señuelo sitioWeb existe, no es visible, sale del orden de tabulación y no se autocompleta', async () => {
      await montar();

      const senuelo = el<HTMLInputElement>('input[name="sitioWeb"]');
      expect(senuelo.getAttribute('tabindex')).toBe('-1');
      expect(senuelo.getAttribute('aria-hidden')).toBe('true');
      expect(senuelo.getAttribute('autocomplete')).toBe('off');
      expect(senuelo.value).toBe('');
      const envoltorio = senuelo.parentElement as HTMLElement;
      expect(envoltorio.getAttribute('aria-hidden')).toBe('true');
      expect(envoltorio.style.position).toBe('absolute');
      expect(envoltorio.style.left).toBe('-10000px');
    });

    it('un DNI inválido marca el campo y no envía la solicitud', async () => {
      await flujoHastaFormulario();
      escribir('dni', '12AB');
      enviar();

      expect(solicitarTurno).not.toHaveBeenCalled();
      expect(el('[data-error="dni"]').textContent).toMatch(/7 u 8 dígitos/);
      expect(el('input[name="dni"]').getAttribute('aria-invalid')).toBe('true');
    });

    it('otros campos obligatorios vacíos también se marcan', async () => {
      await montar();
      await elegirDia();
      await elegirHueco();
      escribir('email', 'sin-arroba');
      enviar();

      expect(solicitarTurno).not.toHaveBeenCalled();
      expect(el('[data-error="nombre"]').textContent).not.toBe('');
      expect(el('[data-error="email"]').textContent).not.toBe('');
    });

    it('envía los datos del contrato con el señuelo vacío y el hueco elegido', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'aceptada', mensaje: 'x' });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(solicitarTurno).toHaveBeenCalledTimes(1));
      expect(solicitarTurno).toHaveBeenCalledWith(solicitudValida);
    });

    it('si alguien completó el señuelo se envía tal como está', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'aceptada', mensaje: 'x' });
      await flujoHastaFormulario();
      escribir('sitioWeb', 'http://spam.example');
      enviar();

      await vi.waitFor(() => expect(solicitarTurno).toHaveBeenCalled());
      expect(solicitarTurno.mock.calls[0]?.[0].sitioWeb).toBe('http://spam.example');
    });
  });

  describe('resultados (12.4)', () => {
    it('202 muestra el mensaje genérico y limpia el formulario', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'aceptada', mensaje: 'texto del servidor' });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(el('[role="status"]').textContent).toBe(mensajeGenerico));
      expect(el<HTMLInputElement>('input[name="dni"]').value).toBe('');
      expect(el<HTMLInputElement>('input[name="nombre"]').value).toBe('');
      expect(el<HTMLFormElement>('form').hidden).toBe(true);
      expect(container.textContent).not.toContain('Ficticia');
    });

    it('409 informa el conflicto, ofrece las alternativas y conserva los datos', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'conflicto', alternativas });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(container.querySelectorAll('button[data-alternativa]')).toHaveLength(2));
      expect(el('[role="alert"]').textContent).toMatch(/ya no está disponible/);
      const textos = [...container.querySelectorAll('button[data-alternativa]')].map((b) => b.textContent);
      expect(textos).toEqual(['23/11/2026 10:30', '23/11/2026 10:45']);
      expect(el<HTMLInputElement>('input[name="dni"]').value).toBe(solicitudValida.dni);
      expect(el<HTMLInputElement>('input[name="email"]').value).toBe(solicitudValida.email);
    });

    it('elegir una alternativa cambia el horario y permite reenviar con los mismos datos', async () => {
      solicitarTurno.mockResolvedValueOnce({ tipo: 'conflicto', alternativas });
      solicitarTurno.mockResolvedValueOnce({ tipo: 'aceptada', mensaje: 'x' });
      await flujoHastaFormulario();
      enviar();
      await vi.waitFor(() => expect(container.querySelectorAll('button[data-alternativa]')).toHaveLength(2));

      container.querySelectorAll<HTMLButtonElement>('button[data-alternativa]')[1]?.click();
      expect(el('[data-hueco-elegido]').textContent).toContain('10:45');
      enviar();

      await vi.waitFor(() => expect(solicitarTurno).toHaveBeenCalledTimes(2));
      expect(solicitarTurno.mock.calls[1]?.[0]).toEqual({ ...solicitudValida, inicio: alternativas[1]?.inicio });
    });

    it('409 sin alternativas informa el conflicto y recarga los huecos', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'conflicto', alternativas: [] });
      await flujoHastaFormulario();
      consultarDisponibilidad.mockClear();
      enviar();

      await vi.waitFor(() => expect(consultarDisponibilidad).toHaveBeenCalledTimes(1));
      expect(el('[role="alert"]').textContent).toMatch(/ya no está disponible/);
    });

    it('422 en inicio informa que el horario no es válido y recarga los huecos', async () => {
      solicitarTurno.mockResolvedValue({
        tipo: 'horarioInvalido',
        fieldErrors: [{ field: 'inicio', message: 'Debe quedar dentro de un horario de trabajo.' }],
      });
      await flujoHastaFormulario();
      consultarDisponibilidad.mockClear();
      enviar();

      await vi.waitFor(() => expect(consultarDisponibilidad).toHaveBeenCalledWith(3, '2026-11-23'));
      expect(el('[role="alert"]').textContent).toMatch(/horario.*no es válido/i);
      expect(el<HTMLInputElement>('input[name="dni"]').value).toBe(solicitudValida.dni);
    });

    it('429 con Retry-After 1800 muestra 30 minutos y deshabilita el envío', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'limite', retryAfterSeconds: 1800 });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(el('[role="alert"]').textContent).toMatch(/30 minutos/));
      expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(true);
    });

    it('429 redondea hacia arriba los segundos a minutos y sin Retry-After dice "unos minutos"', async () => {
      solicitarTurno.mockResolvedValueOnce({ tipo: 'limite', retryAfterSeconds: 61 });
      await flujoHastaFormulario();
      enviar();
      await vi.waitFor(() => expect(el('[role="alert"]').textContent).toMatch(/2 minutos/));
    });

    it('429 sin Retry-After usa un texto sin cifra y mantiene el envío deshabilitado', async () => {
      solicitarTurno.mockResolvedValue({ tipo: 'limite' });
      await flujoHastaFormulario();
      enviar();
      await vi.waitFor(() => expect(el('[role="alert"]').textContent).toMatch(/unos minutos/));
      expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(true);
    });

    it('400 con errores por campo los muestra junto a cada campo', async () => {
      solicitarTurno.mockResolvedValue({
        tipo: 'invalida',
        fieldErrors: [{ field: 'dni', message: 'Debe tener 7 u 8 dígitos.' }],
      });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(el('[data-error="dni"]').textContent).toBe('Debe tener 7 u 8 dígitos.'));
    });

    it('un error de red muestra un mensaje genérico y permite reintentar', async () => {
      solicitarTurno.mockRejectedValueOnce({ kind: 'network' });
      solicitarTurno.mockResolvedValueOnce({ tipo: 'aceptada', mensaje: 'x' });
      await flujoHastaFormulario();
      enviar();

      await vi.waitFor(() => expect(el('[role="alert"]').textContent).toMatch(/no pudimos|inténtalo|intentá/i));
      expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(false);
      enviar();
      await vi.waitFor(() => expect(el('[role="status"]').textContent).toBe(mensajeGenerico));
    });

    it('un doble envío seguido manda una sola solicitud', async () => {
      solicitarTurno.mockReturnValue(new Promise(() => undefined));
      await flujoHastaFormulario();
      enviar();
      enviar();

      expect(solicitarTurno).toHaveBeenCalledTimes(1);
      expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(true);
    });
  });
});
