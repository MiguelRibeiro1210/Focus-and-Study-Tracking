import { isSessionResponse, type ActiveSession, type SessionResponse } from './sessionTypes'
import type { SessionApi } from './sessionApi'

export interface SessionEventEnvelope {
  eventId: string
  version: 1
  type: string
  occurredAt: string
  payload: SessionResponse
}

type SocketLike = Pick<WebSocket, 'close' | 'onopen' | 'onmessage' | 'onclose' | 'onerror'>

const knownEventTypes = new Set([
  'session.started', 'session.paused', 'session.resumed', 'session.completed', 'session.abandoned',
  'interval.completed', 'interval.started', 'break.skipped',
])

function parseEnvelope(data: unknown): SessionEventEnvelope | undefined {
  if (typeof data !== 'string') return undefined
  try {
    const value: unknown = JSON.parse(data)
    if (typeof value !== 'object' || value === null) return undefined
    const item = value as Record<string, unknown>
    if (item.version !== 1 || typeof item.eventId !== 'string' || !knownEventTypes.has(String(item.type))
      || typeof item.occurredAt !== 'string' || !isSessionResponse(item.payload)) return undefined
    return item as unknown as SessionEventEnvelope
  } catch {
    return undefined
  }
}

export interface SessionEventClientOptions {
  api: Pick<SessionApi, 'getActiveSession'>
  onSnapshot: (snapshot: ActiveSession) => void
  onError?: (error: unknown) => void
  createSocket?: (url: string) => SocketLike
  getUrl?: () => string
  maxRememberedEvents?: number
}

export function createSessionEventClient(options: SessionEventClientOptions) {
  const createSocket = options.createSocket ?? ((url) => new WebSocket(url))
  const getUrl = options.getUrl ?? (() => `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`)
  const maxRemembered = options.maxRememberedEvents ?? 256
  let socket: SocketLike | undefined
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let stopped = true
  let attempt = 0
  let revision = 0
  let reconciliation = 0
  const eventIds = new Set<string>()
  const eventOrder: string[] = []

  function reconcile() {
    const requestId = ++reconciliation
    const startingRevision = revision
    void options.api.getActiveSession().then((snapshot) => {
      if (stopped || requestId !== reconciliation) return
      if (startingRevision !== revision) {
        reconcile()
        return
      }
      options.onSnapshot(snapshot)
    }).catch((error: unknown) => options.onError?.(error))
  }

  function remember(id: string) {
    if (eventIds.has(id)) return false
    eventIds.add(id)
    eventOrder.push(id)
    if (eventOrder.length > maxRemembered) eventIds.delete(eventOrder.shift()!)
    return true
  }

  function scheduleReconnect() {
    if (stopped || reconnectTimer !== undefined) return
    const delay = Math.min(250 * (2 ** attempt), 5000)
    attempt += 1
    reconnectTimer = setTimeout(() => {
      reconnectTimer = undefined
      connect()
    }, delay)
  }

  function connect() {
    if (stopped) return
    try {
      const connection = createSocket(getUrl())
      socket = connection
      connection.onopen = () => {
        attempt = 0
        reconcile()
      }
      connection.onmessage = (message) => {
        const envelope = parseEnvelope(message.data)
        if (!envelope) {
          reconcile()
          return
        }
        if (!remember(envelope.eventId)) return
        revision += 1
        reconciliation += 1
        options.onSnapshot(envelope.payload)
      }
      connection.onclose = scheduleReconnect
      connection.onerror = () => connection.close()
    } catch (error) {
      options.onError?.(error)
      scheduleReconnect()
    }
  }

  return {
    start() {
      if (!stopped) return
      stopped = false
      connect()
    },
    reconcile,
    stop() {
      stopped = true
      reconciliation += 1
      if (reconnectTimer !== undefined) clearTimeout(reconnectTimer)
      reconnectTimer = undefined
      socket?.close()
      socket = undefined
    },
  }
}
