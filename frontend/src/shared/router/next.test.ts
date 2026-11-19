import { describe, expect, it } from 'vitest';
import { sanitizeNext } from './next';

describe('sanitizeNext', () => {
  it.each(['/pacientes', '/agenda', '/pacientes?q=ana', '/a/b?x=1#h'])('acepta la ruta interna %s', (value) => {
    expect(sanitizeNext(value)).toBe(value);
  });

  it.each([
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
    'pacientes',
    '',
    '   ',
  ])('rechaza %j y vuelve a /agenda', (value) => {
    expect(sanitizeNext(value)).toBe('/agenda');
  });

  it('null y undefined caen en /agenda', () => {
    expect(sanitizeNext(null)).toBe('/agenda');
    expect(sanitizeNext(undefined)).toBe('/agenda');
  });
});
