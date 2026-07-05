/** Aviso de resultado de una operación: éxito (commit), error o abort. */
export default function Mensaje({ aviso }) {
  if (!aviso) return null;

  const estilos = {
    ok: 'border-emerald-300 bg-emerald-50 text-emerald-800',
    error: 'border-red-300 bg-red-50 text-red-800',
    info: 'border-blue-300 bg-blue-50 text-blue-800',
  };

  return (
    <div className={`rounded-md border px-3 py-2 text-sm ${estilos[aviso.tipo] ?? estilos.info}`}>
      {aviso.texto}
    </div>
  );
}
