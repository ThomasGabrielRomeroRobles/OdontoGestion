/** Datos FICTICIOS para tests de la feature de configuración (regla dura #12). Solo los importan tests. */
import type { Bloqueo, Horario, Prestacion } from './agenda-config-api';
import type { Consultorio, Perfil } from './config-api';

export const consultorioFicticio: Consultorio = {
  nombre: 'Consultorio Demo',
  direccion: 'Calle Falsa 123',
  telefono: '011 5555-0000',
  emailContacto: 'contacto@consultorio.example',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  granularidadMin: 15,
  anticipacionMinReservaMin: 120,
  limiteCancelacionHoras: 24,
  recordatorioHorasAntes: 24,
  vencimientoAvisoMin: 60,
  vencimientoPendienteMin: 30,
};

export const perfilFicticio: Perfil = {
  email: 'odontologo@consultorio.example',
  nombre: 'Ana',
  apellido: 'Pérez',
  matricula: 'MN 0000',
};

export const prestacionesFicticias: readonly Prestacion[] = [
  { id: 1, nombre: 'Consulta', duracionMin: 30, activa: true },
  { id: 2, nombre: 'Control', duracionMin: 15, activa: false },
  { id: 3, nombre: 'Endodoncia', duracionMin: 90, activa: true },
];

export const horariosFicticios: readonly Horario[] = [
  { id: 10, diaSemana: 1, horaDesde: '09:00', horaHasta: '13:00' },
  { id: 11, diaSemana: 1, horaDesde: '14:00', horaHasta: '18:00' },
  { id: 12, diaSemana: 2, horaDesde: '09:00', horaHasta: '13:00' },
];

export const bloqueosFicticios: readonly Bloqueo[] = [
  { id: 20, desde: '2026-12-24T03:00:00Z', hasta: '2026-12-26T03:00:00Z', motivo: 'Fiestas' },
];

/** Error 422 con un campo, como lo entrega `apiRequest`. */
export function error422(field: string, message: string): unknown {
  return { kind: 'http', status: 422, title: 'Datos inválidos', fieldErrors: [{ field, message }] };
}

/** Promesa controlable para verificar estados intermedios (botón deshabilitado). */
export function diferida<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void } {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

export function input(container: ParentNode, name: string): HTMLInputElement {
  const el = container.querySelector<HTMLInputElement>(`[name="${name}"]`);
  if (el === null) throw new Error(`falta el campo ${name}`);
  return el;
}

export function errorDe(container: ParentNode, name: string): string {
  return container.querySelector(`[data-error-for="${name}"]`)?.textContent ?? '';
}

export function escribir(container: ParentNode, name: string, valor: string): void {
  const el = input(container, name);
  el.value = valor;
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

export function boton(container: ParentNode, texto: string): HTMLButtonElement {
  const el = [...container.querySelectorAll('button')].find((b) => b.textContent === texto);
  if (el === undefined) throw new Error(`falta el botón ${texto}`);
  return el;
}

export function enviar(form: HTMLFormElement | null): void {
  if (form === null) throw new Error('falta el formulario');
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}
