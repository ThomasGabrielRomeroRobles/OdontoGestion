import type { AgendaConfigApi, DiaSemana, Horario } from '../agenda-config-api';
import { toFormErrors } from '../form-errors';
import { buscarSolapamiento, validarRangoHorario } from '../validators';
import type { SectionRenderer } from './config-page';
import {
  conBotonOcupado,
  crearAlerta,
  crearBoton,
  crearCampo,
  limpiarErrores,
  marcarError,
  mostrarErrorApi,
} from './form-kit';

export type HorariosSectionDeps = Pick<
  AgendaConfigApi,
  'listarHorarios' | 'crearHorario' | 'actualizarHorario' | 'eliminarHorario'
> & { readonly confirmar: (mensaje: string) => boolean };

const DIAS: readonly [DiaSemana, string][] = [
  [1, 'Lunes'],
  [2, 'Martes'],
  [3, 'Miércoles'],
  [4, 'Jueves'],
  [5, 'Viernes'],
  [6, 'Sábado'],
  [7, 'Domingo'],
];

const rango = (h: Pick<Horario, 'horaDesde' | 'horaHasta'>): string => `${h.horaDesde} – ${h.horaHasta}`;
const ordenar = (a: Horario, b: Horario): number => a.diaSemana - b.diaSemana || a.horaDesde.localeCompare(b.horaDesde);

function esDiaSemana(valor: number): valor is DiaSemana {
  return Number.isInteger(valor) && valor >= 1 && valor <= 7;
}

export function createHorariosSection(deps: HorariosSectionDeps): SectionRenderer {
  return (container) => {
    let horarios: Horario[] = [];
    let editandoId: number | null = null;

    const titulo = document.createElement('h2');
    titulo.textContent = 'Horarios semanales';
    const alerta = crearAlerta();
    const semana = document.createElement('div');

    const form = document.createElement('form');
    form.noValidate = true;
    form.dataset['form'] = 'horario';
    const tituloForm = document.createElement('h3');
    const wrapperDia = document.createElement('div');
    const labelDia = document.createElement('label');
    labelDia.textContent = 'Día';
    const selectDia = document.createElement('select');
    selectDia.name = 'diaSemana';
    selectDia.id = 'cfg-diaSemana';
    labelDia.htmlFor = selectDia.id;
    for (const [n, nombre] of DIAS) selectDia.append(new Option(nombre, String(n)));
    wrapperDia.append(labelDia, selectDia);
    const desde = crearCampo('horaDesde', 'Desde', { type: 'time' });
    const hasta = crearCampo('horaHasta', 'Hasta', { type: 'time' });
    const campos = { horaDesde: desde, horaHasta: hasta };
    const guardar = crearBoton('', 'submit');
    const cancelar = crearBoton('Cancelar edición');
    form.append(tituloForm, wrapperDia, desde.wrapper, hasta.wrapper, guardar, cancelar);

    container.replaceChildren(titulo, alerta, semana, form);

    function modoAlta(): void {
      editandoId = null;
      tituloForm.textContent = 'Agregar franja';
      guardar.textContent = 'Agregar';
      cancelar.hidden = true;
      desde.input.value = '';
      hasta.input.value = '';
      limpiarErrores(Object.values(campos), alerta);
    }

    function modoEdicion(horario: Horario): void {
      editandoId = horario.id;
      tituloForm.textContent = 'Editar franja';
      guardar.textContent = 'Guardar cambios';
      cancelar.hidden = false;
      selectDia.value = String(horario.diaSemana);
      desde.input.value = horario.horaDesde;
      hasta.input.value = horario.horaHasta;
      limpiarErrores(Object.values(campos), alerta);
    }

    function eliminar(horario: Horario, boton: HTMLButtonElement): void {
      if (!deps.confirmar(`¿Eliminar la franja ${rango(horario)}?`)) return;
      alerta.textContent = '';
      void conBotonOcupado(boton, () => deps.eliminarHorario(horario.id))
        .then(() => {
          horarios = horarios.filter((h) => h.id !== horario.id);
          if (editandoId === horario.id) modoAlta();
          renderSemana();
        })
        .catch((error: unknown) => {
          alerta.textContent = toFormErrors(error).general ?? 'No se pudo eliminar la franja.';
        });
    }

    function renderFranja(horario: Horario): HTMLLIElement {
      const li = document.createElement('li');
      li.dataset['id'] = String(horario.id);
      const texto = document.createElement('span');
      texto.dataset['col'] = 'rango';
      texto.textContent = rango(horario);
      const editar = crearBoton('Editar');
      editar.addEventListener('click', () => modoEdicion(horario));
      const borrar = crearBoton('Eliminar');
      borrar.addEventListener('click', () => eliminar(horario, borrar));
      li.append(texto, ' ', editar, ' ', borrar);
      return li;
    }

    function renderSemana(): void {
      semana.replaceChildren(
        ...DIAS.map(([n, nombre]) => {
          const bloque = document.createElement('section');
          bloque.dataset['dia'] = String(n);
          const h3 = document.createElement('h3');
          h3.textContent = nombre;
          const delDia = horarios.filter((h) => h.diaSemana === n);
          if (delDia.length === 0) {
            const p = document.createElement('p');
            p.textContent = 'Sin atención';
            bloque.append(h3, p);
          } else {
            const ul = document.createElement('ul');
            ul.append(...delDia.map(renderFranja));
            bloque.append(h3, ul);
          }
          return bloque;
        }),
      );
    }

    cancelar.addEventListener('click', modoAlta);

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      limpiarErrores(Object.values(campos), alerta);
      const diaSemana = Number(selectDia.value);
      if (!esDiaSemana(diaSemana)) {
        alerta.textContent = 'Elegí un día de la semana.';
        return;
      }
      const datos = { diaSemana, horaDesde: desde.input.value, horaHasta: hasta.input.value };
      const validacion = validarRangoHorario(datos.horaDesde, datos.horaHasta);
      if (!validacion.ok) {
        marcarError(campos[validacion.campo], validacion.error);
        return;
      }
      const conflicto = buscarSolapamiento(datos, horarios, editandoId ?? undefined);
      if (conflicto !== null) {
        marcarError(desde, `Se superpone con la franja ${rango(conflicto)}.`);
        return;
      }
      const id = editandoId;
      const peticion = id === null ? () => deps.crearHorario(datos) : () => deps.actualizarHorario(id, datos);
      void conBotonOcupado(guardar, peticion)
        .then((guardado) => {
          horarios = [...horarios.filter((h) => h.id !== guardado.id), guardado].sort(ordenar);
          modoAlta();
          renderSemana();
        })
        .catch((error: unknown) => mostrarErrorApi(error, campos, alerta));
    });

    modoAlta();
    renderSemana();

    deps
      .listarHorarios()
      .then((lista) => {
        horarios = [...lista].sort(ordenar);
        renderSemana();
      })
      .catch((error: unknown) => {
        alerta.textContent = toFormErrors(error).general ?? 'No se pudieron cargar los horarios.';
      });
  };
}
