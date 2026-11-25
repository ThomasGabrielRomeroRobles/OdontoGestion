import { publicoApi, type PrestacionPublica } from '../publico/publico-api';
import type { Route } from '../../shared/router/router';
import { createAvisoPage } from './aviso-page';
import { createInscripcionPage } from './inscripcion-page';
import { createListaEsperaPage } from './lista-espera-page';
import { listaEsperaApi, type ListaEsperaApi } from './lista-espera-api';

export type { ListaEsperaApi } from './lista-espera-api';

/**
 * Rutas de la lista de espera (C-10): inscripción y aviso son públicas (sin sesión ni layout del odontólogo);
 * `/lista-espera` es privada. Por defecto, conectadas a la API real. `listarPrestaciones` es el mismo cliente de
 * prestaciones públicas que usa `/publico` (lo inyecta `bootstrap`, así los mocks y los tests usan el mismo doble).
 */
export function createListaEsperaRoutes(
  api: ListaEsperaApi = listaEsperaApi,
  listarPrestaciones: () => Promise<readonly PrestacionPublica[]> = () => publicoApi.listarPrestaciones(),
): readonly Route[] {
  const renderInscripcion = createInscripcionPage(api, listarPrestaciones);
  const renderAviso = createAvisoPage(api);
  const renderListado = createListaEsperaPage(api);
  return [
    { path: '/publico/lista-espera', access: 'public', render: (container) => renderInscripcion(container) },
    {
      path: '/avisos/:token',
      access: 'public',
      render: (container, context) => {
        const token = context.params['token'];
        if (token !== undefined) renderAviso(container, token);
      },
    },
    { path: '/lista-espera', access: 'private', render: (container) => renderListado(container) },
  ];
}
