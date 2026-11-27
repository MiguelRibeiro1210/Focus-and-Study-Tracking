// @vitest-environment jsdom
import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DistractionsPage } from '../src/pages/DistractionsPage'
import type { ApplicationCatalog, BlockingRule, BlockingRulesApi } from '../src/api/blockingRulesApi'

afterEach(cleanup)
const website: BlockingRule = { id: 'site', displayName: 'Video', target: { type: 'WEBSITE', domain: 'youtube.com' },
  category: 'OTHER', action: 'BLOCK', enabled: true, createdAt: '2026-11-26T00:00:00Z', updatedAt: '2026-11-26T00:00:00Z' }
const app: BlockingRule = { ...website, id: 'app', displayName: 'Chat', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'PROCESS_NAME', value: 'chat' } }
const packaged: BlockingRule = { ...app, id: 'packaged', displayName: 'Packaged Chat', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'APPLICATION_ID', value: 'flatpak:org.example.Chat' } }
const music: BlockingRule = { ...app, id: 'music', displayName: 'Music', target: { type: 'APPLICATION', platform: 'LINUX', matchKind: 'EXECUTABLE_PATH', value: '/usr/bin/music' } }
const catalog: ApplicationCatalog = { platform: 'LINUX', partial: false, warnings: [], scannedAt: website.createdAt, applications: [
  ...[app, packaged, music].map((rule) => ({ id: rule.id, displayName: rule.displayName, source: rule.id === 'packaged' ? 'Flatpak' : 'Desktop app', target: rule.target as Extract<BlockingRule['target'], { type: 'APPLICATION' }>, selectable: true, reason: null })),
  { id: 'launcher', displayName: 'Shared Launcher', source: 'Desktop app', target: null, selectable: false, reason: 'Add this app manually.' },
] }
function api(overrides: Partial<BlockingRulesApi> = {}): BlockingRulesApi {
  return { list: vi.fn().mockResolvedValue([website, app]), create: vi.fn().mockResolvedValue(website),
    patch: vi.fn().mockResolvedValue({ ...website, enabled: false }), delete: vi.fn().mockResolvedValue(undefined), ...overrides }
}

