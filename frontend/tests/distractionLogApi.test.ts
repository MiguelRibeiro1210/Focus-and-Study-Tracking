import { expect, it, vi } from 'vitest'
import { createDistractionLogApi } from '../src/api/distractionLogApi'

const entry = { detectionId: 'one', sessionId: 'session', ruleId: 'rule', target: { type: 'WEBSITE', domain: 'example.com' },
  observationType: 'WEBSITE', application: null, host: 'example.com', category: 'OTHER', action: 'BLOCK',
  occurredAt: '2026-01-01T10:00:00Z', outcome: 'PENDING', outcomeAt: null, blocked: false, failureReason: null }
it('encodes combined history filters and validates truthful outcomes', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ items: [entry], total: 1, page: 0, size: 20 }))
    .mockResolvedValueOnce(Response.json({ items: [{ ...entry, blocked: true }], total: 1, page: 0, size: 20 }))
  const api = createDistractionLogApi(fetcher)
  await api.list({ page: 0, size: 20, sessionId: 'session', from: '2026-01-01' })
  expect(fetcher.mock.calls[0][0]).toContain('sessionId=session&from=2026-01-01')
  await expect(api.list({ page: 0, size: 20 })).rejects.toThrow('invalid distraction history')
})
it('preserves API failures rather than returning an empty history', async () => {
  const api = createDistractionLogApi(vi.fn().mockResolvedValue(Response.json({ message: 'Invalid date range' }, { status: 400 })))
  await expect(api.list({ page: 0, size: 20 })).rejects.toThrow('Invalid date range')
})
