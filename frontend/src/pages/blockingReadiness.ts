import type { BlockingRulesApi } from '../api/blockingRulesApi'

export interface BlockingGap { kind: 'apps' | 'websites' | 'unknown'; message: string }
const unknown: BlockingGap = { kind: 'unknown', message: 'We couldn’t check your blocking setup. Your distractions might still be available.' }

async function inspect(api: BlockingRulesApi): Promise<BlockingGap[]> {
  const rules = (await api.list()).filter((rule) => rule.enabled && rule.action === 'BLOCK')
  if (!rules.length) return []
  const apps = rules.some((rule) => rule.target.type === 'APPLICATION')
  const websites = rules.some((rule) => rule.target.type === 'WEBSITE')
  const results = await Promise.all([
    apps ? (async (): Promise<BlockingGap | null> => {
      try {
        const status = await api.status?.()
        if (!status) return { kind: 'apps', message: 'We couldn’t check app blocking. Selected apps might still open.' }
        if (!status.applicationProtectionEnabled) return { kind: 'apps', message: 'App blocking is off. Turn it on to pause the apps you selected during focus time.' }
        if (!status.monitorEnabled || ['DISABLED', 'UNAVAILABLE'].includes(status.applicationDetection)
          || !['READY', 'ACTIVE'].includes(status.applicationProtectionState ?? ''))
          return { kind: 'apps', message: 'App blocking isn’t ready on this computer. Check its connection or permissions in Settings.' }
        return null
      } catch { return { kind: 'apps', message: 'We couldn’t check app blocking. Selected apps might still open.' } }
    })() : null,
    websites ? (async (): Promise<BlockingGap | null> => {
      try {
        const status = await api.websiteStatus?.()
        if (!status) return { kind: 'websites', message: 'We couldn’t check website blocking. Selected websites might still open.' }
        if (!status.enabled) return { kind: 'websites', message: 'Website blocking is off. Turn it on and connect your browser to block the websites you selected.' }
        if (status.runtimeEnabled !== true || ['UNAVAILABLE', 'RESTORING', 'DISABLED'].includes(status.state))
          return { kind: 'websites', message: 'Website blocking isn’t ready on this computer. Complete its setup in Settings.' }
        if (!status.routingVerified || status.state !== 'VERIFIED' || status.braveLaunchPending)
          return { kind: 'websites', message: 'Your browser isn’t connected yet. Connect it in Settings so your selected websites can be blocked.' }
        return null
      } catch { return { kind: 'websites', message: 'We couldn’t check website blocking. Selected websites might still open.' } }
    })() : null,
  ])
  return results.filter((gap): gap is BlockingGap => gap !== null)
}

export async function checkBlockingReadiness(api: BlockingRulesApi, timeoutMs = 5000): Promise<BlockingGap[]> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([inspect(api), new Promise<BlockingGap[]>((resolve) => { timer = setTimeout(() => resolve([unknown]), timeoutMs) })])
  } catch { return [unknown] }
  finally { clearTimeout(timer) }
}
