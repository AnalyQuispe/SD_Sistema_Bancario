import { useCallback, useEffect, useState } from 'react';
import { cuentasDeCliente, mensajeDeError, BANCOS } from '../api/client';
import { useAuth } from '../context/AuthContext';
import ListaCuentas from '../components/ListaCuentas';
import FormDeposito from '../components/FormDeposito';
import FormRetiro from '../components/FormRetiro';

/**
 * Vista unificada de las cuentas del cliente en los 3 bancos + depósito/retiro.
 *
 * Da igual a qué banco esté conectado el cliente: el endpoint del banco conectado
 * une sus cuentas locales con las de los peers (vista global, Integrante 1).
 */
export default function DashboardPage() {
  const { sesion, banco } = useAuth();
  const [cuentas, setCuentas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const nombreConectado = BANCOS.find((b) => b.prefijo === banco)?.nombre;

  const cargar = useCallback(async () => {
    setError(null);
    try {
      setCuentas(await cuentasDeCliente(banco, sesion.clienteId));
    } catch (err) {
      setError(mensajeDeError(err));
      setCuentas([]);
    } finally {
      setCargando(false);
    }
  }, [banco, sesion.clienteId]);

  useEffect(() => {
    setCargando(true);
    cargar();
  }, [cargar]);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-800">Mis cuentas en la red de bancos</h2>
        <p className="text-sm text-slate-500">
          Consultadas a través de <b>{nombreConectado}</b>; incluye las cuentas de los otros
          bancos (vista global del sistema distribuido).
        </p>
      </div>

      <ListaCuentas cuentas={cuentas} cargando={cargando} error={error} />

      <div className="grid gap-6 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <FormDeposito cuentas={cuentas} alTerminar={cargar} />
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <FormRetiro cuentas={cuentas} alTerminar={cargar} />
        </div>
      </div>
    </div>
  );
}
