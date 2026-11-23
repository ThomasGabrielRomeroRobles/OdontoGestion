import { describe, expect, it } from 'vitest';
import indexHtml from '../index.html?raw';

/** El documento no envía Referer: la URL de `/acciones/{token}` lleva el token (C-09 D8). */
describe('index.html', () => {
  const documento = new DOMParser().parseFromString(indexHtml, 'text/html');

  it('declara referrer no-referrer en el head', () => {
    const meta = documento.head.querySelector('meta[name="referrer"]');
    expect(meta?.getAttribute('content')).toBe('no-referrer');
  });

  it('declara una sola política de referer', () => {
    expect(documento.querySelectorAll('meta[name="referrer"]')).toHaveLength(1);
  });

  it('conserva el punto de montaje y el script de entrada', () => {
    expect(documento.getElementById('app')).not.toBeNull();
    expect(documento.querySelector('script[type="module"][src="/src/main.ts"]')).not.toBeNull();
  });
});
