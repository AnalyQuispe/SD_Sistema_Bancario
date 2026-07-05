import { createContext, useContext, useState } from 'react';

/**
 * Sesión del cliente (JWT emitido por el gateway) + banco al que está "conectado".
 *
 * El banco conectado simula la agencia a la que entra el cliente: todas las
 * peticiones salen hacia ese banco (vía gateway) y aun así el cliente ve TODAS
 * sus cuentas de la red — esa es la gracia del sistema distribuido.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => {
    const token = localStorage.getItem('token');
    return token
      ? {
          token,
          clienteId: localStorage.getItem('clienteId'),
          rol: localStorage.getItem('rol'),
        }
      : null;
  });

  const [banco, setBanco] = useState(localStorage.getItem('banco') || 'a');

  const iniciarSesion = ({ token, clienteId, rol }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('clienteId', clienteId);
    localStorage.setItem('rol', rol);
    setSesion({ token, clienteId, rol });
  };

  const cerrarSesion = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('clienteId');
    localStorage.removeItem('rol');
    setSesion(null);
  };

  const cambiarBanco = (prefijo) => {
    localStorage.setItem('banco', prefijo);
    setBanco(prefijo);
  };

  return (
    <AuthContext.Provider value={{ sesion, banco, iniciarSesion, cerrarSesion, cambiarBanco }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
