import { useCallback, useEffect, useRef, useState } from 'react'
import { SessionApiError, sessionApi, type SessionApi, type TimerOverride } from '../api/sessionApi'
import { createSessionEventClient, type SessionEventClientOptions } from '../api/sessionEvents'
import type { ActiveSession, SessionResponse } from '../api/sessionTypes'
import { createNativeWebSocket, isDesktopShell } from '../api/nativeTransport'

export type TimerAction = 'start' | 'pause' | 'resume' | 'stop' | 'startNextInterval' | 'skipBreak'

export interface TimerController {
  session: ActiveSession
  remainingSeconds: number | null
  isLoading: boolean
  pendingAction: TimerAction | null
  error: string | null
  runAction: (action: TimerAction, projectName?: string, timerOverride?: TimerOverride) => Promise<void>
  retry: () => void
}

interface TimerSessionDependencies {
  api?: SessionApi
  eventClientFactory?: (options: SessionEventClientOptions) => ReturnType<typeof createSessionEventClient>
  onIntervalCompleted?: (intervalType: SessionResponse['intervalType']) => void
}

const defaultEventClientFactory: NonNullable<TimerSessionDependencies['eventClientFactory']> = (options) =>
  createSessionEventClient({
    ...options,
    ...(isDesktopShell() ? { createSocket: () => createNativeWebSocket() } : {}),
  })

const POLL_INTERVAL_MS = 30_000
const DISPLAY_TICK_MS = 1_000
function errorMessage(error: unknown): string {
  if (error instanceof SessionApiError) {
    if (error.status === 401) return 'The local API rejected its token. Restart the backend and try again.'
    if (error.status === 503) return 'The local API token is not ready. Start the backend and retry.'
    if (error.kind === 'network') return 'The local API is unavailable. Check that the backend is running.'
    return error.message
  }
  return 'Could not synchronize with the local API. Please retry.'
}

function isTerminal(session: SessionResponse): boolean {
  return session.state === 'COMPLETED' || session.state === 'ABANDONED'
}

