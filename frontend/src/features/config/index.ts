import { agendaConfigApi, type AgendaConfigApi } from './agenda-config-api';
import { configApi, type ConfigApi } from './config-api';
import type { ConfigSections } from './pages/config-page';
import { createBloqueosSection } from './pages/bloqueos-section';
import { createConsultorioSection } from './pages/consultorio-section';
import { createHorariosSection } from './pages/horarios-section';
import { createPerfilSection } from './pages/perfil-section';
import { createPrestacionesSection } from './pages/prestaciones-section';

export { createConfigRoutes, type ConfigSections } from './pages/config-page';

const confirmarEnNavegador = (mensaje: string): boolean => window.confirm(mensaje);

/** Arma las cinco secciones sobre las APIs dadas (reales o, en desarrollo, en memoria). */
export function createConfigSections(
  config: ConfigApi,
  agenda: AgendaConfigApi,
  confirmar: (mensaje: string) => boolean = confirmarEnNavegador,
): ConfigSections {
  return {
    consultorio: createConsultorioSection(config),
    perfil: createPerfilSection(config),
    prestaciones: createPrestacionesSection({ ...agenda, obtenerConsultorio: config.obtenerConsultorio, confirmar }),
    horarios: createHorariosSection({ ...agenda, confirmar }),
    bloqueos: createBloqueosSection({ ...agenda, confirmar }),
  };
}

/** Secciones conectadas a la API real. */
export function createDefaultConfigSections(): ConfigSections {
  return createConfigSections(configApi, agendaConfigApi);
}
