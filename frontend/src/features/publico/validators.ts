/**
 * Validaciones de cliente de la solicitud pública (C-09). Espejan los formatos de `PacienteRequest` del backend
 * para evitar envíos inútiles; la autoridad es siempre el servidor (`400`).
 */
const DNI = /^\s*[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}\s*$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const OBLIGATORIO = 'Es obligatorio.';

export const MAX_NOMBRE = 80;
export const MAX_TELEFONO = 40;
export const MAX_EMAIL = 254;

/** Devuelve el mensaje de error, o `null` si el valor es válido. */
export function validarObligatorio(valor: string, max: number): string | null {
  const limpio = valor.trim();
  if (limpio === '') return OBLIGATORIO;
  return limpio.length > max ? `Máximo ${max} caracteres.` : null;
}

/** 7 u 8 dígitos, con puntos de miles opcionales (`30.123.456`, `7123456`). */
export function validarDni(valor: string): string | null {
  if (valor.trim() === '') return OBLIGATORIO;
  return DNI.test(valor) ? null : 'Debe tener 7 u 8 dígitos.';
}

export function validarEmail(valor: string): string | null {
  const limpio = valor.trim();
  if (limpio === '') return OBLIGATORIO;
  if (limpio.length > MAX_EMAIL) return `Máximo ${MAX_EMAIL} caracteres.`;
  return EMAIL.test(limpio) ? null : 'No es un email válido.';
}

export function validarTelefono(valor: string): string | null {
  return validarObligatorio(valor, MAX_TELEFONO);
}

export interface DatosPacienteFormulario {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string;
}

/** Errores por campo (vacío si todo es válido). */
export function validarDatosPaciente(datos: DatosPacienteFormulario): Readonly<Record<string, string>> {
  const errores: Record<string, string> = {};
  const candidatos: ReadonlyArray<readonly [string, string | null]> = [
    ['nombre', validarObligatorio(datos.nombre, MAX_NOMBRE)],
    ['apellido', validarObligatorio(datos.apellido, MAX_NOMBRE)],
    ['dni', validarDni(datos.dni)],
    ['email', validarEmail(datos.email)],
    ['telefono', validarTelefono(datos.telefono)],
  ];
  for (const [campo, error] of candidatos) {
    if (error !== null) errores[campo] = error;
  }
  return errores;
}