export function useTimerSession(dependencies: TimerSessionDependencies = {}): TimerController {
  const api = dependencies.api ?? sessionApi
  const eventClientFactory = dependencies.eventClientFactory ?? defaultEventClientFactory
  const [session, setSession] = useState<ActiveSession>(null)
  const [snapshotReceivedAt, setSnapshotReceivedAt] = useState(0)
  const [clockNow, setClockNow] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [pendingAction, setPendingAction] = useState<TimerAction | null>(null)
  const [commandError, setCommandError] = useState<string | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)
  const eventClientRef = useRef<ReturnType<typeof createSessionEventClient> | null>(null)
  const snapshotRevisionRef = useRef(0)
  const pendingActionRef = useRef(false)
  const sessionRef = useRef<ActiveSession>(null)
  const previousIntervalTypeRef = useRef<SessionResponse['intervalType'] | null>(null)
  const intervalCompletedHandlerRef = useRef(dependencies.onIntervalCompleted)

  useEffect(() => {
    intervalCompletedHandlerRef.current = dependencies.onIntervalCompleted
  }, [dependencies.onIntervalCompleted])

  const acceptSnapshot = useCallback((next: ActiveSession) => {
    snapshotRevisionRef.current += 1
    // The active-session endpoint returns 204 after a terminal response. Keep
    // that result visible until the learner starts another session.
    if (next === null && sessionRef.current !== null && isTerminal(sessionRef.current)) return
    const previous = sessionRef.current
    if (next === null || previous?.sessionId !== next.sessionId) {
      previousIntervalTypeRef.current = null
    } else if (next.intervalSequence > previous.intervalSequence) {
      previousIntervalTypeRef.current = previous.intervalType
    }
    const receivedAt = performance.now()
    sessionRef.current = next
    setSession(next)
    setSnapshotReceivedAt(receivedAt)
    setClockNow(receivedAt)
    setSyncError(null)
    setIsLoading(false)
  }, [])

  const reportSyncError = useCallback((cause: unknown) => {
    setSyncError(errorMessage(cause))
    setIsLoading(false)
  }, [])

  useEffect(() => {
    const client = eventClientFactory({
      api,
      onSnapshot: acceptSnapshot,
      onIntervalCompleted: (completedIntervalType, eventSnapshot) => {
        if (completedIntervalType) {
          intervalCompletedHandlerRef.current?.(completedIntervalType)
          return
        }
        const displayedSession = sessionRef.current
        const endedType = displayedSession?.sessionId === eventSnapshot.sessionId
          && displayedSession.intervalSequence < eventSnapshot.intervalSequence
          ? displayedSession.intervalType
          : previousIntervalTypeRef.current
        // Older backends do not include the completed type. If that interval
        // cannot be recovered from prior state, a next WORK interval implies
        // a break; short is the only safe legacy fallback available.
        intervalCompletedHandlerRef.current?.(endedType
          ?? (eventSnapshot.intervalType === 'WORK' ? 'SHORT_BREAK' : 'WORK'))
      },
      onError: reportSyncError,
    })
    eventClientRef.current = client
    client.start()
    // Reconcile immediately instead of waiting for the socket handshake.
    client.reconcile()
    return () => {
      client.stop()
      eventClientRef.current = null
    }
  }, [acceptSnapshot, api, eventClientFactory, reportSyncError])

  useEffect(() => {
    const timer = window.setInterval(() => setClockNow(performance.now()), DISPLAY_TICK_MS)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (session?.state !== 'RUNNING') return
    const timer = window.setInterval(() => eventClientRef.current?.reconcile(), POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [session?.sessionId, session?.state])

  const retry = useCallback(() => {
    setCommandError(null)
    setSyncError(null)
    setIsLoading(sessionRef.current === null)
    eventClientRef.current?.reconcile()
  }, [])

  const runAction = useCallback(async (action: TimerAction, projectName?: string, timerOverride?: TimerOverride) => {
    if (pendingActionRef.current) return
    pendingActionRef.current = true
    setPendingAction(action)
    setCommandError(null)
    setSyncError(null)
    const revisionAtStart = snapshotRevisionRef.current
    const current = sessionRef.current

    try {
      let result: SessionResponse
      switch (action) {
        case 'start':
          if (!projectName?.trim()) throw new SessionApiError('Enter a project name before starting.', 'http', 400, 'VALIDATION_ERROR')
          result = await api.startSession(projectName.trim(), timerOverride)
          break
        case 'pause':
          if (!current) return
          result = await api.pauseSession(current.sessionId)
          break
        case 'resume':
          if (!current) return
          result = await api.resumeSession(current.sessionId)
          break
        case 'stop':
          if (!current) return
          result = await api.stopSession(current.sessionId)
          break
        case 'startNextInterval':
          if (!current) return
          result = await api.startNextInterval(current.sessionId)
          break
        case 'skipBreak':
          if (!current) return
          result = await api.skipBreak(current.sessionId)
          break
      }

      if (snapshotRevisionRef.current === revisionAtStart) acceptSnapshot(result)
      eventClientRef.current?.reconcile()
    } catch (cause) {
      setCommandError(errorMessage(cause))
      eventClientRef.current?.reconcile()
    } finally {
      pendingActionRef.current = false
      setPendingAction(null)
    }
  }, [acceptSnapshot, api])

  let remainingSeconds: number | null = session?.remainingSeconds ?? null
  if (session?.state === 'RUNNING') {
    remainingSeconds = Math.max(0, Math.ceil(session.remainingSeconds - (clockNow - snapshotReceivedAt) / 1000))
  }

  return { session, remainingSeconds, isLoading, pendingAction, error: commandError ?? syncError, runAction, retry }
}
