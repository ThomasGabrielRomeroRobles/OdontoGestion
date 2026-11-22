import { toFormErrors } from '../config/form-errors';
import { formatearInstante } from '../config/zoned-time';
import {
  ESTADOS_NOTIFICACION,
  TIPOS_NOTIFICACION,
  type EstadoNotificacion,
  type FiltrosNotificaciones,
  type NotificacionDto,
  type NotificacionesApi,
  type Pagina,
  type TipoNotificacion,
} from './notificaciones-api';

const TAMANIO_PAGINA = 20;

const ETIQUETA_TIPO: Readonly<Record<TipoNotificacion, string>> = {
  CONFIRMAR_SOLICITUD: 'Confirmar solicitud',
  CONFIRMAR_LISTA_ESPERA: 'Confirmar lista de espera',
  CONFIRMACION_TURNO: 'Confirmación de turno',
  RECORDATORIO: 'Recordatorio',
  AVISO_HUECO: 'Aviso de hueco',
  CANCELACION: 'Cancelación',
  AUSENTE_REPROGRAMAR: 'Ausente: reprogramar',
};

const COLUMNAS = [
  ['tipo', 'Tipo'],
  ['destinatario', 'Destinatario'],
  ['estado', 'Estado'],
  ['intentos', 'Intentos'],
  ['error', 'Último error'],
  ['creada', 'Creada'],
  ['enviada', 'Enviada'],
  ['acciones', ''],
] as const;

type Columna = (typeof COLUMNAS)[number][0];

function esEstado(valor: string): valor is EstadoNotificacion {
  return (ESTADOS_NOTIFICACION as readonly string[]).includes(valor);
}

function esTipo(valor: string): valor is TipoNotificacion {
  return (TIPOS_NOTIFICACION as readonly string[]).includes(valor);
}

function mensajeDeError(error: unknown): string {
  const errores = toFormErrors(error);
  return errores.general ?? Object.values(errores.byField)[0] ?? '';
}

function crearSelect<T extends string>(
  name: string,
  etiqueta: string,
  opciones: readonly T[],
  textoDe: (valor: T) => string,
): { wrapper: HTMLLabelElement; select: HTMLSelectElement } {
  const wrapper = document.createElement('label');
  wrapper.append(`${etiqueta} `);
  const select = document.createElement('select');
  select.name = name;
  const todas = document.createElement('option');
  todas.value = '';
  todas.textContent = 'Todos';
  select.append(todas);
  for (const valor of opciones) {
    const option = document.createElement('option');
    option.value = valor;
    option.textContent = textoDe(valor);
    select.append(option);
  }
  wrapper.append(select);
  return { wrapper, select };
}

