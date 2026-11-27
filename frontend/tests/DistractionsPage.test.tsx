// @vitest-environment jsdom
import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DistractionsPage } from '../src/pages/DistractionsPage'
import type { BlockingRule, BlockingRulesApi } from '../src/api/blockingRulesApi'

afterEach(cleanup)
const website: BlockingRule = { id: 'site', displayName: 'Video', target: { type: 'WEBSITE', domain: 'youtube.com' },
  category: 'OTHER', action: 'BLOCK', enabled: true, createdAt: '2026-11-26T00:00:00Z', updatedAt: '2026-11-26T00:00:00Z' }
const app: BlockingRule = { ...website, id: 'app', displayName: 'Chat', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'PROCESS_NAME', value: 'chat' } }
function api(overrides: Partial<BlockingRulesApi> = {}): BlockingRulesApi {
  return { list: vi.fn().mockResolvedValue([website, app]), create: vi.fn().mockResolvedValue(website),
    patch: vi.fn().mockResolvedValue({ ...website, enabled: false }), delete: vi.fn().mockResolvedValue(undefined), ...overrides }
}

describe('saved distraction rules', () => {
  it('keeps saved rules separate from setup and links to Settings', async () => {
    const user = userEvent.setup(); const open = vi.fn(); const status = vi.fn()
    render(<DistractionsPage api={api({ status })} onOpenSettings={open} />)
    expect(await screen.findByText('Video')).toBeTruthy()
    expect(screen.queryByRole('switch', { name: 'Block apps' })).toBeNull()
    expect(screen.queryByRole('switch', { name: 'Block websites' })).toBeNull()
    expect(status).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Open blocking settings' })); expect(open).toHaveBeenCalledOnce()
  })
  it('disables concurrent mutations until a toggle is confirmed by storage', async () => {
    const user = userEvent.setup()
    let complete!: (rule: BlockingRule) => void
    const client = api({ patch: vi.fn().mockImplementation(() => new Promise<BlockingRule>((resolve) => { complete = resolve })) })
    render(<DistractionsPage api={client} />)
    const toggle = await screen.findByRole('switch', { name: 'Enable Video' })
    await user.click(toggle)
    expect((toggle as HTMLButtonElement).disabled).toBe(true)
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    expect((screen.getByRole('button', { name: 'Edit Chat' }) as HTMLButtonElement).disabled).toBe(true)
    await act(async () => complete({ ...website, enabled: false }))
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    expect((toggle as HTMLButtonElement).disabled).toBe(false)
  })

  it('rejects website URLs locally and cancels deletion without changing storage', async () => {
    const user = userEvent.setup()
    const client = api()
    render(<DistractionsPage api={client} />)
    await screen.findByText('Video')
    await user.click(screen.getByRole('button', { name: 'Delete Video' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(client.delete).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /Add distraction/ }))
    await user.type(screen.getByRole('textbox', { name: 'Display name' }), 'Invalid URL')
    await user.type(screen.getByRole('textbox', { name: 'Domain' }), 'https://example.org/path')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    expect((await screen.findByRole('alert')).textContent).toContain('without a URL')
    expect(client.create).not.toHaveBeenCalled()
  })

  it('loads rules, combines type filters with target search, and distinguishes configuration from protection', async () => {
    const user = userEvent.setup()
    render(<DistractionsPage api={api()} />)
    await screen.findByText('Video')
    await user.click(screen.getByRole('button', { name: 'Apps' }))
    expect(screen.queryByText('Video')).toBeNull()
    await user.type(screen.getByRole('searchbox'), 'chat')
    expect(screen.getByText('Chat')).toBeTruthy()
    await user.clear(screen.getByRole('searchbox'))
    await user.type(screen.getByRole('searchbox'), 'youtube')
    expect(screen.getByText('No rules match these filters.')).toBeTruthy()
  })

  it('creates a website and edits it to a typed application target', async () => {
    const user = userEvent.setup()
    const client = api({ list: vi.fn().mockResolvedValue([]), patch: vi.fn().mockResolvedValue(app) })
    render(<DistractionsPage api={client} />)
    await screen.findByText('No distraction rules have been added.')
    await user.click(screen.getByRole('button', { name: /Add distraction/ }))
    await user.type(screen.getByRole('textbox', { name: 'Display name' }), 'Video')
    await user.type(screen.getByRole('textbox', { name: 'Domain' }), 'youtube.com')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    expect(client.create).toHaveBeenCalledWith(expect.objectContaining({ target: { type: 'WEBSITE', domain: 'youtube.com' } }))
    await user.click(await screen.findByRole('button', { name: 'Edit Video' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target type' }), 'APPLICATION')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Platform' }), 'LINUX')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Identify by' }), 'PROCESS_NAME')
    await user.type(screen.getByRole('textbox', { name: 'Application identifier' }), 'chat')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Action' }), 'WARN')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    expect(client.patch).toHaveBeenCalledWith('site', expect.objectContaining({ action: 'WARN',
      target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'PROCESS_NAME', value: 'chat' } }))
  })

  it('keeps the previous toggle and row after failed mutations and confirms deletion', async () => {
    const user = userEvent.setup()
    const client = api({ list: vi.fn().mockResolvedValue([website]), patch: vi.fn().mockRejectedValue(new Error('Permission denied')),
      delete: vi.fn().mockRejectedValueOnce(new Error('Delete failed')).mockResolvedValueOnce(undefined) })
    render(<DistractionsPage api={client} />)
    const toggle = await screen.findByRole('switch', { name: 'Enable Video' })
    await user.click(toggle)
    expect((await screen.findByRole('alert')).textContent).toContain('Permission denied')
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    await user.click(screen.getByRole('button', { name: 'Delete Video' }))
    expect(client.delete).not.toHaveBeenCalled()
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Confirm delete' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Delete failed')
    expect(screen.getByText('Video')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))
    await screen.findByText('No distraction rules have been added.')
  })

  it('retries initial loading errors and preserves form values after a rejected save', async () => {
    const user = userEvent.setup()
    const client = api({ list: vi.fn().mockRejectedValueOnce(new Error('Offline')).mockResolvedValueOnce([]),
      create: vi.fn().mockRejectedValue(new Error('Duplicate target')) })
    render(<DistractionsPage api={client} />)
    await screen.findByText('Offline')
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    await screen.findByText('No distraction rules have been added.')
    await user.click(screen.getByRole('button', { name: /Add distraction/ }))
    await user.type(screen.getByRole('textbox', { name: 'Display name' }), 'Saved input')
    await user.type(screen.getByRole('textbox', { name: 'Domain' }), 'example.org')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    await screen.findByText('Duplicate target')
    expect((screen.getByRole('textbox', { name: 'Display name' }) as HTMLInputElement).value).toBe('Saved input')
  })
})
