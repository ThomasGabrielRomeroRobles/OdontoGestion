/** SOLO DESARROLLO: API de pacientes en memoria con datos 100 % ficticios. */
import type { PacientesApi, PacienteResponse } from '../features/pacientes/pacientes-api';

const PACIENTES: readonly PacienteResponse[] = [
  { id: 1, nombre: 'Lucía', apellido: 'Gómez', dni: '30111222', fechaNacimiento: '1990-04-12', telefono: '+54 11 5555-0101', email: 'lucia.gomez@ejemplo.test', direccion: 'Calle Falsa 123', cantidadAusencias: 2, activo: true },
  { id: 2, nombre: 'Beto', apellido: 'Demo', dni: '28333444', fechaNacimiento: null, telefono: '+54 11 5555-0102', email: 'beto.demo@ejemplo.test', direccion: null, cantidadAusencias: 0, activo: true },
  { id: 3, nombre: 'Zoilo', apellido: 'Quispe', dni: '35777888', fechaNacimiento: null, telefono: '+54 11 5555-0103', email: 'zoilo.quispe@ejemplo.test', direccion: null, cantidadAusencias: 1, activo: true },
];

export function crearPacientesApiMock(): PacientesApi {
  return {
    buscar: ({ dni, q }) => {
      const texto = (q ?? '').toLowerCase();
      const digitos = (dni ?? '').replace(/\./g, '');
      return Promise.resolve(
        PACIENTES.filter((p) =>
          digitos !== '' ? p.dni === digitos : `${p.apellido} ${p.nombre} ${p.email}`.toLowerCase().includes(texto),
        ),
      );
    },
    obtener: (id) => {
      const p = PACIENTES.find((x) => x.id === id);
      return p === undefined ? Promise.reject(new Error('404')) : Promise.resolve(p);
    },
    ausencias: (id) => {
      const p = PACIENTES.find((x) => x.id === id);
      const n = p?.cantidadAusencias ?? 0;
      return Promise.resolve({
        cantidad: n,
        ausencias: Array.from({ length: n }, (_, i) => ({ turnoId: 100 + i, inicio: `2026-11-${10 + i}T13:00:00Z`, prestacionNombre: i % 2 === 0 ? 'Consulta' : 'Limpieza' })),
      });
    },
  };
}
