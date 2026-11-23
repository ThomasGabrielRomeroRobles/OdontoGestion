/** Piezas DOM mínimas de la agenda. Nunca usa innerHTML con datos (solo textContent). */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  texto?: string,
  atributos: Readonly<Record<string, string>> = {},
): HTMLElementTagNameMap[K] {
  const elemento = document.createElement(tag);
  if (texto !== undefined) elemento.textContent = texto;
  for (const [nombre, valor] of Object.entries(atributos)) elemento.setAttribute(nombre, valor);
  return elemento;
}

export function boton(texto: string, alClick?: () => void): HTMLButtonElement {
  const b = el('button', texto, { type: 'button' });
  if (alClick !== undefined) b.addEventListener('click', alClick);
  return b;
}

export function alerta(): HTMLParagraphElement {
  return el('p', '', { role: 'alert' });
}
