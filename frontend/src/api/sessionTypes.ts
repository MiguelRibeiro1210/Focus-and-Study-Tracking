export interface SessionResponse {
  sessionId: string
  sessionType: SessionType
  state: SessionState
  intervalType: SessionType
  intervalState: IntervalState
  intervalSequence: number
  plannedDurationSeconds: number
  actualDurationSeconds: number
  actualFocusDurationSeconds: number
  remainingSeconds: number
  createdAt: string
  startedAt: string | null
  endedAt: string | null
}

export type SessionType = 'WORK' | 'SHORT_BREAK' | 'LONG_BREAK'
export type SessionState = 'IDLE' | 'RUNNING' | 'PAUSED' | 'WAITING_FOR_NEXT_INTERVAL' | 'COMPLETED' | 'ABANDONED'
export type IntervalState = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'SKIPPED'

export interface ApiErrorBody {
  code: string
  message: string
  details: unknown
  timestamp: string
}

export type ActiveSession = SessionResponse | null

export function isSessionResponse(value: unknown): value is SessionResponse {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  const sessionTypes: readonly string[] = ['WORK', 'SHORT_BREAK', 'LONG_BREAK']
  const sessionStates: readonly string[] = ['IDLE', 'RUNNING', 'PAUSED', 'WAITING_FOR_NEXT_INTERVAL', 'COMPLETED', 'ABANDONED']
  const intervalStates: readonly string[] = ['PENDING', 'RUNNING', 'COMPLETED', 'SKIPPED']
  return typeof item.sessionId === 'string'
    && sessionTypes.includes(String(item.sessionType))
    && sessionStates.includes(String(item.state))
    && sessionTypes.includes(String(item.intervalType))
    && intervalStates.includes(String(item.intervalState))
    && Number.isInteger(item.intervalSequence)
    && Number.isFinite(item.plannedDurationSeconds)
    && Number.isFinite(item.actualDurationSeconds)
    && Number.isFinite(item.actualFocusDurationSeconds)
    && Number.isFinite(item.remainingSeconds)
    && typeof item.createdAt === 'string'
    && (typeof item.startedAt === 'string' || item.startedAt === null)
    && (typeof item.endedAt === 'string' || item.endedAt === null)
}
