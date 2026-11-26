import { describe, expect, it, vi } from 'vitest'
import { createBlockingRulesApi } from '../src/api/blockingRulesApi'

const rule = { id: 'one', displayName: 'Video', target: { type: 'WEBSITE', domain: 'youtube.com' },
  category: 'OTHER', action: 'BLOCK', enabled: true, createdAt: '2026-11-26T00:00:00Z', updatedAt: '2026-11-26T00:00:00Z' }

describe('blocking rules API', () => {
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
