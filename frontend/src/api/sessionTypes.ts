export interface SessionResponse {
  sessionId: string
  projectName: string
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

export type SessionHistoryStatus = 'COMPLETED' | 'ABANDONED'

export interface SessionHistoryItem {
  sessionId: string
  projectName: string
  sessionType: SessionType
  status: SessionHistoryStatus
  startedAt: string
  endedAt: string
  actualFocusDurationSeconds: number
}

export interface SessionHistoryPage {
  items: SessionHistoryItem[]
  total: number
  page: number
  size: number
}

export interface SessionHistoryQuery {
  from?: string
  to?: string
  status?: SessionHistoryStatus
  page?: number
  size?: number
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
    && typeof item.projectName === 'string'
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

export function isSessionHistoryPage(value: unknown): value is SessionHistoryPage {
  if (typeof value !== 'object' || value === null) return false
  const page = value as Record<string, unknown>
  return Array.isArray(page.items)
    && page.items.every(isSessionHistoryItem)
    && Number.isInteger(page.total)
    && Number.isInteger(page.page)
    && Number.isInteger(page.size)
}

function isSessionHistoryItem(value: unknown): value is SessionHistoryItem {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return typeof item.sessionId === 'string'
    && typeof item.projectName === 'string'
    && item.sessionType === 'WORK'
    && (item.status === 'COMPLETED' || item.status === 'ABANDONED')
    && typeof item.startedAt === 'string'
    && typeof item.endedAt === 'string'
    && Number.isFinite(item.actualFocusDurationSeconds)
}
