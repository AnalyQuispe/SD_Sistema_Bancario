import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// El frontend habla ÚNICAMENTE con el API Gateway (VITE_GATEWAY_URL, por
// defecto http://localhost:8080). Nunca con los puertos de los bancos.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
});
