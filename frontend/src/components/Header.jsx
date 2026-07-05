import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { BANCOS } from '../api/client';
import BancoStatus from './BancoStatus';

/**
 * Barra superior: navegación, selector del banco "conectado" (la agencia desde la
 * que opera el cliente), semáforo de nodos y cierre de sesión.
 */
export default function Header() {
  const { sesion, banco, cambiarBanco, cerrarSesion } = useAuth();

  const claseNav = ({ isActive }) =>
    `rounded-md px-3 py-1.5 text-sm font-medium ${
      isActive ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-200'
    }`;

  return (
    <header className="border-b border-slate-200 bg-white shadow-sm">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3">
        <span className="text-lg font-bold text-slate-800">🏦 Bancos Asociados</span>

        <nav className="flex gap-1">
          <NavLink to="/cuentas" className={claseNav}>
            Mis cuentas
          </NavLink>
          <NavLink to="/transferencias" className={claseNav}>
            Transferencias
          </NavLink>
        </nav>

        <div className="ml-auto flex flex-wrap items-center gap-4">
          <BancoStatus />

          <label className="flex items-center gap-2 text-sm text-slate-600">
            Conectado a:
            <select
              value={banco}
              onChange={(e) => cambiarBanco(e.target.value)}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
            >
              {BANCOS.map((b) => (
                <option key={b.prefijo} value={b.prefijo}>
                  {b.nombre}
                </option>
              ))}
            </select>
          </label>

          <span className="rounded-full bg-blue-50 px-3 py-1 text-sm font-medium text-blue-700">
            {sesion.clienteId}
          </span>

          <button
            onClick={cerrarSesion}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
          >
            Salir
          </button>
        </div>
      </div>
    </header>
  );
}
