import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { apiTokenProxyPlugin, backendTarget, websocketProxyOptions } from './dev/apiProxy.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [apiTokenProxyPlugin(), react()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': { target: backendTarget, changeOrigin: false },
      '/ws': websocketProxyOptions(),
    },
  },
})
