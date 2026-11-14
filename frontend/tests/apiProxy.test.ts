import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApiTokenMiddleware, websocketProxyOptions } from '../dev/apiProxy'
import { readApiToken, resolveApiTokenPath } from '../dev/token'

const temporaryDirectories: string[] = []
function tempDirectory() {
  const path = mkdtempSync(join(tmpdir(), 'focusdesktop-token-'))
  temporaryDirectories.push(path)
  return path
}
afterEach(() => temporaryDirectories.splice(0).forEach((path) => rmSync(path, { recursive: true, force: true })))

describe('development API token proxy', () => {
  it('resolves the backend data directory consistently and reads the token on demand', () => {
    expect(resolveApiTokenPath({ FOCUSDESKTOP_DATA_DIR: 'app-data' }, 'linux', '/home/example'))
      .toBe('/home/example/app-data/api-token')
    expect(resolveApiTokenPath({ XDG_DATA_HOME: '/tmp/data' }, 'linux', '/home/example'))
      .toBe('/tmp/data/focusdesktop/api-token')
    const dataDirectory = tempDirectory()
    const tokenFile = join(dataDirectory, 'api-token')
    const env = { FOCUSDESKTOP_DATA_DIR: dataDirectory } as NodeJS.ProcessEnv
    expect(readApiToken(env)).toBeUndefined()
    writeFileSync(tokenFile, 'secret-token\n')
    expect(readApiToken(env)).toBe('secret-token')
  })

  it('returns a clear 503 when the token is missing and injects it for API requests', () => {
    const use = vi.fn()
    const plugin = createApiTokenMiddleware(() => undefined)
    plugin({ middlewares: { use } } as never)
    const middleware = use.mock.calls[0][0] as (req: { url: string; headers: Record<string, string> }, res: Record<string, unknown>, next: () => void) => void
    const end = vi.fn()
    const setHeader = vi.fn()
    const response = { setHeader, end, statusCode: 0 } as unknown as Record<string, unknown>
    middleware({ url: '/api/v1/sessions', headers: {} }, response, vi.fn())
    expect(response.statusCode).toBe(503)
    expect(end).toHaveBeenCalledWith(expect.stringContaining('token is unavailable'))

    const authorizedUse = vi.fn()
    createApiTokenMiddleware(() => 'local-secret')({ middlewares: { use: authorizedUse } } as never)
    const authorized = { url: '/api/v1/sessions', headers: {} }
    const next = vi.fn()
    authorizedUse.mock.calls[0][0](authorized, {}, next)
    expect(authorized.headers.authorization).toBe('Bearer local-secret')
    expect(next).toHaveBeenCalledOnce()
  })

  it('injects the bearer header for WebSocket upgrades and rejects upgrades without a token', () => {
    const wsOptions = websocketProxyOptions(() => 'local-secret')
    const on = vi.fn()
    wsOptions.configure?.({ on } as never, {} as never)
    const proxyRequest = { setHeader: vi.fn(), destroy: vi.fn() }
    on.mock.calls[0][1](proxyRequest, {}, { write: vi.fn(), destroy: vi.fn() })
    expect(proxyRequest.setHeader).toHaveBeenCalledWith('Authorization', 'Bearer local-secret')

    const missingOptions = websocketProxyOptions(() => undefined)
    const missingOn = vi.fn()
    missingOptions.configure?.({ on: missingOn } as never, {} as never)
    const socket = { write: vi.fn(), destroy: vi.fn() }
    const outgoing = { destroy: vi.fn() }
    missingOn.mock.calls[0][1](outgoing, {}, socket)
    expect(outgoing.destroy).toHaveBeenCalledOnce()
    expect(socket.write).toHaveBeenCalledWith(expect.stringContaining('503 Service Unavailable'))
  })
})
