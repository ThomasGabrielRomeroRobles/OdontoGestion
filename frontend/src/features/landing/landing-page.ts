import type { Route } from '../../shared/router/router';

type Hijo = Node | string;

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  clase: string | null,
  ...hijos: Hijo[]
): HTMLElementTagNameMap[K] {
  const nodo = document.createElement(tag);
  if (clase !== null) nodo.className = clase;
  nodo.append(...hijos);
  return nodo;
}

function enlace(href: string, texto: string, clase: string): HTMLAnchorElement {
  const a = el('a', clase, texto);
  a.href = href;
  a.dataset['link'] = '';
  return a;
}

function ancla(hash: string, texto: string): HTMLAnchorElement {
  const a = el('a', null, texto);
  a.href = hash;
  return a;
}

const PASOS: readonly (readonly [string, string])[] = [
  ['01', 'Elegí qué te hace falta'],
  ['02', 'Mirá los horarios libres'],
  ['03', 'Confirmá desde tu email'],
];

const FEATURES: readonly (readonly [string, string, string])[] = [
  ['📅', 'Turnos online', 'Pedí tu turno cuando quieras, sin llamadas ni cuentas: solo elegís prestación y horario.'],
  ['✉️', 'Recordatorios por email', 'Te avisamos antes de tu turno para que no se te pase y el consultorio no pierda el horario.'],
  ['⏳', 'Lista de espera que llena huecos', 'Si no hay horario, anotate: cuando se libera uno, te escribimos con un enlace para tomarlo.'],
];

/** Landing pública `/inicio`: presentación del consultorio y accesos a pedir turno / lista de espera. */
export function renderLanding(container: HTMLElement): void {
  const marca = el('span', 'landing__brand', el('span', 'landing__logo', '🦷'), 'OdontoGestión');
  const barra = el(
    'header',
    'landing__bar',
    marca,
    el('nav', 'landing__links', ancla('#como-funciona', 'Cómo funciona'), enlace('/publico', 'Pedir turno', '')),
    enlace('/login', 'Ingresar', 'btn-pill'),
  );

  const hero = el(
    'section',
    'landing__hero',
    el(
      'div',
      'landing__copy',
      el('p', 'eyebrow', 'Consultorio odontológico'),
      el('h1', null, 'Tu turno en el consultorio, sin llamadas ni vueltas.'),
      el(
        'p',
        'landing__lead',
        'Pedí tu turno en el consultorio en pocos pasos, sin crear una cuenta. Te confirmamos y te recordamos por email.',
      ),
      el(
        'div',
        'landing__cta',
        enlace('/publico', 'Pedir turno', 'btn-pill btn-pill--primary'),
        enlace('/publico/lista-espera', 'Anotarme en lista de espera', 'btn-pill'),
      ),
    ),
    el(
      'div',
      'landing__panel',
      el('span', 'landing__circle landing__circle--a'),
      el('span', 'landing__circle landing__circle--b'),
      el(
        'div',
        'landing__card',
        el('div', 'landing__card-head', el('h2', null, 'Reservá en tres pasos'), el('span', 'pill-online', el('i', null), 'Sin cuenta')),
        el(
          'ol',
          'landing__steps',
          ...PASOS.map(([n, texto]) => el('li', null, el('span', 'landing__num', n), texto)),
        ),
      ),
    ),
  );

  const como = el(
    'section',
    'landing__section',
    el('p', 'eyebrow', 'Cómo funciona'),
    el('h2', null, 'Todo lo que necesitás, sin vueltas'),
    el(
      'div',
      'landing__features',
      ...FEATURES.map(([icono, titulo, texto]) =>
        el('article', 'landing__feature', el('span', 'landing__icon', icono), el('h3', null, titulo), el('p', null, texto)),
      ),
    ),
  );
  como.id = 'como-funciona';

  const pie = el('footer', 'landing__footer', '© OdontoGestión · Turnos e historia clínica para tu consultorio');

  container.replaceChildren(el('div', 'landing', barra, el('main', 'landing__main', hero, como), pie));
}

export function createLandingRoute(): Route {
  return { path: '/inicio', access: 'public', render: (container) => renderLanding(container) };
}
