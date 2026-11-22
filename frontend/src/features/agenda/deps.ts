import type { Prestacion } from '../config/agenda-config-api';
import type { Consultorio } from '../config/config-api';
import type { AgendaApi } from './agenda-api';
import type { PacientesApi } from './pacientes-api';

/** Dependencias de las vistas de agenda: reales, mocks de desarrollo o dobles de test (design D2). */
export interface AgendaDeps {
  readonly api: AgendaApi;
  readonly pacientes: PacientesApi;
  readonly listarPrestaciones: () => Promise<Prestacion[]>;
  /** Para `parametros.toleranciaAusenteMin`; si falla, la tolerancia cae a 0 (design D5). */
  readonly obtenerConsultorio: () => Promise<Consultorio>;
  /** Confirmación explícita antes de marcar ausente o revertir. */
  readonly confirmar: (mensaje: string) => boolean;
  /** Reloj inyectable (los tests lo fijan). */
  readonly ahora: () => Date;
}
