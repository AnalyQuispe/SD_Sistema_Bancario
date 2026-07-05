import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Header from './components/Header';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TransferenciasPage from './pages/TransferenciasPage';

/** Solo deja pasar con sesión iniciada (JWT guardado). */
function RutaPrivada({ children }) {
  const { sesion } = useAuth();
  return sesion ? children : <Navigate to="/" replace />;
}

export default function App() {
  const { sesion } = useAuth();

  return (
    <div className="min-h-screen">
      {sesion && <Header />}
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/" element={sesion ? <Navigate to="/cuentas" replace /> : <LoginPage />} />
          <Route
            path="/cuentas"
            element={
              <RutaPrivada>
                <DashboardPage />
              </RutaPrivada>
            }
          />
          <Route
            path="/transferencias"
            element={
              <RutaPrivada>
                <TransferenciasPage />
              </RutaPrivada>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
