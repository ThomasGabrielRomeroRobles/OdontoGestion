import { toFormErrors } from '../config/form-errors';
import { alerta, el } from '../agenda/pages/dom';
import type { Anamnesis, DatosAnamnesis, HistoriaApi } from './historia-api';
import { textoActualizacion } from './odontograma';

type CampoTexto = Exclude<keyof DatosAnamnesis, 'embarazo'>;

const CAMPOS: readonly (readonly [CampoTexto, string])[] = [
  ['alergias', 'Alergias'],
  ['medicacionActual', 'Medicación actual'],
  ['enfermedadesPrevias', 'Enfermedades previas'],
  ['antecedentesRelevantes', 'Antecedentes relevantes'],
  ['observaciones', 'Observaciones'],
];

export interface OpcionesAnamnesis {
  readonly api: Pick<HistoriaApi, 'guardarAnamnesis'>;
  readonly pacienteId: number;
  readonly anamnesis: Anamnesis;
}

/** Formulario de anamnesis (US-070): reemplazo completo, errores por campo y autor/fecha del último guardado. */
export function crearAnamnesisForm({ api, pacienteId, anamnesis }: OpcionesAnamnesis): HTMLElement {
  const seccion = el('section', undefined, { 'data-anamnesis': '' });
  const form = el('form', undefined, { novalidate: '' });
  const textos = new Map<CampoTexto, HTMLTextAreaElement>();
  const errores = new Map<string, HTMLElement>();

  for (const [campo, etiqueta] of CAMPOS) {
    const area = el('textarea', undefined, { name: campo, maxlength: '2000' });
    area.value = anamnesis[campo];
    textos.set(campo, area);
    const error = el('span', '', { 'data-error-for': campo, class: 'campo-error' });
    errores.set(campo, error);
    const label = el('label', `${etiqueta} `);
    label.append(area, error);
    form.append(label);
  }

  const embarazo = el('select', undefined, { name: 'embarazo' });
  for (const [valor, texto] of [['', 'Sin dato'], ['true', 'Sí'], ['false', 'No']] as const) {
    const opcion = el('option', texto, { value: valor });
    opcion.selected = valor === (anamnesis.embarazo === null ? '' : String(anamnesis.embarazo));
    embarazo.append(opcion);
  }
  const labelEmbarazo = el('label', 'Embarazo ');
  labelEmbarazo.append(embarazo);

  const meta = el('p', textoActualizacion(anamnesis.actualizadoPor?.nombre, anamnesis.actualizadoEn),
    { 'data-meta': '' });
  const aviso = alerta();
  const guardar = el('button', 'Guardar anamnesis', { type: 'submit' });
  form.append(labelEmbarazo, meta, aviso, guardar);

  function datos(): DatosAnamnesis {
    const texto = (campo: CampoTexto): string => textos.get(campo)?.value.trim() ?? '';
    return {
      alergias: texto('alergias'),
      medicacionActual: texto('medicacionActual'),
      enfermedadesPrevias: texto('enfermedadesPrevias'),
      antecedentesRelevantes: texto('antecedentesRelevantes'),
      embarazo: embarazo.value === '' ? null : embarazo.value === 'true',
      observaciones: texto('observaciones'),
    };
  }

  form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    guardar.disabled = true;
    aviso.textContent = '';
    errores.forEach((e) => (e.textContent = ''));
    api.guardarAnamnesis(pacienteId, datos())
      .then((guardada) => {
        meta.textContent = textoActualizacion(guardada.actualizadoPor?.nombre, guardada.actualizadoEn);
      })
      .catch((error: unknown) => {
        const { byField, general } = toFormErrors(error);
        for (const [campo, mensaje] of Object.entries(byField)) {
          const destino = errores.get(campo);
          if (destino !== undefined) destino.textContent = mensaje;
          else aviso.textContent = mensaje;
        }
        if (general !== null) aviso.textContent = general;
      })
      .finally(() => {
        guardar.disabled = false;
      });
  });

  seccion.append(el('h2', 'Anamnesis'), form);
  return seccion;
}
