import type { Plugin, ProxyOptions, ViteDevServer } from 'vite'
import { readApiToken } from './token.js'

export const backendTarget = 'http://127.0.0.1:8080'

export function createApiTokenMiddleware(tokenReader = readApiToken) {
  return (server: ViteDevServer) => {
    server.middlewares.use((request, response, next) => {
      if (!request.url?.startsWith('/api/')) {
        next()
        return
      }
      const token = tokenReader()
      if (!token) {
        response.statusCode = 503
        response.setHeader('Content-Type', 'text/plain; charset=utf-8')
        response.end('Local API token is unavailable. Start the backend and retry.')
        return
      }
      request.headers.authorization = `Bearer ${token}`
      next()
    })
  }
}

export function websocketProxyOptions(tokenReader = readApiToken): ProxyOptions {
  return {
    target: backendTarget,
    ws: true,
    configure(proxy) {
      proxy.on('proxyReqWs', (proxyRequest, _request, socket) => {
        const token = tokenReader()
        if (!token) {
          proxyRequest.destroy()
          socket.write('HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Type: text/plain\r\n\r\nLocal API token is unavailable. Start the backend and retry.')
          socket.destroy()
          return
        }
        proxyRequest.setHeader('Authorization', `Bearer ${token}`)
      })
    },
  }
}

export function apiTokenProxyPlugin(tokenReader = readApiToken): Plugin {
  return {
    name: 'focusdesktop-local-api-token-proxy',
    configureServer: createApiTokenMiddleware(tokenReader),
  }
}
