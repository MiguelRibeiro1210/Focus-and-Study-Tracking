import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { apiTokenProxyPlugin, backendTarget, websocketProxyOptions } from './dev/apiProxy.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [apiTokenProxyPlugin(), react()],
  clearScreen: false,
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    watch: {
      ignored: ['**/src-tauri/**'],
    },
    proxy: {
      '/api': { target: backendTarget, changeOrigin: false },
      '/ws': websocketProxyOptions(),
    },
  },
})
