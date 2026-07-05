import { useCallback, useEffect, useState } from 'react';
import {
  cuentasDeCliente,
  historialDeBanco,
  mensajeDeError,
  nombreBanco,
  BANCOS,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import FormTransferencia from '../components/FormTransferencia';

const colorEstado = {
  COMMITTED: 'bg-emerald-100 text-emerald-700',
  ABORTED: 'bg-red-100 text-red-700',
  PREPARED: 'bg-amber-100 text-amber-700',
  PENDING: 'bg-slate-100 text-slate-600',
};

/**
 * Transferencias (locales y entre bancos vía 2PC) + historial global de
 * transacciones con su estado (COMMITTED / ABORTED), consultando a los 3 bancos.
 */
export default function TransferenciasPage() {
  const { sesion, banco } = useAuth();
  const [cuentas, setCuentas] = useState([]);
  const [historial, setHistorial] = useState([]);
  const [errorHistorial, setErrorHistorial] = useState(null);

  const cargar = useCallback(async () => {
    try {
      setCuentas(await cuentasDeCliente(banco, sesion.clienteId));
    } catch {
      setCuentas([]);
    }

    // Historial global: se pregunta a los 3 bancos; un banco caído simplemente no aporta.
    setErrorHistorial(null);
    const porBanco = await Promise.all(
      BANCOS.map((b) =>
        historialDeBanco(b.prefijo)
          .then((txs) => txs.map((tx) => ({ ...tx, banco: b.id })))
          .catch(() => null),
      ),
    );
    if (porBanco.every((r) => r === null)) {
      setErrorHistorial('Ningún banco respondió al consultar el historial');
      setHistorial([]);
      return;
    }
    const todas = porBanco.filter(Boolean).flat();
    todas.sort((t1, t2) => (t2.timestamp ?? '').localeCompare(t1.timestamp ?? ''));
    setHistorial(todas);
  }, [banco, sesion.clienteId]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  return (
    <div className="grid gap-6 lg:grid-cols-[380px,1fr]">
      <div className="h-fit rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <FormTransferencia cuentas={cuentas} alTerminar={cargar} />
      </div>

      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h3 className="font-semibold text-slate-700">
            Historial de transacciones (los 3 bancos)
          </h3>
          <button
            onClick={cargar}
            className="rounded-md border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:bg-slate-50"
          >
            Actualizar
          </button>
        </div>

        {errorHistorial && <p className="px-5 py-4 text-sm text-red-600">{errorHistorial}</p>}

        <div className="max-h-[32rem] overflow-y-auto">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2">Fecha</th>
                <th className="px-4 py-2">Registrada en</th>
                <th className="px-4 py-2">Tipo</th>
                <th className="px-4 py-2">Origen → Destino</th>
                <th className="px-4 py-2 text-right">Monto</th>
                <th className="px-4 py-2">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {historial.map((tx) => (
                <tr key={`${tx.banco}-${tx.id}`}>
                  <td className="whitespace-nowrap px-4 py-2 text-xs text-slate-500">
                    {tx.timestamp ? new Date(tx.timestamp).toLocaleString('es-PE') : '—'}
                  </td>
                  <td className="px-4 py-2 text-xs">{nombreBanco(tx.banco)}</td>
                  <td className="px-4 py-2 text-xs">{tx.tipo}</td>
                  <td className="px-4 py-2 font-mono text-xs">
                    {tx.origen ?? '—'} → {tx.destino ?? '—'}
                  </td>
                  <td className="px-4 py-2 text-right font-medium">{tx.monto}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        colorEstado[tx.estado] ?? colorEstado.PENDING
                      }`}
                    >
                      {tx.estado}
                    </span>
                  </td>
                </tr>
              ))}
              {!historial.length && !errorHistorial && (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-400">
                    Aún no hay transacciones registradas.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
