import { describe, expect, it, vi } from 'vitest'
import { createSessionApi, SessionApiError } from '../src/api/sessionApi'

const session = {
  sessionId: 'session-1', sessionType: 'WORK', state: 'RUNNING', intervalType: 'WORK',
  intervalState: 'RUNNING', intervalSequence: 1, plannedDurationSeconds: 1500,
  actualDurationSeconds: 0, actualFocusDurationSeconds: 0, remainingSeconds: 1500,
  createdAt: '2026-11-11T00:00:00Z', startedAt: '2026-11-11T00:00:00Z', endedAt: null,
}

describe('session REST client', () => {
  it('sends the start request and typed payload to the documented endpoint', async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json(session, { status: 201 }))
    const api = createSessionApi(fetcher)
    await expect(api.startSession()).resolves.toEqual(session)
    expect(fetcher).toHaveBeenCalledWith('/api/v1/sessions', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ sessionType: 'WORK' }),
    }))
  })

  it('maps active session 204 to null', async () => {
    const api = createSessionApi(vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(api.getActiveSession()).resolves.toBeNull()
  })

  it.each([
    ['pauseSession', 'pause'], ['resumeSession', 'resume'], ['stopSession', 'stop'],
    ['startNextInterval', 'intervals/start'], ['skipBreak', 'skip-break'],
  ] as const)('%s posts to its command route', async (method, route) => {
    const fetcher = vi.fn().mockResolvedValue(Response.json(session))
    const api = createSessionApi(fetcher)
    await api[method]('id/with space')
    expect(fetcher).toHaveBeenCalledWith(`/api/v1/sessions/id%2Fwith%20space/${route}`, expect.objectContaining({ method: 'POST' }))
  })

  it('preserves API error code, status and message', async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json({ code: 'INVALID_SESSION_STATE', message: 'Invalid state', details: null }, { status: 422 }))
    const api = createSessionApi(fetcher)
    await expect(api.stopSession('id')).rejects.toMatchObject({
      name: 'SessionApiError', kind: 'http', status: 422, code: 'INVALID_SESSION_STATE', message: 'Invalid state',
    })
  })

  it('converts network and malformed success responses into typed errors', async () => {
    const network = createSessionApi(vi.fn().mockRejectedValue(new Error('offline')))
    await expect(network.getActiveSession()).rejects.toMatchObject({ kind: 'network' })
    const malformed = createSessionApi(vi.fn().mockResolvedValue(Response.json({ sessionId: 'partial' })))
    await expect(malformed.getActiveSession()).rejects.toBeInstanceOf(SessionApiError)
    await expect(malformed.getActiveSession()).rejects.toMatchObject({ kind: 'protocol' })
  })
})
