import { describe, expect, it } from 'vitest';
import config from '../vite.config';

describe('proxy de desarrollo', () => {
  const proxy = config.server?.proxy ?? {};
  const apiProxy = proxy['/api'];

  it('reenvía /api al backend en localhost:8080', () => {
    expect(apiProxy).toBeDefined();
    expect(typeof apiProxy === 'object' && apiProxy.target).toBe('http://localhost:8080');
  });

  it('conserva el path sin reescritura', () => {
    expect(typeof apiProxy === 'object' && apiProxy.rewrite).toBeFalsy();
  });

  it('solo proxea /api (no hay otras rutas reenviadas)', () => {
    expect(Object.keys(proxy)).toEqual(['/api']);
  });
});
