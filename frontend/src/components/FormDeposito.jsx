import { useState } from 'react';
import { deposito, mensajeDeError } from '../api/client';
import Mensaje from './Mensaje';

/** Depósito sobre cualquiera de las cuentas del cliente (en cualquier banco). */
export default function FormDeposito({ cuentas, alTerminar }) {
  const [cuenta, setCuenta] = useState('');
  const [monto, setMonto] = useState('');
  const [aviso, setAviso] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    setAviso(null);
    setEnviando(true);
    try {
      // La operación va al banco DUEÑO de la cuenta: cada banco es autoridad de sus archivos.
      const bancoDueno = cuenta.charAt(0).toLowerCase();
      const actualizada = await deposito(bancoDueno, cuenta, Number(monto));
      setAviso({ tipo: 'ok', texto: `Depósito realizado. Nuevo saldo de ${cuenta}: ${actualizada.saldo}` });
      setMonto('');
      alTerminar?.();
    } catch (err) {
      setAviso({ tipo: 'error', texto: mensajeDeError(err) });
    } finally {
      setEnviando(false);
    }
  };

  return (
    <form onSubmit={enviar} className="space-y-3">
      <h3 className="font-semibold text-slate-700">Depósito</h3>
      <select
        required
        value={cuenta}
        onChange={(e) => setCuenta(e.target.value)}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      >
        <option value="">— elige la cuenta —</option>
        {cuentas.map((c) => (
          <option key={c.numero} value={c.numero}>
            {c.numero} · {c.bancoId.replace('BANCO_', 'Banco ')} · saldo {c.saldo}
          </option>
        ))}
      </select>
      <input
        required
        type="number"
        min="0.01"
        step="0.01"
        placeholder="Monto"
        value={monto}
        onChange={(e) => setMonto(e.target.value)}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      />
      <button
        disabled={enviando}
        className="w-full rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
      >
        {enviando ? 'Procesando…' : 'Depositar'}
      </button>
      <Mensaje aviso={aviso} />
    </form>
  );
}
