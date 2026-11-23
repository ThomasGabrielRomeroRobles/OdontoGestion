import { publicoApi } from '../publico/publico-api';
import type { Route } from '../../shared/router/router';
import { createAvisoPage } from './aviso-page';
import { createInscripcionPage } from './inscripcion-page';
import { createListaEsperaPage } from './lista-espera-page';
import { listaEsperaApi, type ListaEsperaApi } from './lista-espera-api';

export type { ListaEsperaApi } from './lista-espera-api';

/**
 * Rutas de la lista de espera (C-10): inscripción y aviso son públicas (sin sesión ni layout del odontólogo);
 * `/lista-espera` es privada. Por defecto, conectadas a la API real.
 */
export function createListaEsperaRoutes(api: ListaEsperaApi = listaEsperaApi): readonly Route[] {
  const renderInscripcion = createInscripcionPage(api, () => publicoApi.listarPrestaciones());
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
