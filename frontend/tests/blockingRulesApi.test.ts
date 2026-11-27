import { describe, expect, it, vi } from 'vitest'
import { createBlockingRulesApi } from '../src/api/blockingRulesApi'

const rule = { id: 'one', displayName: 'Video', target: { type: 'WEBSITE', domain: 'youtube.com' },
  category: 'OTHER', action: 'BLOCK', enabled: true, createdAt: '2026-11-26T00:00:00Z', updatedAt: '2026-11-26T00:00:00Z' }

describe('blocking rules API', () => {
  it('loads and validates local application identities and sends one atomic batch', async () => {
    const target = { type: 'APPLICATION', platform: 'LINUX', matchKind: 'APPLICATION_ID', value: 'flatpak:org.example.Chat' } as const
    const input = { displayName: 'Chat', target, action: 'BLOCK', category: 'OTHER', enabled: true } as const
    const catalog = { platform: 'LINUX', partial: false, warnings: [], scannedAt: rule.createdAt,
      applications: [{ id: 'chat', displayName: 'Chat', source: 'Flatpak', target, selectable: true, reason: null }] }
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json(catalog)).mockResolvedValueOnce(Response.json([{ ...rule, ...input }], { status: 201 }))
      .mockResolvedValueOnce(Response.json({ ...catalog, applications: [{ ...catalog.applications[0], target: null }] }))
    const api = createBlockingRulesApi(fetcher)
    expect((await api.applications!(true)).applications[0].target).toEqual(target)
    await api.createBatch!([input])
    expect(fetcher.mock.calls[0][0]).toBe('/api/v1/applications?refresh=true')
    expect(fetcher.mock.calls[1]).toEqual(['/api/v1/blocking-rules/batch', expect.objectContaining({ method: 'POST', body: JSON.stringify({ rules: [input] }) })])
    await expect(api.applications!()).rejects.toThrow('invalid application list')
  })
  it('rejects a partial batch success payload', async () => {
    const api = createBlockingRulesApi(vi.fn().mockResolvedValue(Response.json([], { status: 201 })))
    const input = { displayName: 'Chat', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'PROCESS_NAME', value: 'chat' }, category: 'OTHER', action: 'BLOCK', enabled: true } as const
    await expect(api.createBatch!([input])).rejects.toThrow('invalid saved application list')
  })
  it('sends explicit suspension consent only when enabling protection', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ applicationsEnabled: true, applicationConsentAt: '2026-11-26T00:00:00Z' }))
      .mockResolvedValueOnce(Response.json({ applicationsEnabled: false, applicationConsentAt: null }))
    const api = createBlockingRulesApi(fetcher)
    await api.setApplicationProtection!(true)
    await api.setApplicationProtection!(false)
    expect(fetcher.mock.calls[0][0]).toBe('/api/v1/blocking-protection')
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ applicationsEnabled: true, consent: 'SUSPEND_MATCHED_APPS' })
    expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual({ applicationsEnabled: false })
  })
  it('validates the diagnostic status response', async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json({ applicationEnforcement: true }))
    await expect(createBlockingRulesApi(fetcher).status!()).rejects.toThrow('invalid protection status')
    expect(fetcher.mock.calls[0][0]).toBe('/api/v1/blocking-status')
  })
  it('sends typed creation and partial edits, and accepts bodyless deletion', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json(rule, { status: 201 }))
      .mockResolvedValueOnce(Response.json({ ...rule, enabled: false }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const api = createBlockingRulesApi(fetcher)
    const { id: _id, createdAt: _created, updatedAt: _updated, ...input } = rule
    await api.create(input as Parameters<typeof api.create>[0])
    await api.patch('one', { enabled: false })
    await api.delete('one')
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual(input)
    expect(fetcher.mock.calls[1]).toEqual(['/api/v1/blocking-rules/one', expect.objectContaining({ method: 'PATCH', body: '{"enabled":false}' })])
    expect(fetcher.mock.calls[2][1].method).toBe('DELETE')
  })

  it('rejects invalid successful payloads rather than replacing saved state', async () => {
    const api = createBlockingRulesApi(vi.fn().mockResolvedValue(Response.json([{ ...rule, enabled: 'yes' }])))
    await expect(api.list()).rejects.toThrow('invalid rule')
  })

  it('preserves validation details and duplicate errors', async () => {
    const api = createBlockingRulesApi(vi.fn().mockResolvedValue(Response.json({ message: 'Duplicate rule', details: ['target: already exists'] }, { status: 409 })))
    await expect(api.patch('one', { enabled: true })).rejects.toThrow('target: already exists')
  })
})

it('sends separate website consent and rejects arbitrary verification links', async () => {
  const status = { enabled: true, state: 'READY', routingVerified: false,
    verificationUrl: 'http://focusdesktop.invalid/verify/00000000-0000-0000-0000-000000000001', reason: 'Verify routing.' }
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json(status))
    .mockResolvedValueOnce(Response.json({ ...status, verificationUrl: 'https://untrusted.example/' }))
  const api = createBlockingRulesApi(fetcher)
  await api.setWebsiteProtection!(true)
  expect(fetcher.mock.calls[0][0]).toBe('/api/v1/website-protection')
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ enabled: true, consent: 'CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC' })
  await expect(api.websiteStatus!()).rejects.toThrow('invalid website protection status')
})

it('sends explicit Brave routing and the fixed existing-profile setup confirmation', async () => {
  const status = { enabled: true, state: 'READY', routingVerified: false, verificationUrl: null, reason: 'Ready.',
    routingMethod: 'BRAVE', runtimeEnabled: true, braveAvailable: true, braveLaunchPending: false }
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json(status))
    .mockResolvedValueOnce(Response.json({ ...status, braveLaunchPending: true }, { status: 202 }))
    .mockResolvedValueOnce(Response.json({ ...status, routingMethod: 'ARBITRARY' }))
  const api = createBlockingRulesApi(fetcher)
  await api.setWebsiteProtection!(true, 'BRAVE')
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ enabled: true, consent: 'CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC', routingMethod: 'BRAVE' })
  expect((await api.setupBrave!()).braveLaunchPending).toBe(true)
  expect(fetcher.mock.calls[1]).toEqual(['/api/v1/website-protection/brave', expect.objectContaining({ method: 'POST', body: '{"confirmation":"OPEN_EXISTING_BRAVE_PROFILE"}' })])
  await expect(api.websiteStatus!()).rejects.toThrow('invalid website protection status')
})
