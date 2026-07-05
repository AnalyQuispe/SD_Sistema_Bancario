import { useState } from 'react';
import { transferencia, mensajeDeError, BANCOS } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Mensaje from './Mensaje';

/**
 * Transferencia local o ENTRE BANCOS. La petición se envía al banco al que el
 * cliente está conectado: si origen y destino viven en bancos distintos, ese banco
 * actúa de COORDINADOR del Two-Phase Commit (Integrante 2) y el resultado llega
 * como COMMITTED o ABORTED.
 */
export default function FormTransferencia({ cuentas, alTerminar }) {
  const { banco } = useAuth();
  const [origen, setOrigen] = useState('');
  const [destino, setDestino] = useState('');
  const [monto, setMonto] = useState('');
  const [aviso, setAviso] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const nombreConectado = BANCOS.find((b) => b.prefijo === banco)?.nombre;
  const esEntreBancos =
    origen && destino && origen.charAt(0).toUpperCase() !== destino.charAt(0).toUpperCase();

  const enviar = async (e) => {
    e.preventDefault();
    setAviso(null);
    setEnviando(true);
    try {
      const resultado = await transferencia(banco, origen, destino.trim(), Number(monto));
      if (resultado.estado === 'COMMITTED') {
        setAviso({ tipo: 'ok', texto: `✓ COMMITTED — ${resultado.mensaje}` });
        setMonto('');
      } else {
        setAviso({ tipo: 'error', texto: `✗ ${resultado.estado} — ${resultado.mensaje}` });
      }
      alTerminar?.();
    } catch (err) {
      setAviso({ tipo: 'error', texto: mensajeDeError(err) });
    } finally {
      setEnviando(false);
    }
  };

  return (
    <form onSubmit={enviar} className="space-y-3">
      <h3 className="font-semibold text-slate-700">Nueva transferencia</h3>

      <label className="block text-sm text-slate-600">
        Cuenta origen (tuya)
        <select
          required
          value={origen}
          onChange={(e) => setOrigen(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="">— elige la cuenta —</option>
          {cuentas.map((c) => (
            <option key={c.numero} value={c.numero}>
              {c.numero} · {c.bancoId.replace('BANCO_', 'Banco ')} · saldo {c.saldo}
            </option>
          ))}
        </select>
      </label>

      <label className="block text-sm text-slate-600">
        Cuenta destino (propia o de otro cliente, en cualquier banco)
        <input
          required
          placeholder="p. ej. C-3001"
          value={destino}
          onChange={(e) => setDestino(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
        />
      </label>

      <label className="block text-sm text-slate-600">
        Monto
        <input
          required
          type="number"
          min="0.01"
          step="0.01"
          placeholder="Monto"
          value={monto}
          onChange={(e) => setMonto(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </label>

      {esEntreBancos && (
        <p className="rounded-md bg-indigo-50 px-3 py-2 text-xs text-indigo-700">
          ⚡ Transferencia <b>entre bancos</b>: {nombreConectado} coordinará un{' '}
          <b>Two-Phase Commit</b> (prepare → commit/abort) con los bancos dueños de las cuentas.
        </p>
      )}

      <button
        disabled={enviando}
        className="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {enviando ? 'Ejecutando…' : 'Transferir'}
      </button>

      <Mensaje aviso={aviso} />
    </form>
  );
}
