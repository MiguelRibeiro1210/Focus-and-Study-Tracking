import { vi } from 'vitest'
import type { BlockingRule, BlockingRulesApi, BlockingStatus, WebsiteProtectionStatus } from '../src/api/blockingRulesApi'

export const site: BlockingRule = { id: 'site', displayName: 'YouTube', target: { type: 'WEBSITE', domain: 'youtube.com' }, category: 'OTHER', action: 'BLOCK', enabled: true, createdAt: '', updatedAt: '' }
export const app: BlockingRule = { ...site, id: 'app', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'PROCESS_NAME', value: 'chat' } }
export const appsReady: BlockingStatus = { monitorEnabled: true, applicationDetection: 'IDLE', applicationReason: '', applicationEnforcement: false, websiteEnforcement: false, checkedAt: '', applicationProtectionEnabled: true, applicationProtectionState: 'READY', ownedRestrictions: 0 }
export const webReady: WebsiteProtectionStatus = { enabled: true, runtimeEnabled: true, state: 'VERIFIED', routingVerified: true, verificationUrl: null, reason: '' }
export function client(rules = [site, app], overrides: Partial<BlockingRulesApi> = {}): BlockingRulesApi {
  return { list: vi.fn().mockResolvedValue(rules), status: vi.fn().mockResolvedValue(appsReady), websiteStatus: vi.fn().mockResolvedValue(webReady), create: vi.fn(), patch: vi.fn(), delete: vi.fn(), ...overrides }
}