describe('saved distraction rules', () => {
  async function openPicker(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByRole('button', { name: /Add distraction/ })
    await user.click(screen.getByRole('button', { name: /Add distraction/ }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target type' }), 'APPLICATION')
  }
  it('searches installed apps and adds multiple selections in a single atomic request', async () => {
    const user = userEvent.setup()
    const client = api({ applications: vi.fn().mockResolvedValue(catalog), createBatch: vi.fn().mockResolvedValue([packaged, music]) })
    render(<DistractionsPage api={client} />)
    await screen.findByText('Video'); await openPicker(user)
    const existing = await screen.findByRole('checkbox', { name: 'Select Chat (Desktop app)' })
    expect((existing as HTMLInputElement).disabled).toBe(true)
    expect((existing as HTMLInputElement).checked).toBe(true)
    expect((screen.getByRole('checkbox', { name: 'Select Shared Launcher (Desktop app)' }) as HTMLInputElement).disabled).toBe(true)
    await user.click(screen.getByRole('checkbox', { name: 'Select Packaged Chat (Flatpak)' }))
    await user.type(screen.getByRole('searchbox', { name: 'Search applications' }), 'Music')
    expect(screen.queryByRole('checkbox', { name: 'Select Packaged Chat (Flatpak)' })).toBeNull()
    await user.click(screen.getByRole('checkbox', { name: 'Select Music (Desktop app)' }))
    await user.clear(screen.getByRole('searchbox', { name: 'Search applications' }))
    expect((screen.getByRole('checkbox', { name: 'Select Packaged Chat (Flatpak)' }) as HTMLInputElement).checked).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Add selected (2)' }))
    await screen.findByRole('button', { name: 'Edit Packaged Chat' })
    expect(client.createBatch).toHaveBeenCalledOnce()
    expect(client.createBatch).toHaveBeenCalledWith([packaged, music].map(({ displayName, target }) => ({ displayName, target, action: 'BLOCK', enabled: true, category: 'OTHER' })))
    expect(client.create).not.toHaveBeenCalled()
    expect(screen.queryByRole('region', { name: 'Choose applications' })).toBeNull()
  })
  it('retains selections and reloads duplicates after an atomic save conflict', async () => {
    const user = userEvent.setup()
    const client = api({ list: vi.fn().mockResolvedValueOnce([app]).mockResolvedValueOnce([app, packaged]), applications: vi.fn().mockResolvedValue(catalog),
      createBatch: vi.fn().mockRejectedValueOnce(new Error('Already added (HTTP 409)')).mockResolvedValueOnce([music]) })
    render(<DistractionsPage api={client} />)
    await screen.findByText('Chat'); await openPicker(user)
    await user.click(await screen.findByRole('checkbox', { name: 'Select Packaged Chat (Flatpak)' }))
    await user.click(screen.getByRole('checkbox', { name: 'Select Music (Desktop app)' }))
    await user.click(screen.getByRole('button', { name: 'Add selected (2)' }))
    await screen.findByText('Already added (HTTP 409)')
    const duplicate = screen.getByRole('checkbox', { name: 'Select Packaged Chat (Flatpak)' }) as HTMLInputElement
    expect(duplicate.checked).toBe(true); expect(duplicate.disabled).toBe(true)
    expect((screen.getByRole('checkbox', { name: 'Select Music (Desktop app)' }) as HTMLInputElement).checked).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Add selected (1)' }))
    await screen.findByRole('button', { name: 'Edit Music' })
    expect(client.createBatch).toHaveBeenLastCalledWith([expect.objectContaining({ displayName: 'Music' })])
  })
  it('uses the detected platform for manual fallback and keeps single-rule editing', async () => {
    const user = userEvent.setup()
    const client = api({ applications: vi.fn().mockResolvedValue(catalog), create: vi.fn().mockResolvedValue(music) })
    render(<DistractionsPage api={client} />)
    await screen.findByText('Video'); await openPicker(user)
    await screen.findByRole('checkbox', { name: 'Select Music (Desktop app)' })
    await user.click(screen.getByRole('button', { name: 'App not listed? Add manually' }))
    expect((screen.getByRole('combobox', { name: 'Platform' }) as HTMLSelectElement).value).toBe('LINUX')
    await user.type(screen.getByRole('textbox', { name: 'Display name' }), 'Music')
    await user.type(screen.getByRole('textbox', { name: 'Application identifier' }), '/usr/bin/music')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    await screen.findByRole('button', { name: 'Edit Music' })
    expect(client.create).toHaveBeenCalledWith(expect.objectContaining({ target: music.target }))
  })
  it('retries failed discovery, shows partial results and refreshes an empty catalog', async () => {
    const user = userEvent.setup()
    const applications = vi.fn().mockRejectedValueOnce(new Error('Discovery unavailable')).mockResolvedValueOnce({ ...catalog, partial: true, warnings: ['Source unavailable'] })
      .mockResolvedValueOnce({ ...catalog, applications: [] })
    render(<DistractionsPage api={api({ applications })} />)
    await screen.findByText('Video'); await openPicker(user)
    await screen.findByText('Discovery unavailable')
    expect(screen.getByRole('button', { name: 'App not listed? Add manually' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    await screen.findByText('Some apps could not be listed. Refresh or add a missing app manually.')
    expect(screen.getByRole('checkbox', { name: 'Select Music (Desktop app)' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('No applications were found. Add an app manually.')
    expect(applications).toHaveBeenLastCalledWith(true)
  })
  it('preserves installed app identity while editing its name and action', async () => {
    const user = userEvent.setup(); const client = api({ list: vi.fn().mockResolvedValue([packaged]), patch: vi.fn().mockResolvedValue({ ...packaged, action: 'WARN' }) })
    render(<DistractionsPage api={client} />)
    await user.click(await screen.findByRole('button', { name: 'Edit Packaged Chat' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Action' }), 'WARN')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))
    expect(client.patch).toHaveBeenCalledWith('packaged', expect.objectContaining({ target: packaged.target, action: 'WARN' }))
  })
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
