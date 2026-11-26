// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invoke } from '@tauri-apps/api/core'
import { nativeFetch } from '../src/api/nativeTransport'

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn(), isTauri: vi.fn() }))
vi.mock('@tauri-apps/api/event', () => ({ listen: vi.fn() }))

describe('native rule management transport', () => {
  beforeEach(() => vi.clearAllMocks())

  it('passes a partial rule edit to the local desktop bridge', async () => {
    vi.mocked(invoke).mockResolvedValue({ status: 200, body: { enabled: false } })
    const response = await nativeFetch('/api/v1/blocking-rules/rule-id', {
      method: 'PATCH', body: JSON.stringify({ enabled: false }),
    })
    expect(invoke).toHaveBeenCalledWith('api_request', {
      method: 'PATCH', path: '/api/v1/blocking-rules/rule-id', body: { enabled: false },
    })
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ enabled: false })
  })

  it('preserves validation status and field details for the client', async () => {
    const error = { code: 'VALIDATION_ERROR', details: ['target.domain: invalid domain'] }
    vi.mocked(invoke).mockResolvedValue({ status: 422, body: error })
    const response = await nativeFetch('/api/v1/blocking-rules/rule-id', {
      method: 'PATCH', body: JSON.stringify({ target: { type: 'WEBSITE', domain: 'bad/domain' } }),
    })
    expect(response.status).toBe(422)
    expect(await response.json()).toEqual(error)
  })

  it('returns a bodyless response after deleting a rule', async () => {
    vi.mocked(invoke).mockResolvedValue({ status: 204, body: null })
    const response = await nativeFetch('/api/v1/blocking-rules/rule-id', { method: 'DELETE' })
    expect(response.status).toBe(204)
    expect(await response.text()).toBe('')
  })
})
