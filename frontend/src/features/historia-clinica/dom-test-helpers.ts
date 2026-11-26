/** Espera a que se resuelvan las promesas pendientes (las APIs dobles resuelven en microtareas). */
export function esperar(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

export function requerir<T extends Element>(raiz: ParentNode, selector: string): T {
  const elemento = raiz.querySelector<T>(selector);
  if (elemento === null) throw new Error(`No se encontró ${selector}`);
  return elemento;
}

export function clickEnBoton(raiz: ParentNode, texto: string): void {
  const boton = [...raiz.querySelectorAll('button')].find((b) => b.textContent === texto);
  if (boton === undefined) throw new Error(`No hay botón "${texto}"`);
  boton.click();
}
