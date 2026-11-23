import { crearCampo, limpiarErrores, marcarError, mostrarErrorApi, type Campo } from '../../config/pages/form-kit';
import type { Turno } from '../agenda-api';
import type { AgendaDeps } from '../deps';
import { esApiError, traducirErrorAccion } from '../errores';
import type { PacienteResponse } from '../pacientes-api';
import { alerta, boton, el } from './dom';
import { crearSelectorHueco } from './selector-hueco';

/** Mismo formato que C-04: 7 u 8 dígitos con puntos de miles opcionales. */
const PATRON_DNI = /^\s*[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}\s*$/;
const MENSAJE_DNI = 'Debe tener 7 u 8 dígitos.';
/** Chequeo mínimo de cliente; el formato definitivo lo valida el servidor (400 por campo). */
const PATRON_EMAIL = /^[^\s@]+@[^\s@]+$/;

const normalizarDni = (dni: string): string => dni.replace(/[.\s]/g, '');
const describirPaciente = (p: PacienteResponse): string => `${p.apellido}, ${p.nombre} (DNI ${p.dni})`;

/** Flujo de creación (US-021, design D6): paciente → prestación y fecha → hueco → POST /turnos. */
export function createNuevoTurno(deps: AgendaDeps) {
  return (host: HTMLElement, alCrear: (turno: Turno) => void, cerrar: () => void): void => {
    let paciente: PacienteResponse | null = null;

    // --- Paciente -------------------------------------------------------------------------
    const seccionPaciente = el('section', undefined, { 'data-paso': 'paciente' });
    const dni = crearCampo('dni', 'DNI del paciente');
    const buscar = boton('Buscar');
    const avisoPaciente = alerta();
    const seleccionado = el('p', '', { 'data-paciente-seleccionado': '' });
    const cambiar = boton('Cambiar paciente', () => elegirPaciente(null));
    const altaHost = el('div');
    seccionPaciente.append(el('h3', 'Paciente'), dni.wrapper, buscar, avisoPaciente, altaHost);

    // --- Horario --------------------------------------------------------------------------
    const selector = crearSelectorHueco({
      disponibilidad: deps.api.disponibilidad,
      prestaciones: { tipo: 'lista', cargar: deps.listarPrestaciones },
    });
    const seccionHorario = el('section', undefined, { 'data-paso': 'horario' });
    seccionHorario.append(el('h3', 'Prestación y horario'), selector.elemento);

    const avisoTurno = el('p', '', { role: 'alert', 'data-aviso-turno': '' });
    const confirmar = boton('Confirmar turno');
    const botonCerrar = boton('Cerrar', cerrar);

    host.replaceChildren(el('h2', 'Nuevo turno'), seccionPaciente, seccionHorario, avisoTurno, confirmar, ' ', botonCerrar);

    function elegirPaciente(p: PacienteResponse | null): void {
      paciente = p;
      altaHost.replaceChildren();
      avisoPaciente.textContent = '';
      if (p === null) {
        seleccionado.remove();
        cambiar.remove();
        dni.wrapper.hidden = false;
        buscar.hidden = false;
        return;
      }
      seleccionado.textContent = describirPaciente(p);
      dni.wrapper.hidden = true;
      buscar.hidden = true;
      seccionPaciente.append(seleccionado, cambiar);
    }

    async function buscarDni(valor: string): Promise<PacienteResponse | null> {
      const encontrados = await deps.pacientes.buscarPorDni(valor);
      return encontrados[0] ?? null;
    }

    buscar.addEventListener('click', () => {
      marcarError(dni, null);
      avisoPaciente.textContent = '';
      const valor = dni.input.value;
      if (!PATRON_DNI.test(valor)) {
        marcarError(dni, MENSAJE_DNI);
        return;
      }
      buscar.disabled = true;
      buscarDni(valor)
        .then((encontrado) => {
          if (encontrado !== null) elegirPaciente(encontrado);
          else mostrarAlta(normalizarDni(valor));
        })
        .catch((error: unknown) => {
          avisoPaciente.textContent = traducirErrorAccion(error).mensaje;
        })
        .finally(() => {
          buscar.disabled = false;
        });
    });

    function mostrarAlta(dniInicial: string): void {
      const form = el('form', undefined, { 'data-form': 'alta-paciente' });
      form.noValidate = true;
      const campos: Record<'nombre' | 'apellido' | 'dni' | 'telefono' | 'email', Campo> = {
        nombre: crearCampo('nombre', 'Nombre'),
        apellido: crearCampo('apellido', 'Apellido'),
        dni: crearCampo('altaDni', 'DNI'),
        telefono: crearCampo('telefono', 'Teléfono', { type: 'tel' }),
        email: crearCampo('email', 'Email', { type: 'email' }),
      };
      campos.dni.input.value = dniInicial;
      const avisoAlta = alerta();
      const crear = boton('Crear paciente');
      crear.type = 'submit';
      form.append(
        el('p', 'No hay un paciente con ese DNI. Alta rápida:'),
        ...Object.values(campos).map((c) => c.wrapper),
        avisoAlta,
        crear,
      );
      altaHost.replaceChildren(form);

      form.addEventListener('submit', (event) => {
        event.preventDefault();
        limpiarErrores(Object.values(campos), avisoAlta);
        const valores = {
          nombre: campos.nombre.input.value.trim(),
          apellido: campos.apellido.input.value.trim(),
          dni: campos.dni.input.value.trim(),
          telefono: campos.telefono.input.value.trim(),
          email: campos.email.input.value.trim(),
        };
        let valido = true;
        for (const clave of ['nombre', 'apellido', 'dni', 'telefono', 'email'] as const) {
          if (valores[clave] === '') {
            marcarError(campos[clave], 'Es obligatorio.');
            valido = false;
          }
        }
        if (valores.dni !== '' && !PATRON_DNI.test(valores.dni)) {
          marcarError(campos.dni, MENSAJE_DNI);
          valido = false;
        }
        if (valores.email !== '' && !PATRON_EMAIL.test(valores.email)) {
          marcarError(campos.email, 'No es un email válido.');
          valido = false;
        }
        if (!valido) return;

        crear.disabled = true;
        deps.pacientes
          .crearPaciente(valores)
          .then(elegirPaciente)
          .catch(async (error: unknown) => {
            if (esApiError(error) && error.kind === 'http' && error.status === 409) {
              // DNI duplicado: el paciente ya existe → se busca y se selecciona (spec "DNI duplicado en el alta").
              const existente = await buscarDni(valores.dni).catch(() => null);
              if (existente !== null) {
                elegirPaciente(existente);
                return;
              }
            }
            mostrarErrorApi(error, campos, avisoAlta);
          })
          .finally(() => {
            crear.disabled = false;
          });
      });
    }

    // --- Confirmación ---------------------------------------------------------------------
    confirmar.addEventListener('click', () => {
      avisoTurno.textContent = '';
      const eleccion = selector.seleccion();
      if (paciente === null || eleccion === null) {
        avisoTurno.textContent = 'Elegí paciente, prestación y horario.';
        return;
      }
      confirmar.disabled = true;
      deps.api
        .crearTurno({ pacienteId: paciente.id, prestacionId: eleccion.prestacionId, inicio: eleccion.hueco.inicio })
        .then(alCrear)
        .catch((error: unknown) => {
          const traducido = traducirErrorAccion(error);
          avisoTurno.textContent = traducido.mensaje;
          if (traducido.conflicto) {
            void selector.refrescar();
            selector.mostrarAlternativas(traducido.alternativas);
          }
        })
        .finally(() => {
          confirmar.disabled = false;
        });
    });
  };
}
