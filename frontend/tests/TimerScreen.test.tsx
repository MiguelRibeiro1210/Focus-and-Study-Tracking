// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SessionResponse } from '../src/api/sessionTypes'
import { TimerScreen } from '../src/timer/TimerScreen'
import type { TimerController } from '../src/timer/useTimerSession'

afterEach(cleanup)

function makeSession(overrides: Partial<SessionResponse> = {}): SessionResponse {
  return {
    sessionId: 'session-1',
    sessionType: 'WORK',
    state: 'RUNNING',
    intervalType: 'WORK',
    intervalState: 'RUNNING',
    intervalSequence: 1,
    plannedDurationSeconds: 1500,
    actualDurationSeconds: 0,
    actualFocusDurationSeconds: 0,
    remainingSeconds: 1500,
    createdAt: '2026-11-11T00:00:00Z',
    startedAt: '2026-11-11T00:00:00Z',
    endedAt: null,
    ...overrides,
  }
}

function makeTimer(session: SessionResponse | null, overrides: Partial<TimerController> = {}): TimerController {
  return {
    session,
    remainingSeconds: session?.remainingSeconds ?? null,
    isLoading: false,
    pendingAction: null,
    error: null,
    runAction: vi.fn().mockResolvedValue(undefined),
    retry: vi.fn(),
    ...overrides,
  }
}

describe('timer screen', () => {
  it('shows idle state and starts a focus session', async () => {
    const user = userEvent.setup()
    const timer = makeTimer(null)
    render(<TimerScreen timer={timer} />)

    expect(screen.getByText('Ready to focus')).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toContain('—:—')
    expect(screen.queryByRole('button', { name: 'Pause session' })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Start focus session' }))
    expect(timer.runAction).toHaveBeenCalledWith('start')
  })

  it('renders running controls and confirms before stopping', async () => {
    const user = userEvent.setup()
    const timer = makeTimer(makeSession())
    render(<TimerScreen timer={timer} />)

    expect(screen.getByText('Focus interval')).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toContain('25:00')
    expect(screen.getByRole('button', { name: 'Pause session' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Stop' }))
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('This ends your current session')
    await user.click(screen.getByRole('button', { name: 'Keep going' }))
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Stop' }))
    expect(timer.runAction).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Stop' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('alertdialog')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Stop' }))
    await user.click(screen.getByRole('button', { name: 'Stop session' }))
    expect(timer.runAction).toHaveBeenCalledWith('stop')
  })

  it('renders paused state and resumes the session', async () => {
    const user = userEvent.setup()
    const timer = makeTimer(makeSession({ state: 'PAUSED', intervalState: 'RUNNING', remainingSeconds: 800 }))
    render(<TimerScreen timer={timer} />)
    expect(screen.getByText('Paused')).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toContain('13:20')
    expect(screen.getByRole('button', { name: 'Resume session' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Pause session' })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Resume session' }))
    expect(timer.runAction).toHaveBeenCalledWith('resume')
  })

  it('allows starting and skipping a pending break, but does not offer skip for pending work', async () => {
    const user = userEvent.setup()
    const timer = makeTimer(makeSession({
      state: 'WAITING_FOR_NEXT_INTERVAL', intervalType: 'SHORT_BREAK', intervalState: 'PENDING',
      intervalSequence: 2, remainingSeconds: 300, plannedDurationSeconds: 300,
    }))
    const { rerender } = render(<TimerScreen timer={timer} />)
    expect(screen.getByText('Short break')).toBeTruthy()
    expect(screen.getByText('Ready for next interval')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Start next interval' }))
    expect(timer.runAction).toHaveBeenCalledWith('startNextInterval')
    await user.click(screen.getByRole('button', { name: 'Skip break' }))
    expect(timer.runAction).toHaveBeenCalledWith('skipBreak')
    expect(screen.getByRole('button', { name: 'Start next interval' })).toBeTruthy()

    const pendingWork = makeTimer(makeSession({
      state: 'WAITING_FOR_NEXT_INTERVAL', intervalType: 'WORK', intervalState: 'PENDING',
      intervalSequence: 3,
    }))
    rerender(<TimerScreen timer={pendingWork} />)
    expect(screen.getByRole('button', { name: 'Start next interval' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Skip break' })).toBeNull()
  })

  it.each([
    ['COMPLETED', 'Session complete'],
    ['ABANDONED', 'Session ended'],
  ] as const)('renders terminal state %s and allows a new session', async (state, label) => {
    const user = userEvent.setup()
    const timer = makeTimer(makeSession({ state, intervalState: 'COMPLETED', endedAt: '2026-11-11T00:25:00Z' }))
    render(<TimerScreen timer={timer} />)
    expect(screen.getByText(label)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Start a new session' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Start a new session' }))
    expect(timer.runAction).toHaveBeenCalledWith('start')
  })

  it('shows the work interval progress and waits at zero for the backend transition', () => {
    render(<TimerScreen timer={makeTimer(makeSession({ intervalSequence: 3, remainingSeconds: 0 }), { remainingSeconds: 0 })} />)
    expect(screen.getByText('1 work interval completed')).toBeTruthy()
    expect(screen.getByText('Waiting for next interval')).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toContain('00:00')
  })

  it('disables controls while a command is pending and exposes retry for errors', async () => {
    const user = userEvent.setup()
    const timer = makeTimer(makeSession(), {
      pendingAction: 'pause',
      error: 'The local API is unavailable.',
    })
    render(<TimerScreen timer={timer} />)
    expect((screen.getByRole('button', { name: 'Please wait…' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByRole('alert').textContent).toContain('The local API is unavailable.')
    await user.click(screen.getByRole('button', { name: 'Retry sync' }))
    expect(timer.retry).toHaveBeenCalledOnce()
  })

  it('shows a loading message while recovering the active session', () => {
    render(<TimerScreen timer={makeTimer(null, { isLoading: true })} />)
    expect(screen.getByRole('status').textContent).toContain('Connecting to your local session')
    expect(screen.queryByRole('button', { name: 'Start focus session' })).toBeNull()
  })
})
