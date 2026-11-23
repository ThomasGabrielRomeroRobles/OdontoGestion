import { bootstrap, defaultDeps, type BootstrapDeps } from './bootstrap';

export { bootstrap } from './bootstrap';

/**
 * En desarrollo, `VITE_CONFIG_MOCKS=true npm run dev` usa APIs en memoria (sin backend): configuración
 * (C-06) y agenda/pacientes contra el contrato provisional v0 (C-08, design D10).
 * La rama entera desaparece del build de producción porque `import.meta.env.DEV` es `false`.
 */
async function resolverDeps(root: HTMLElement): Promise<BootstrapDeps> {
  const base = defaultDeps(root);
  if (import.meta.env.DEV && import.meta.env['VITE_CONFIG_MOCKS'] === 'true') {
    const [{ crearMocksDesarrollo }, { crearAgendaMocks }, { createConfigSections }, { crearPublicoApiMock }, { crearAccionesApiMock }] = await Promise.all([
      import('./dev/config-mocks'),
      import('./dev/agenda-mocks'),
      import('./features/config'),
      import('./dev/publico-mocks'),
      import('./dev/acciones-mocks'),
    ]);
    const mocks = crearMocksDesarrollo();
    const agenda = crearAgendaMocks({
      listarPrestaciones: mocks.agenda.listarPrestaciones,
      listarHorarios: mocks.agenda.listarHorarios,
      listarBloqueos: mocks.agenda.listarBloqueos,
      obtenerConsultorio: mocks.config.obtenerConsultorio,
    });
    console.info('[OdontoGestión] Modo mocks de desarrollo activo (contraseña de prueba: "demo").');
    return {
      ...base,
      ...mocks.auth,
      configSections: createConfigSections(mocks.config, mocks.agenda),
      agenda: {
        api: agenda.api,
        pacientes: agenda.pacientes,
        listarPrestaciones: mocks.agenda.listarPrestaciones,
        obtenerConsultorio: mocks.config.obtenerConsultorio,
        confirmar: (mensaje) => window.confirm(mensaje),
        ahora: () => new Date(),
      },
      publicoApi: crearPublicoApiMock(),
      accionesApi: crearAccionesApiMock(),
    };
  }
  return base;
}

const root = document.getElementById('app');
if (root !== null) {
  void resolverDeps(root).then(bootstrap);
}