/** Pantalla privada `/notificaciones`: registro de envíos con filtros, paginación y reintento (C-03 D13). */
export function createNotificacionesPage(api: NotificacionesApi): (container: HTMLElement) => void {
  return (container) => {
    let estado: EstadoNotificacion | undefined;
    let tipo: TipoNotificacion | undefined;
    let paginaActual = 0;
    let totalPaginas = 0;
    let consulta = 0;

    const titulo = document.createElement('h1');
    titulo.textContent = 'Registro de envíos';

    const alerta = document.createElement('p');
    alerta.setAttribute('role', 'alert');

    const filtroEstado = crearSelect('estado', 'Estado', ESTADOS_NOTIFICACION, (e) => e);
    const filtroTipo = crearSelect('tipo', 'Tipo', TIPOS_NOTIFICACION, (t) => ETIQUETA_TIPO[t]);
    const filtros = document.createElement('div');
    filtros.append(filtroEstado.wrapper, filtroTipo.wrapper);

    const tabla = document.createElement('table');
    const thead = document.createElement('thead');
    const encabezado = document.createElement('tr');
    for (const [, texto] of COLUMNAS) {
      const th = document.createElement('th');
      th.scope = 'col';
      th.textContent = texto;
      encabezado.append(th);
    }
    thead.append(encabezado);
    const tbody = document.createElement('tbody');
    tabla.append(thead, tbody);

    const vacio = document.createElement('p');
    vacio.textContent = 'No hay envíos registrados.';
    vacio.hidden = true;

    const anterior = document.createElement('button');
    anterior.type = 'button';
    anterior.dataset['pagina'] = 'anterior';
    anterior.textContent = 'Anterior';
    const siguiente = document.createElement('button');
    siguiente.type = 'button';
    siguiente.dataset['pagina'] = 'siguiente';
    siguiente.textContent = 'Siguiente';
    const indicador = document.createElement('span');
    const paginacion = document.createElement('nav');
    paginacion.setAttribute('aria-label', 'Paginación');
    paginacion.append(anterior, indicador, siguiente);

    container.replaceChildren(titulo, alerta, filtros, tabla, vacio, paginacion);
    container.dataset['estadoCarga'] = 'cargando';

    function celda(fila: HTMLTableRowElement, columna: Columna, texto: string): HTMLTableCellElement {
      const td = document.createElement('td');
      td.dataset['col'] = columna;
      td.textContent = texto;
      fila.append(td);
      return td;
    }

    function renderFila(n: NotificacionDto): HTMLTableRowElement {
      const fila = document.createElement('tr');
      fila.dataset['id'] = String(n.id);
      celda(fila, 'tipo', ETIQUETA_TIPO[n.tipo]);
      celda(fila, 'destinatario', n.destinatario);
      celda(fila, 'estado', n.estado);
      celda(fila, 'intentos', String(n.intentos));
      celda(fila, 'error', n.error ?? '');
      celda(fila, 'creada', formatearInstante(n.creadoEn));
      celda(fila, 'enviada', n.enviadaEn === null ? '' : formatearInstante(n.enviadaEn));
      const acciones = celda(fila, 'acciones', '');
      if (n.estado === 'FALLIDA') {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.dataset['accion'] = 'reintentar';
        boton.textContent = 'Reintentar';
        boton.addEventListener('click', () => reintentarFila(n, fila, boton));
        acciones.append(boton);
      }
      return fila;
    }

    function reintentarFila(n: NotificacionDto, fila: HTMLTableRowElement, boton: HTMLButtonElement): void {
      alerta.textContent = '';
      boton.disabled = true;
      api
        .reintentar(n.id)
        .then((actualizada) => fila.replaceWith(renderFila(actualizada)))
        .catch((error: unknown) => {
          alerta.textContent = mensajeDeError(error);
          boton.disabled = false;
        });
    }

    function render(resultado: Pagina<NotificacionDto>): void {
      paginaActual = resultado.pagina;
      totalPaginas = resultado.totalPaginas;
      tbody.replaceChildren(...resultado.contenido.map(renderFila));
      vacio.hidden = resultado.contenido.length > 0;
      tabla.hidden = resultado.contenido.length === 0;
      anterior.disabled = paginaActual <= 0;
      siguiente.disabled = paginaActual + 1 >= totalPaginas;
      indicador.textContent = totalPaginas > 0 ? `Página ${paginaActual + 1} de ${totalPaginas}` : '';
    }

    function cargar(pagina: number): void {
      const filtrosConsulta: FiltrosNotificaciones = {
        ...(estado === undefined ? {} : { estado }),
        ...(tipo === undefined ? {} : { tipo }),
        page: pagina,
        size: TAMANIO_PAGINA,
      };
      // Solo la respuesta de la última consulta se dibuja (cambios de filtro rápidos).
      consulta += 1;
      const esta = consulta;
      alerta.textContent = '';
      container.dataset['estadoCarga'] = 'cargando';
      api
        .listar(filtrosConsulta)
        .then((resultado) => {
          if (esta === consulta) render(resultado);
        })
        .catch((error: unknown) => {
          if (esta === consulta) alerta.textContent = mensajeDeError(error);
        })
        .finally(() => {
          if (esta === consulta) container.dataset['estadoCarga'] = 'listo';
        });
    }

    filtroEstado.select.addEventListener('change', () => {
      const valor = filtroEstado.select.value;
      estado = esEstado(valor) ? valor : undefined;
      cargar(0);
    });
    filtroTipo.select.addEventListener('change', () => {
      const valor = filtroTipo.select.value;
      tipo = esTipo(valor) ? valor : undefined;
      cargar(0);
    });
    anterior.addEventListener('click', () => cargar(paginaActual - 1));
    siguiente.addEventListener('click', () => cargar(paginaActual + 1));

    cargar(0);
  };
}
