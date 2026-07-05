import { useState } from 'react';
import { login, mensajeDeError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Mensaje from '../components/Mensaje';

/**
 * Login por id de cliente. El gateway valida que el cliente exista en la red de
 * bancos y emite un JWT (Aporte A); sin ese token ninguna otra ruta responde.
 */
export default function LoginPage() {
  const { iniciarSesion } = useAuth();
  const [clienteId, setClienteId] = useState('');
  const [aviso, setAviso] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    setAviso(null);
    setEnviando(true);
    try {
      const datos = await login(clienteId.trim());
      iniciarSesion(datos);
    } catch (err) {
      setAviso({ tipo: 'error', texto: mensajeDeError(err) });
      setEnviando(false);
    }
  };

  return (
    <div className="mx-auto mt-16 max-w-md">
      <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-md">
        <h1 className="text-2xl font-bold text-slate-800">🏦 Bancos Asociados</h1>
        <p className="mt-1 text-sm text-slate-500">
          Sistema bancario distribuido — accede desde cualquier banco a todas tus cuentas.
        </p>

        <form onSubmit={enviar} className="mt-6 space-y-4">
          <label className="block text-sm font-medium text-slate-600">
            ID de cliente
            <input
              autoFocus
              required
              placeholder="p. ej. C200"
              value={clienteId}
              onChange={(e) => setClienteId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono"
            />
          </label>
          <button
            disabled={enviando}
            className="w-full rounded-md bg-blue-600 px-3 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {enviando ? 'Verificando…' : 'Ingresar'}
          </button>
          <Mensaje aviso={aviso} />
        </form>

        <div className="mt-6 rounded-md bg-slate-50 px-4 py-3 text-xs text-slate-500">
          <p className="font-semibold text-slate-600">Clientes de demostración</p>
          <p className="mt-1">
            <b>C200</b> — cuentas en los 3 bancos · <b>C100</b> — en A y B
            <br />
            <b>C001</b> (solo A) · <b>C002</b> (solo B) · <b>C003</b>/<b>C004</b> (solo C)
            <br />
            <b>ADMIN</b> — rol administrador (puede consultar a cualquier cliente)
          </p>
        </div>
      </div>
    </div>
  );
}
