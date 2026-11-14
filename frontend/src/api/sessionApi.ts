import { isSessionResponse, type ActiveSession, type ApiErrorBody, type SessionResponse } from './sessionTypes'

export type SessionApiErrorKind = 'http' | 'network' | 'protocol'

export class SessionApiError extends Error {
  readonly kind: SessionApiErrorKind
  readonly status?: number
  readonly code?: string
  readonly details?: unknown

  constructor(
    message: string,
    kind: SessionApiErrorKind,
    status?: number,
    code?: string,
    details?: unknown,
  ) {
    super(message)
    this.name = 'SessionApiError'
    this.kind = kind
    this.status = status
    this.code = code
    this.details = details
  }
}

export interface SessionApi {
  startSession(): Promise<SessionResponse>
  getActiveSession(): Promise<ActiveSession>
  pauseSession(sessionId: string): Promise<SessionResponse>
  resumeSession(sessionId: string): Promise<SessionResponse>
  stopSession(sessionId: string): Promise<SessionResponse>
  startNextInterval(sessionId: string): Promise<SessionResponse>
  skipBreak(sessionId: string): Promise<SessionResponse>
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false
  const body = value as Record<string, unknown>
  return typeof body.code === 'string' && typeof body.message === 'string'
}

export function createSessionApi(
  fetcher: typeof fetch = fetch,
  basePath = '/api/v1',
): SessionApi {
  async function request(path: string, init?: RequestInit, allowNoContent = false): Promise<SessionResponse | ActiveSession> {
    let response: Response
    try {
      response = await fetcher(`${basePath}${path}`, {
        ...init,
        headers: { Accept: 'application/json', ...init?.headers },
      })
    } catch (error) {
      throw new SessionApiError('Could not reach the local API. Check that FocusDesktop is running.', 'network', undefined, undefined, error)
    }

    if (allowNoContent && response.status === 204) return null

    let body: unknown
    try {
      body = await response.json()
    } catch (error) {
      if (!response.ok) {
        throw new SessionApiError(`The API request failed with HTTP ${response.status}.`, 'http', response.status)
      }
      throw new SessionApiError('The API returned an invalid response.', 'protocol', response.status, 'INVALID_RESPONSE', error)
    }

    if (!response.ok) {
      const apiError = isApiErrorBody(body) ? body : undefined
      throw new SessionApiError(
        apiError?.message ?? `The API request failed with HTTP ${response.status}.`,
        'http', response.status, apiError?.code, apiError?.details,
      )
    }
    if (!isSessionResponse(body)) {
      throw new SessionApiError('The API returned an invalid session response.', 'protocol', response.status, 'INVALID_RESPONSE')
    }
    return body
  }

  function command(sessionId: string, suffix: string) {
    return request(`/sessions/${encodeURIComponent(sessionId)}/${suffix}`, { method: 'POST' }) as Promise<SessionResponse>
  }

  return {
    startSession: () => request('/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionType: 'WORK' }),
    }) as Promise<SessionResponse>,
    getActiveSession: () => request('/sessions/active', { method: 'GET' }, true) as Promise<ActiveSession>,
    pauseSession: (id) => command(id, 'pause'),
    resumeSession: (id) => command(id, 'resume'),
    stopSession: (id) => command(id, 'stop'),
    startNextInterval: (id) => command(id, 'intervals/start'),
    skipBreak: (id) => command(id, 'skip-break'),
  }
}
