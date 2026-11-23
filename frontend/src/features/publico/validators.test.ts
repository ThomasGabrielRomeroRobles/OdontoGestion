import { describe, expect, it } from 'vitest';
import { validarDatosPaciente, validarDni, validarEmail, validarObligatorio, validarTelefono } from './validators';

describe('validarDni', () => {
  it.each(['30123456', '30.123.456', '7123456', '7.123.456', ' 30.123.456 ', '30123.456', '30.123456'])(
    '%s es válido (7 u 8 dígitos, con o sin puntos)',
    (dni) => {
      expect(validarDni(dni)).toBeNull();
    },
  );

  it.each(['12AB', '123456', '123456789', '30-123-456', '', '   ', '3.0123.456', 'abcdefgh', '30.12.3456'])(
    '%s es inválido',
    (dni) => {
      expect(validarDni(dni)).toBe(dni.trim() === '' ? 'Es obligatorio.' : 'Debe tener 7 u 8 dígitos.');
    },
  );
});

describe('validarEmail', () => {
  it('acepta un email común y recorta espacios', () => {
    expect(validarEmail('ana@ejemplo.test')).toBeNull();
    expect(validarEmail('  ana.ficticia+x@ejemplo.test  ')).toBeNull();
  });

  it.each(['', 'ana', 'ana@', '@ejemplo.test', 'ana@ejemplo', 'a na@ejemplo.test', 'ana@@ejemplo.test'])(
    '"%s" es inválido',
    (email) => {
      expect(validarEmail(email)).not.toBeNull();
    },
  );

  it('rechaza más de 254 caracteres', () => {
    expect(validarEmail(`${'a'.repeat(250)}@ejemplo.test`)).toBe('Máximo 254 caracteres.');
  });
});

describe('validarObligatorio y validarTelefono', () => {
  it('un texto vacío o en blanco es obligatorio', () => {
    expect(validarObligatorio('', 80)).toBe('Es obligatorio.');
    expect(validarObligatorio('   ', 80)).toBe('Es obligatorio.');
  });

  it('respeta el máximo de caracteres', () => {
    expect(validarObligatorio('a'.repeat(80), 80)).toBeNull();
    expect(validarObligatorio('a'.repeat(81), 80)).toBe('Máximo 80 caracteres.');
  });

  it('el teléfono es obligatorio y de hasta 40 caracteres', () => {
    expect(validarTelefono('+54 11 5555-0000')).toBeNull();
    expect(validarTelefono('')).toBe('Es obligatorio.');
    expect(validarTelefono('1'.repeat(41))).toBe('Máximo 40 caracteres.');
  });
});

describe('validarDatosPaciente', () => {
  const validos = {
    nombre: 'Ana',
    apellido: 'Ficticia',
    dni: '30.123.456',
    email: 'ana@ejemplo.test',
    telefono: '+54 11 5555-0000',
  };

  it('datos válidos no dan errores', () => {
    expect(validarDatosPaciente(validos)).toEqual({});
  });

  it('marca cada campo inválido por su nombre', () => {
    expect(validarDatosPaciente({ nombre: '', apellido: ' ', dni: '12AB', email: 'x', telefono: '' })).toEqual({
      nombre: 'Es obligatorio.',
      apellido: 'Es obligatorio.',
      dni: 'Debe tener 7 u 8 dígitos.',
      email: 'No es un email válido.',
      telefono: 'Es obligatorio.',
    });
  });

  it('solo marca los inválidos', () => {
    expect(validarDatosPaciente({ ...validos, dni: '123456' })).toEqual({ dni: 'Debe tener 7 u 8 dígitos.' });
  });
});
