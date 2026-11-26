import { isDesktopShell, nativeFetch } from './nativeTransport'
import type { BlockingAction, RuleTarget } from './blockingRulesApi'

export interface DistractionLogEntry {
  detectionId: string
  sessionId: string
  ruleId: string
  target: RuleTarget
  observationType: 'APPLICATION' | 'WEBSITE'
  application: { platform: string; executablePath: string | null; processName: string | null } | null
  host: string | null
  category: 'OTHER'
  action: BlockingAction
  occurredAt: string
  outcome: 'PENDING' | 'BLOCKED' | 'FAILED'
  outcomeAt: string | null
  blocked: boolean
  failureReason: string | null
}
export interface DistractionLogQuery { sessionId?: string; from?: string; to?: string; page: number; size: number }
export interface DistractionLogPage { items: DistractionLogEntry[]; total: number; page: number; size: number }
export interface DistractionLogApi { list(query: DistractionLogQuery): Promise<DistractionLogPage> }

export function createDistractionLogApi(fetcher?: typeof fetch): DistractionLogApi {
  const request = fetcher ?? (isDesktopShell() ? nativeFetch : fetch)
  return { async list(query) {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') params.set(key, String(value))
    const response = await request(`/api/v1/distractions?${params}`, { headers: { Accept: 'application/json' } })
    let body: unknown
    try { body = await response.json() }
    catch { throw new Error('Distraction history could not be read.') }
    if (!response.ok) {
      const error = body as { message?: unknown } | null
      throw new Error(typeof error?.message === 'string' ? error.message : 'Distraction history could not be loaded.')
    }
    const page = body as Partial<DistractionLogPage> | null
    if (!page || !Array.isArray(page.items) || !page.items.every(isEntry) || !Number.isSafeInteger(page.total) || page.total! < 0
      || page.page !== query.page || page.size !== query.size || page.items.length > query.size)
      throw new Error('The local API returned invalid distraction history.')
    return page as DistractionLogPage
  } }
}
function isEntry(value: unknown): value is DistractionLogEntry {
  if (!value || typeof value !== 'object') return false
  const entry = value as Partial<DistractionLogEntry>
  const target = entry.target
  return typeof entry.detectionId === 'string' && typeof entry.sessionId === 'string' && typeof entry.ruleId === 'string'
    && entry.category === 'OTHER' && ['LOG', 'WARN', 'BLOCK'].includes(String(entry.action))
    && ['PENDING', 'BLOCKED', 'FAILED'].includes(String(entry.outcome))
    && typeof entry.occurredAt === 'string' && Number.isFinite(Date.parse(entry.occurredAt))
    && typeof entry.blocked === 'boolean' && entry.blocked === (entry.outcome === 'BLOCKED')
    && (entry.outcome === 'PENDING' ? entry.outcomeAt === null && entry.failureReason === null
      : entry.action === 'BLOCK' && typeof entry.outcomeAt === 'string' && Number.isFinite(Date.parse(entry.outcomeAt))
        && Date.parse(entry.outcomeAt) >= Date.parse(entry.occurredAt)
        && (entry.outcome === 'FAILED' ? typeof entry.failureReason === 'string' && !!entry.failureReason.trim() : entry.failureReason === null))
    && !!target && (entry.observationType === 'WEBSITE' ? target.type === 'WEBSITE' && typeof target.domain === 'string'
      && typeof entry.host === 'string' && entry.application === null
      : entry.observationType === 'APPLICATION' && target.type === 'APPLICATION' && typeof target.value === 'string'
        && !!entry.application && typeof entry.application.platform === 'string' && entry.host === null
        && (typeof entry.application.executablePath === 'string' || typeof entry.application.processName === 'string'))
}
