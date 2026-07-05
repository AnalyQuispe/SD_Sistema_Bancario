import axios from 'axios';

/**
 * Cliente HTTP del frontend.
 *
 * REGLA DEL HITO 2: el frontend habla ÚNICAMENTE con el API Gateway
 * (por defecto http://localhost:8080). Nunca con los puertos de los bancos.
 * El gateway enruta según el prefijo: /a/** → Banco A, /b/** → Banco B, /c/** → Banco C.
 */
const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

export const api = axios.create({ baseURL: GATEWAY_URL });

// Adjunta el JWT (Aporte A · seguridad) a toda petición.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Si el gateway responde 401 (token ausente/expirado) se fuerza un nuevo login.
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const esLogin = error.config?.url?.includes('/auth/login');
    if (error.response?.status === 401 && !esLogin) {
      localStorage.removeItem('token');
      localStorage.removeItem('clienteId');
      localStorage.removeItem('rol');
      window.location.href = '/';
    }
    return Promise.reject(error);
  },
);

export const BANCOS = [
  { prefijo: 'a', id: 'BANCO_A', nombre: 'Banco A' },
  { prefijo: 'b', id: 'BANCO_B', nombre: 'Banco B' },
  { prefijo: 'c', id: 'BANCO_C', nombre: 'Banco C' },
];

export const nombreBanco = (bancoId) =>
  BANCOS.find((b) => b.id === bancoId)?.nombre ?? bancoId;

// ------------------------------------------------------------------ endpoints

export const login = (clienteId) =>
  api.post('/auth/login', { clienteId }).then((r) => r.data);

/** Vista global: el banco consultado une sus cuentas con las de sus peers (Integrante 1). */
export const cuentasDeCliente = (banco, clienteId) =>
  api.get(`/${banco}/api/clientes/${clienteId}/cuentas`).then((r) => r.data);

export const deposito = (banco, cuenta, monto) =>
  api.post(`/${banco}/api/operaciones/deposito`, { cuenta, monto }).then((r) => r.data);

export const retiro = (banco, cuenta, monto) =>
  api.post(`/${banco}/api/operaciones/retiro`, { cuenta, monto }).then((r) => r.data);

/** Transferencia local o entre bancos: si cruza bancos, el backend ejecuta el 2PC (Integrante 2). */
export const transferencia = (banco, cuentaOrigen, cuentaDestino, monto) =>
  api
    .post(`/${banco}/api/operaciones/transferencia`, { cuentaOrigen, cuentaDestino, monto })
    .then((r) => r.data);

/** Historial de UN banco; el global se arma consultando a los tres. */
export const historialDeBanco = (banco) =>
  api.get(`/${banco}/api/transacciones`).then((r) => r.data);

/** Salud de un banco (ruta pública, para el semáforo de nodos vivos). */
export const saludDeBanco = (banco) =>
  api.get(`/${banco}/actuator/health`, { timeout: 3000 }).then((r) => r.data);

/** Mensaje legible a partir de un error de Axios. */
export const mensajeDeError = (error) =>
  error.response?.data?.message ??
  error.response?.data?.mensaje ??
  (error.response ? `Error HTTP ${error.response.status}` : 'No se pudo contactar al gateway');
