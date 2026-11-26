import { toFormErrors } from '../config/form-errors';
import { formatearInstante } from '../config/zoned-time';
import type { FiltroPacientes, HistorialAusenciasDto, PacienteResponse, PacientesApi } from './pacientes-api';

function mensajeDeError(error: unknown): string {
  const errores = toFormErrors(error);
  return errores.general ?? Object.values(errores.byField)[0] ?? 'No se pudo completar la operación.';
}

/** Un texto de 7 u 8 dígitos (con o sin puntos) se busca como DNI exacto; el resto, como texto libre. */
export function filtroDeBusqueda(texto: string): FiltroPacientes {
  const limpio = texto.trim();
  if (limpio === '') return {};
  return /^\d{1,2}(\.?\d{3}){2}$/.test(limpio) ? { dni: limpio } : { q: limpio };
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, texto?: string): HTMLElementTagNameMap[K] {
  const e = document.createElement(tag);
  if (texto !== undefined) e.textContent = texto;
  return e;
}

function badgeAusencias(cantidad: number): HTMLSpanElement {
  const b = el('span', String(cantidad));
  b.className = 'badge';
  b.dataset['col'] = 'ausencias';
  return b;
}

/** Pantalla privada `/pacientes`: buscador y tabla (C-04 backend; la pantalla quedó fuera de C-08). */
export function createPacientesListPage(api: PacientesApi): (container: HTMLElement) => void {
  return (container) => {
    let consulta = 0;
    const titulo = el('h1', 'Pacientes');
    const form = el('form');
    form.setAttribute('role', 'search');
    const input = el('input');
    input.type = 'search';
    input.name = 'q';
    input.placeholder = 'Buscá por DNI o apellido';
    input.setAttribute('aria-label', 'Buscar paciente por DNI o apellido');
    const buscar = el('button', 'Buscar');
    buscar.type = 'submit';
    form.append(input, buscar);

    const alerta = el('p');
    alerta.setAttribute('role', 'alert');
    const vacio = el('p', 'No se encontraron pacientes.');
    vacio.hidden = true;

    const tabla = el('table');
    const encabezado = el('tr');
    for (const t of ['Apellido', 'Nombre', 'DNI', 'Email', 'Teléfono', 'Ausencias']) {
      const th = el('th', t);
      th.scope = 'col';
      encabezado.append(th);
    }
    const thead = el('thead');
    thead.append(encabezado);
    const tbody = el('tbody');
    tabla.append(thead, tbody);
    container.replaceChildren(titulo, form, alerta, vacio, tabla);

    function fila(p: PacienteResponse): HTMLTableRowElement {
      const tr = el('tr');
      tr.dataset['id'] = String(p.id);
      const celdas: ReadonlyArray<readonly [string, string]> = [
        ['apellido', p.apellido],
        ['nombre', p.nombre],
        ['dni', p.dni],
        ['email', p.email],
        ['telefono', p.telefono],
      ];
      for (const [col, texto] of celdas) {
        const td = el('td');
        td.dataset['col'] = col;
        if (col === 'apellido') {
          const a = el('a', texto);
          a.href = `/pacientes/${p.id}`;
          td.append(a);
        } else td.textContent = texto;
        tr.append(td);
      }
      const td = el('td');
      td.append(badgeAusencias(p.cantidadAusencias));
      tr.append(td);
      return tr;
    }

    function cargar(): void {
      consulta += 1;
      const esta = consulta;
      alerta.textContent = '';
      container.dataset['estadoCarga'] = 'cargando';
      api
        .buscar(filtroDeBusqueda(input.value))
        .then((lista) => {
          if (esta !== consulta) return;
          tbody.replaceChildren(...lista.map(fila));
          tabla.hidden = lista.length === 0;
          vacio.hidden = lista.length > 0;
          container.dataset['estadoCarga'] = 'listo';
        })
        .catch((error: unknown) => {
          if (esta !== consulta) return;
          tbody.replaceChildren();
          tabla.hidden = true;
          vacio.hidden = true;
          alerta.textContent = mensajeDeError(error);
          container.dataset['estadoCarga'] = 'error';
        });
    }

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      cargar();
    });
    cargar();
  };
}

/** Ficha `/pacientes/:id`: datos de contacto, ausencias (US-061) y un hueco para la historia clínica (C-05). */
export function createPacienteDetallePage(api: PacientesApi): (container: HTMLElement, id: string) => void {
  return (container, idTexto) => {
    const titulo = el('h1', 'Paciente');
    const volver = el('a', 'Volver al listado');
    volver.href = '/pacientes';
    const alerta = el('p');
    alerta.setAttribute('role', 'alert');
    const datos = el('dl');
    const ausenciasSec = el('section');
    const hc = el('section');
    hc.dataset['seccion'] = 'historia-clinica';
    hc.append(el('h2', 'Historia clínica'), el('p', 'Historia clínica — próximamente (C-05)'));
    container.replaceChildren(titulo, volver, alerta, datos, ausenciasSec, hc);

    const id = Number(idTexto);
    if (!Number.isInteger(id) || id <= 0) {
      alerta.textContent = 'Paciente inválido.';
      return;
    }

    function renderDatos(p: PacienteResponse): void {
      titulo.textContent = `${p.apellido}, ${p.nombre}`;
      const items: ReadonlyArray<readonly [string, string]> = [
        ['dni', p.dni],
        ['email', p.email],
        ['telefono', p.telefono],
        ['direccion', p.direccion ?? ''],
        ['fechaNacimiento', p.fechaNacimiento ?? ''],
      ];
      const nombres: Readonly<Record<string, string>> = {
        dni: 'DNI', email: 'Email', telefono: 'Teléfono', direccion: 'Dirección', fechaNacimiento: 'Fecha de nacimiento',
      };
      const nodos: HTMLElement[] = [];
      for (const [k, v] of items) {
        const dt = el('dt', nombres[k]);
        const dd = el('dd', v === '' ? '—' : v);
        dd.dataset['campo'] = k;
        nodos.push(dt, dd);
      }
      datos.replaceChildren(...nodos);
    }

    function renderAusencias(h: HistorialAusenciasDto): void {
      const cab = el('h2', 'Ausencias ');
      cab.append(badgeAusencias(h.cantidad));
      const hijos: HTMLElement[] = [cab];
      if (h.ausencias.length === 0) hijos.push(el('p', 'Sin ausencias registradas.'));
      else {
        const ul = el('ul');
        for (const a of h.ausencias) {
          const li = el('li', `${formatearInstante(a.inicio)} — ${a.prestacionNombre}`);
          li.dataset['turnoId'] = String(a.turnoId);
          ul.append(li);
        }
        hijos.push(ul);
      }
      ausenciasSec.replaceChildren(...hijos);
    }

    container.dataset['estadoCarga'] = 'cargando';
    Promise.all([api.obtener(id), api.ausencias(id)])
      .then(([p, h]) => {
        renderDatos(p);
        renderAusencias(h);
        container.dataset['estadoCarga'] = 'listo';
      })
      .catch((error: unknown) => {
        alerta.textContent = mensajeDeError(error);
        container.dataset['estadoCarga'] = 'error';
      });
  };
}
