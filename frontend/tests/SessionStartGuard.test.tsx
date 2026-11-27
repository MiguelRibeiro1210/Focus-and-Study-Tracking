// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { SessionStartGuard } from '../src/timer/SessionStartGuard'
import type { TimerController } from '../src/timer/useTimerSession'
import { client, site, webReady } from './blockingFixtures'
const history = { getSessionHistory: vi.fn().mockResolvedValue({ items: [], page: 0, size: 3, total: 0 }) }
const timer: TimerController = { session: null, remainingSeconds: null, isLoading: false, pendingAction: null, error: null, runAction: vi.fn(), retry: vi.fn() }
const missing = () => client([site], { websiteStatus: vi.fn().mockResolvedValue({ ...webReady, enabled: false }) })
async function prepare(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByRole('textbox', { name: 'What will you work on?' }), 'Study')
  await user.type(screen.getByRole('spinbutton', { name: 'Work duration in minutes' }), '40')
}
afterEach(cleanup)
it('checks before starting and starts once only after explicit continuation with validated overrides', async () => {
  const user = userEvent.setup(), start = vi.fn().mockResolvedValue(undefined)
  render(<SessionStartGuard api={missing()} onStart={start} onSetup={vi.fn()} timerProps={{ timer, api: history }} />)
  await prepare(user); await user.click(screen.getByRole('button', { name: 'Start focus session' }))
  expect(await screen.findByRole('alertdialog')).toBeTruthy(); expect(start).not.toHaveBeenCalled()
  await user.dblClick(screen.getByRole('button', { name: 'Start without blocking' }))
  expect(start).toHaveBeenCalledTimes(1); expect(start).toHaveBeenCalledWith('Study', { workSeconds: 2400 })
})
it('cancels with Escape or Cancel, traps focus and restores the start button', async () => {
  const user = userEvent.setup(), start = vi.fn()
  render(<SessionStartGuard api={missing()} onStart={start} onSetup={vi.fn()} timerProps={{ timer, api: history }} />)
  await prepare(user); const button = screen.getByRole('button', { name: 'Start focus session' }); await user.click(button)
  await screen.findByRole('alertdialog'); expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Set up blocking' }))
  await user.keyboard('{Shift>}{Tab}{/Shift}'); expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Start without blocking' }))
  await user.keyboard('{Escape}'); expect(start).not.toHaveBeenCalled(); expect(document.activeElement).toBe(button)
  await user.click(button); await user.click(await screen.findByRole('button', { name: 'Cancel' })); expect(start).not.toHaveBeenCalled()
})
it('opens Settings without starting and rechecks on each attempt', async () => {
  const user = userEvent.setup(), start = vi.fn(), setup = vi.fn(), api = missing()
  render(<SessionStartGuard api={api} onStart={start} onSetup={setup} timerProps={{ timer, api: history }} />)
  await prepare(user); await user.click(screen.getByRole('button', { name: 'Start focus session' }))
  await user.click(await screen.findByRole('button', { name: 'Set up blocking' })); expect(setup).toHaveBeenCalledOnce(); expect(start).not.toHaveBeenCalled()
  vi.mocked(api.websiteStatus!).mockResolvedValue(webReady)
  await user.click(screen.getByRole('button', { name: 'Start focus session' })); expect(start).toHaveBeenCalledOnce(); expect(api.list).toHaveBeenCalledTimes(2)
})
it('starts directly for no Block rules and validates inputs before checking', async () => {
  const user = userEvent.setup(), start = vi.fn(), api = client([])
  render(<SessionStartGuard api={api} onStart={start} onSetup={vi.fn()} timerProps={{ timer, api: history }} />)
  await user.click(screen.getByRole('button', { name: 'Start focus session' })); expect(api.list).not.toHaveBeenCalled()
  await prepare(user); await user.click(screen.getByRole('button', { name: 'Start focus session' })); expect(start).toHaveBeenCalledOnce(); expect(screen.queryByRole('alertdialog')).toBeNull()
})
it('ignores late readiness after navigation and prevents concurrent checks', async () => {
  const user = userEvent.setup(), start = vi.fn(); let resolve!: (value: []) => void
  const api = client([], { list: vi.fn().mockImplementation(() => new Promise<[]>((done) => { resolve = done })) })
  const view = render(<SessionStartGuard api={api} onStart={start} onSetup={vi.fn()} timerProps={{ timer, api: history }} />)
  await prepare(user); await user.dblClick(screen.getByRole('button', { name: 'Start focus session' })); expect(api.list).toHaveBeenCalledOnce()
  view.unmount(); await act(async () => resolve([])); expect(start).not.toHaveBeenCalled()
})
