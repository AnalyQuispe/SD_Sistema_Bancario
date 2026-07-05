import { useEffect, useState } from 'react';
import { BANCOS, saludDeBanco } from '../api/client';

/**
 * Semáforo de nodos vivos: consulta /x/actuator/health de cada banco (vía gateway)
 * cada pocos segundos. Sirve para demostrar la tolerancia a fallos en la demo:
 * al apagar un banco su punto pasa a rojo y el resto del sistema sigue operando.
 */
export default function BancoStatus() {
  const [estados, setEstados] = useState({ a: null, b: null, c: null });

  useEffect(() => {
    let activo = true;

    const comprobar = async () => {
      const resultados = await Promise.all(
        BANCOS.map((b) =>
          saludDeBanco(b.prefijo)
            .then((salud) => salud.status === 'UP')
            .catch(() => false),
        ),
      );
      if (activo) {
        setEstados({ a: resultados[0], b: resultados[1], c: resultados[2] });
      }
    };

    comprobar();
    const intervalo = setInterval(comprobar, 8000);
    return () => {
      activo = false;
      clearInterval(intervalo);
    };
  }, []);

  return (
    <div className="flex items-center gap-3" title="Estado de los nodos (healthcheck vía gateway)">
      {BANCOS.map((b) => (
        <span key={b.prefijo} className="flex items-center gap-1 text-xs text-slate-500">
          <span
            className={`inline-block h-2.5 w-2.5 rounded-full ${
              estados[b.prefijo] === null
                ? 'bg-slate-300'
                : estados[b.prefijo]
                  ? 'bg-emerald-500'
                  : 'bg-red-500'
            }`}
          />
          {b.nombre.replace('Banco ', '')}
        </span>
      ))}
    </div>
  );
}
