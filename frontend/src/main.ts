import { bootstrap, defaultDeps, type BootstrapDeps } from './bootstrap';

export { bootstrap } from './bootstrap';

/**
 * En desarrollo, `VITE_CONFIG_MOCKS=true npm run dev` usa APIs en memoria (sin backend).
 * La rama entera desaparece del build de producción porque `import.meta.env.DEV` es `false`.
 */
async function resolverDeps(root: HTMLElement): Promise<BootstrapDeps> {
  const base = defaultDeps(root);
  if (import.meta.env.DEV && import.meta.env['VITE_CONFIG_MOCKS'] === 'true') {
    const [{ crearMocksDesarrollo }, { createConfigSections }] = await Promise.all([
      import('./dev/config-mocks'),
      import('./features/config'),
    ]);
    const mocks = crearMocksDesarrollo();
    console.info('[OdontoGestión] Modo mocks de desarrollo activo (contraseña de prueba: "demo").');
    return { ...base, ...mocks.auth, configSections: createConfigSections(mocks.config, mocks.agenda) };
  }
  return base;
}

const root = document.getElementById('app');
if (root !== null) {
  void resolverDeps(root).then(bootstrap);
}
