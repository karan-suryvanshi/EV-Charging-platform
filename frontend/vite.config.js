
import process from 'node:process'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const usePolling = process.env.VITE_USE_POLLING === 'true'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    host: '0.0.0.0',
    watch: {
      usePolling,
      interval: usePolling ? 500 : undefined,
    },
    hmr: {
      clientPort: Number(process.env.VITE_HMR_CLIENT_PORT || 5173),
    },
  },
})
