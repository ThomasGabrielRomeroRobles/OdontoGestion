import { toFormErrors } from '../form-errors';

/** Piezas DOM compartidas por los formularios de configuración. Nunca usa innerHTML con datos. */
export interface Campo {
  readonly wrapper: HTMLDivElement;
  readonly input: HTMLInputElement;
  readonly error: HTMLParagraphElement;
}

export interface OpcionesCampo {
  readonly type?: string;
  readonly autocomplete?: AutoFill;
  readonly readOnly?: boolean;
  readonly min?: string;
  readonly step?: string;
}

let secuencia = 0;

export function crearCampo(name: string, etiqueta: string, opciones: OpcionesCampo = {}): Campo {
  secuencia += 1;
  const id = `cfg-${name}-${secuencia}`;
  const wrapper = document.createElement('div');
  const label = document.createElement('label');
  label.htmlFor = id;
  label.textContent = etiqueta;
  const input = document.createElement('input');
  input.id = id;
  input.name = name;
  input.type = opciones.type ?? 'text';
  if (opciones.autocomplete !== undefined) input.autocomplete = opciones.autocomplete;
  if (opciones.readOnly === true) input.readOnly = true;
  if (opciones.min !== undefined) input.min = opciones.min;
  if (opciones.step !== undefined) input.step = opciones.step;
  const error = document.createElement('p');
  error.dataset['errorFor'] = name;
  error.id = `${id}-error`;
  input.setAttribute('aria-describedby', error.id);
  wrapper.append(label, input, error);
  return { wrapper, input, error };
}

export function crearAlerta(): HTMLParagraphElement {
  const p = document.createElement('p');
  p.setAttribute('role', 'alert');
  return p;
}

export function crearEstado(): HTMLParagraphElement {
  const p = document.createElement('p');
  p.setAttribute('role', 'status');
  return p;
}

export function crearBoton(texto: string, type: 'submit' | 'button' = 'button'): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = type;
  button.textContent = texto;
  return button;
}

export function marcarError(campo: Campo, mensaje: string | null): void {
  campo.error.textContent = mensaje ?? '';
  if (mensaje === null) campo.input.removeAttribute('aria-invalid');
  else campo.input.setAttribute('aria-invalid', 'true');
}

export function limpiarErrores(campos: Iterable<Campo>, alerta: HTMLElement): void {
  for (const campo of campos) marcarError(campo, null);
  alerta.textContent = '';
}

/**
 * Muestra un error de la API: mensajes de 422 junto a cada campo conocido; si no hay campos
 * (o ninguno es de este formulario), un mensaje general en la alerta.
 */
export function mostrarErrorApi(error: unknown, campos: Readonly<Record<string, Campo>>, alerta: HTMLElement): void {
  const { byField, general } = toFormErrors(error);
  let alguno = false;
  for (const [nombre, mensaje] of Object.entries(byField)) {
    const campo = campos[nombre];
    if (campo !== undefined) {
      marcarError(campo, mensaje);
      alguno = true;
    }
  }
  if (general !== null) alerta.textContent = general;
  else if (!alguno) alerta.textContent = Object.values(byField).join(' ');
}

/** Ejecuta `accion` con el botón deshabilitado mientras dura (evita envíos dobles). */
export async function conBotonOcupado<T>(boton: HTMLButtonElement, accion: () => Promise<T>): Promise<T> {
  boton.disabled = true;
  try {
    return await accion();
  } finally {
    boton.disabled = false;
  }
}
