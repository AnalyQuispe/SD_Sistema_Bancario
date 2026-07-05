import { BANCOS, nombreBanco } from '../api/client';

const formatoMoneda = (saldo, moneda) =>
  new Intl.NumberFormat('es-PE', { style: 'currency', currency: moneda || 'USD' }).format(saldo);

/**
 * Vista unificada de las cuentas del cliente en los 3 bancos (Hito 2).
 * Las cuentas llegan ya mezcladas desde un solo endpoint: el banco consultado
 * une su parte local con la de sus peers (Integrante 1); aquí solo se agrupan
 * por banco para que se vea que provienen de nodos distintos.
 */
export default function ListaCuentas({ cuentas, cargando, error }) {
  if (cargando) return <p className="text-sm text-slate-500">Cargando cuentas…</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!cuentas.length) return <p className="text-sm text-slate-500">Sin cuentas.</p>;

  const total = cuentas.reduce((suma, c) => suma + Number(c.saldo), 0);

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        {BANCOS.map((banco) => {
          const delBanco = cuentas.filter((c) => c.bancoId === banco.id);
          if (!delBanco.length) return null;
          return (
            <div key={banco.id} className="rounded-lg border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-100 px-4 py-2 text-sm font-semibold text-slate-600">
                {banco.nombre}
                <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-normal">
                  {delBanco.length} cuenta{delBanco.length !== 1 && 's'}
                </span>
              </div>
              <ul className="divide-y divide-slate-100">
                {delBanco.map((cuenta) => (
                  <li key={cuenta.numero} className="flex items-center justify-between px-4 py-2.5">
                    <span className="font-mono text-sm text-slate-700">{cuenta.numero}</span>
                    <span className="text-sm font-semibold text-slate-800">
                      {formatoMoneda(cuenta.saldo, cuenta.moneda)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </div>

      <p className="text-right text-sm text-slate-600">
        Saldo total en la red:{' '}
        <span className="font-bold text-slate-800">{formatoMoneda(total, cuentas[0]?.moneda)}</span>
        {' · '}
        {cuentas.length} cuentas en{' '}
        {new Set(cuentas.map((c) => c.bancoId)).size} banco(s)
      </p>
    </div>
  );
}

export { formatoMoneda, nombreBanco };
