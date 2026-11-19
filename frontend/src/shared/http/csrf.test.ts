import { afterEach, describe, expect, it } from 'vitest';
import { readCsrfToken } from './csrf';

function clearCookies(): void {
  for (const pair of document.cookie.split(';')) {
    const name = pair.split('=')[0]?.trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  }
}

describe('readCsrfToken', () => {
  afterEach(clearCookies);

  it('lee el valor de la cookie XSRF-TOKEN', () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    expect(readCsrfToken()).toBe('abc123');
  });

  it('decodifica caracteres codificados en URL (a%2Bb -> a+b)', () => {
    document.cookie = 'XSRF-TOKEN=a%2Bb; path=/';
    expect(readCsrfToken()).toBe('a+b');
  });

  it('devuelve null si no hay cookie XSRF-TOKEN', () => {
    document.cookie = 'otra=valor; path=/';
    expect(readCsrfToken()).toBeNull();
  });

  it('encuentra la cookie entre varias y no confunde nombres parecidos', () => {
    document.cookie = 'NOT-XSRF-TOKEN=falso; path=/';
    document.cookie = 'XSRF-TOKEN=real; path=/';
    document.cookie = 'z=1; path=/';
    expect(readCsrfToken()).toBe('real');
  });
});
