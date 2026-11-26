import { describe, expect, it } from 'vitest';
import { renderLanding } from './landing-page';

describe('landing /inicio', () => {
  it('ofrece pedir turno y lista de espera a pacientes, e ingresar solo para el odontólogo', () => {
    const root = document.createElement('div');
    renderLanding(root);
    const hrefs = [...root.querySelectorAll<HTMLAnchorElement>('a[data-link]')].map((a) => a.getAttribute('href'));
    expect(hrefs).toContain('/publico');
    expect(hrefs).toContain('/publico/lista-espera');
    expect(hrefs).toContain('/login');
    expect(root.querySelector('h1')?.textContent).toContain('sin llamadas');
    expect(root.textContent).not.toMatch(/crear (tu )?cuenta|mi cuenta/i);
  });

  it('muestra los tres pasos y las tres tarjetas', () => {
    const root = document.createElement('div');
    renderLanding(root);
    expect(root.querySelectorAll('.landing__steps li')).toHaveLength(3);
    expect(root.querySelectorAll('.landing__feature')).toHaveLength(3);
  });
});
