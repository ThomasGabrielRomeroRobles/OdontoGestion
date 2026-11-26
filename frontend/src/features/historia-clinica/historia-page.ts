import { el } from '../agenda/pages/dom';
import { crearAnamnesisForm } from './anamnesis-form';
import { crearEvoluciones } from './evoluciones';
import type { HistoriaApi } from './historia-api';
import { estadoHttp, mensajeProblema } from './modelo';
import { crearOdontograma } from './odontograma';

const NO_ENCONTRADO = 'Paciente no encontrado';

/**
 * Pantalla `/pacientes/:id/historia` (C-05): encabezado con el paciente, anamnesis, odontograma y evoluciones. Los
 * datos clínicos solo viven en el DOM: nada en la URL, en storage ni en la consola (design D8).
 */
export function createHistoriaPage(api: HistoriaApi, ahora: () => Date) {
  return (container: HTMLElement, idParam: string | undefined): void => {
    if (idParam === undefined || !/^[1-9]\d{0,17}$/.test(idParam)) {
      container.replaceChildren(el('h1', NO_ENCONTRADO));
      return;
    }
    const pacienteId = Number(idParam);
    const vista = el('div', undefined, { class: 'historia-clinica', 'data-historia': '' });
    vista.append(el('p', 'Cargando historia clínica…'));
    container.replaceChildren(vista);

    api.obtener(pacienteId)
      .then((h) => {
        vista.replaceChildren(
          el('h1', `Historia clínica — ${h.paciente.apellido}, ${h.paciente.nombre}`),
          crearAnamnesisForm({ api, pacienteId, anamnesis: h.anamnesis }),
          crearOdontograma({ api, pacienteId, piezas: h.piezas }),
          crearEvoluciones({ api, pacienteId, evoluciones: h.evoluciones, ahora }),
        );
      })
      .catch((error: unknown) => {
        if (estadoHttp(error) === 404) vista.replaceChildren(el('h1', NO_ENCONTRADO));
        else vista.replaceChildren(el('p', mensajeProblema(error), { role: 'alert' }));
      });
  };
}
