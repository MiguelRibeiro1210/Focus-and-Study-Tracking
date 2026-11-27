import { afterEach, describe, expect, it, vi } from 'vitest'
import { checkBlockingReadiness } from '../src/pages/blockingReadiness'
import { app, appsReady, client, site, webReady } from './blockingFixtures'

afterEach(() => vi.useRealTimers())
describe('session blocking readiness', () => {
  it('skips disabled, Log and Warn rules and does not request unrelated status', async () => {
    const api = client([{ ...site, enabled: false }, { ...app, action: 'LOG' }, { ...site, action: 'WARN' }])
    expect(await checkBlockingReadiness(api)).toEqual([])
    expect(api.status).not.toHaveBeenCalled(); expect(api.websiteStatus).not.toHaveBeenCalled()
    const websites = client([site]); await checkBlockingReadiness(websites); expect(websites.status).not.toHaveBeenCalled()
  })
  it('accepts idle application monitoring with zero suspensions and verified website routing', async () => {
    expect(await checkBlockingReadiness(client())).toEqual([])
  })
  it('reports both missing consent and browser connection without claiming all blocking is off', async () => {
    const result = await checkBlockingReadiness(client(undefined, {
      status: vi.fn().mockResolvedValue({ ...appsReady, applicationProtectionEnabled: false }),
      websiteStatus: vi.fn().mockResolvedValue({ ...webReady, routingVerified: false, state: 'READY' }),
    }))
    expect(result.map((item) => item.kind)).toEqual(['apps', 'websites'])
    expect(result[1].message).toContain('browser isn’t connected')
  })
  it('treats unavailable runtimes, permission failures, restoring and missing status as not ready', async () => {
    for (const status of [{ ...webReady, runtimeEnabled: false }, { ...webReady, runtimeEnabled: undefined }, { ...webReady, state: 'RESTORING' }, { ...webReady, braveLaunchPending: true }])
      expect(await checkBlockingReadiness(client([site], { websiteStatus: vi.fn().mockResolvedValue(status) }))).toHaveLength(1)
    expect(await checkBlockingReadiness(client([app], { status: vi.fn().mockResolvedValue({ ...appsReady, applicationDetection: 'UNAVAILABLE' }) }))).toHaveLength(1)
    expect(await checkBlockingReadiness(client([app], { status: undefined }))).toHaveLength(1)
  })
  it('makes failed reads and timeouts explicit and clears its timeout after success', async () => {
    expect((await checkBlockingReadiness(client([], { list: vi.fn().mockRejectedValue(new Error()) })))[0].kind).toBe('unknown')
    expect((await checkBlockingReadiness(client([site], { websiteStatus: vi.fn().mockRejectedValue(new Error()) })))[0].kind).toBe('websites')
    vi.useFakeTimers()
    const pending = checkBlockingReadiness(client([], { list: vi.fn().mockReturnValue(new Promise(() => {})) }))
    await vi.advanceTimersByTimeAsync(5000)
    expect((await pending)[0].kind).toBe('unknown')
    await checkBlockingReadiness(client([])); expect(vi.getTimerCount()).toBe(0)
  })
})
