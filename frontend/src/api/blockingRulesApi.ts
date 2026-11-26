import { isDesktopShell, nativeFetch } from './nativeTransport'

export type TargetPlatform = 'WINDOWS' | 'MACOS' | 'LINUX'
export type ApplicationMatchKind = 'EXECUTABLE_PATH' | 'PROCESS_NAME'
export type BlockingAction = 'LOG' | 'WARN' | 'BLOCK'
export type RuleTarget = { type: 'WEBSITE'; domain: string }
  | { type: 'APPLICATION'; platform: TargetPlatform; matchKind: ApplicationMatchKind; value: string }
export interface BlockingRuleInput {
  displayName: string
  target: RuleTarget
  category: 'OTHER'
  action: BlockingAction
  enabled: boolean
}
export interface BlockingRule extends BlockingRuleInput { id: string; createdAt: string; updatedAt: string }
export interface BlockingStatus {
  monitorEnabled: boolean
  applicationDetection: 'DISABLED' | 'IDLE' | 'OBSERVING' | 'UNAVAILABLE'
  applicationReason: string
  applicationEnforcement: boolean
  websiteEnforcement: boolean
  checkedAt: string
  applicationProtectionEnabled?: boolean
  applicationProtectionState?: string
  ownedRestrictions?: number
  notice?: ProtectionNotice | null
}
export interface ProtectionNotice { id: string; kind: 'WARN' | 'ERROR'; message: string; occurredAt: string }
export interface ProtectionPreferences { applicationsEnabled: boolean; applicationConsentAt: string | null }
export interface WebsiteProtectionStatus {
  enabled: boolean
  state: 'DISABLED' | 'STARTING' | 'READY' | 'VERIFIED' | 'RESTORING' | 'UNAVAILABLE'
  routingVerified: boolean
  verificationUrl: string | null
  reason: string
  notice?: ProtectionNotice | null
}
export interface BlockingRulesApi {
  list(): Promise<BlockingRule[]>
  create(input: BlockingRuleInput): Promise<BlockingRule>
  patch(id: string, input: Partial<BlockingRuleInput>): Promise<BlockingRule>
  delete(id: string): Promise<void>
  status?(): Promise<BlockingStatus>
  setApplicationProtection?(enabled: boolean): Promise<ProtectionPreferences>
  websiteStatus?(): Promise<WebsiteProtectionStatus>
  setWebsiteProtection?(enabled: boolean): Promise<WebsiteProtectionStatus>
}

function isRule(value: unknown): value is BlockingRule {
  if (typeof value !== 'object' || value === null) return false
  const rule = value as Partial<BlockingRule>
  const target = rule.target
  return typeof rule.id === 'string' && typeof rule.displayName === 'string'
    && typeof rule.enabled === 'boolean' && rule.category === 'OTHER'
    && ['LOG', 'WARN', 'BLOCK'].includes(String(rule.action))
    && typeof rule.createdAt === 'string' && Number.isFinite(Date.parse(rule.createdAt))
    && typeof rule.updatedAt === 'string' && Number.isFinite(Date.parse(rule.updatedAt))
    && !!target && ((target.type === 'WEBSITE' && typeof target.domain === 'string')
      || (target.type === 'APPLICATION' && ['WINDOWS', 'MACOS', 'LINUX'].includes(target.platform)
        && ['EXECUTABLE_PATH', 'PROCESS_NAME'].includes(target.matchKind) && typeof target.value === 'string'))
}

export function targetLabel(target: RuleTarget): string { return target.type === 'WEBSITE' ? target.domain : target.value }

