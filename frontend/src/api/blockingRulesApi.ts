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
export interface BlockingRulesApi {
  list(): Promise<BlockingRule[]>
  create(input: BlockingRuleInput): Promise<BlockingRule>
  patch(id: string, input: Partial<BlockingRuleInput>): Promise<BlockingRule>
  delete(id: string): Promise<void>
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
  async function request(path = '', method = 'GET', input?: Partial<BlockingRuleInput>): Promise<unknown> {
    const response = await requestFetch(`/api/v1/blocking-rules${path}`, {
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