export function createBlockingRulesApi(fetcher?: typeof fetch): BlockingRulesApi {
  const requestFetch = fetcher ?? (isDesktopShell() ? nativeFetch : fetch)
  async function request(path = '', method = 'GET', input?: Partial<BlockingRuleInput> | { applicationsEnabled: boolean; consent?: string } | { enabled: boolean; consent?: string }, base = '/api/v1/blocking-rules'): Promise<unknown> {
    const response = await requestFetch(`${base}${path}`, {
      method, headers: { Accept: 'application/json', ...(input ? { 'Content-Type': 'application/json' } : {}) },
      body: input ? JSON.stringify(input) : undefined,
    })
    if (response.status === 204 && method === 'DELETE') return undefined
    let body: unknown
    try { body = await response.json() } catch { throw new Error(`The rule request returned invalid JSON (HTTP ${response.status}).`) }
    if (!response.ok) {
      const error = body as { message?: unknown; details?: unknown } | null
      const details = Array.isArray(error?.details) ? error.details.filter((item) => typeof item === 'string').join('; ') : ''
      throw new Error(`${typeof error?.message === 'string' ? error.message : 'The rule request failed.'}${details ? `: ${details}` : ''} (HTTP ${response.status})`)
    }
    return body
  }
  async function stored(path: string, method: string, input: Partial<BlockingRuleInput>): Promise<BlockingRule> {
    const body = await request(path, method, input)
    if (!isRule(body)) throw new Error('The local API returned an invalid rule.')
    return body
  }
  return {
    websiteStatus: async () => validateWebsiteStatus(await request('', 'GET', undefined, '/api/v1/website-protection')),
    setWebsiteProtection: async (enabled) => validateWebsiteStatus(await request('', 'PATCH', { enabled,
      ...(enabled ? { consent: 'FORWARD_SYSTEM_PROXY_TRAFFIC' } : {}) }, '/api/v1/website-protection')),
    status: async () => {
      const body = await request('', 'GET', undefined, '/api/v1/blocking-status') as Partial<BlockingStatus> | null
      if (!body || typeof body.monitorEnabled !== 'boolean' || typeof body.applicationReason !== 'string'
        || !['DISABLED', 'IDLE', 'OBSERVING', 'UNAVAILABLE'].includes(String(body.applicationDetection))
        || typeof body.applicationEnforcement !== 'boolean' || typeof body.websiteEnforcement !== 'boolean'
        || (body.applicationProtectionEnabled !== undefined && typeof body.applicationProtectionEnabled !== 'boolean')
        || (body.applicationProtectionState !== undefined && !['DISABLED', 'READY', 'ACTIVE', 'RESTORING', 'UNAVAILABLE'].includes(body.applicationProtectionState))
        || (body.ownedRestrictions !== undefined && (!Number.isInteger(body.ownedRestrictions) || body.ownedRestrictions < 0))
        || (body.notice != null && (typeof body.notice.id !== 'string' || !['WARN', 'ERROR'].includes(body.notice.kind)
          || typeof body.notice.message !== 'string' || typeof body.notice.occurredAt !== 'string' || !Number.isFinite(Date.parse(body.notice.occurredAt))))
        || typeof body.checkedAt !== 'string' || !Number.isFinite(Date.parse(body.checkedAt))) throw new Error('The local API returned invalid protection status.')
      return body as BlockingStatus
    },
    setApplicationProtection: async (enabled) => {
      const body = await request('', 'PATCH', { applicationsEnabled: enabled,
        ...(enabled ? { consent: 'SUSPEND_MATCHED_APPS' } : {}) }, '/api/v1/blocking-protection') as Partial<ProtectionPreferences> | null
      if (!body || typeof body.applicationsEnabled !== 'boolean'
        || (body.applicationsEnabled && body.applicationConsentAt === null)
        || (body.applicationConsentAt !== null && (typeof body.applicationConsentAt !== 'string' || !Number.isFinite(Date.parse(body.applicationConsentAt)))))
        throw new Error('The local API returned invalid protection preferences.')
      return body as ProtectionPreferences
    },
    list: async () => {
      const body = await request()
      if (!Array.isArray(body) || !body.every(isRule)) throw new Error('The local API returned an invalid rule list.')
      return body
    },
    create: (input) => stored('', 'POST', input),
    patch: (id, input) => stored(`/${encodeURIComponent(id)}`, 'PATCH', input),
    delete: async (id) => { await request(`/${encodeURIComponent(id)}`, 'DELETE') },
  }
}

function validateWebsiteStatus(value: unknown): WebsiteProtectionStatus {
  const body = value as Partial<WebsiteProtectionStatus> | null
  let validUrl = body?.verificationUrl === null
  if (typeof body?.verificationUrl === 'string') {
    try {
      const url = new URL(body.verificationUrl)
      validUrl = url.protocol === 'http:' && url.hostname === 'focusdesktop.invalid' && !url.port && !url.username && !url.password
        && /^\/verify\/[0-9a-f-]{36}$/.test(url.pathname) && !url.search && !url.hash
    } catch { validUrl = false }
  }
  if (!body || typeof body.enabled !== 'boolean' || typeof body.routingVerified !== 'boolean'
    || !['DISABLED', 'STARTING', 'READY', 'VERIFIED', 'RESTORING', 'UNAVAILABLE'].includes(String(body.state))
    || (body.routingVerified && body.state !== 'VERIFIED') || !validUrl || typeof body.reason !== 'string'
    || (body.notice != null && (typeof body.notice.id !== 'string' || !['WARN', 'ERROR'].includes(body.notice.kind)
      || typeof body.notice.message !== 'string' || !Number.isFinite(Date.parse(body.notice.occurredAt)))))
    throw new Error('The local API returned invalid website protection status.')
  return body as WebsiteProtectionStatus
}
