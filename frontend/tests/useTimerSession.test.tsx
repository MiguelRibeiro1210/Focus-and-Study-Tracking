// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SessionApi } from '../src/api/sessionApi'
import type { SessionEventClientOptions } from '../src/api/sessionEvents'
import type { ActiveSession, SessionResponse } from '../src/api/sessionTypes'
import { useTimerSession } from '../src/timer/useTimerSession'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

function makeSession(overrides: Partial<SessionResponse> = {}): SessionResponse {
  return {
    sessionId: 'session-1', sessionType: 'WORK', state: 'RUNNING', intervalType: 'WORK',
    intervalState: 'RUNNING', intervalSequence: 1, plannedDurationSeconds: 60,
    actualDurationSeconds: 0, actualFocusDurationSeconds: 0, remainingSeconds: 60,
    createdAt: '2026-11-11T00:00:00Z', startedAt: '2026-11-11T00:00:00Z', endedAt: null,
    ...overrides,
  }
}

function makeApi(overrides: Partial<SessionApi> = {}): SessionApi {
  return {
    startSession: vi.fn().mockResolvedValue(makeSession()),
    getActiveSession: vi.fn().mockResolvedValue(null),
    pauseSession: vi.fn().mockResolvedValue(makeSession({ state: 'PAUSED' })),
    resumeSession: vi.fn().mockResolvedValue(makeSession()),
    stopSession: vi.fn().mockResolvedValue(makeSession({ state: 'COMPLETED', intervalState: 'COMPLETED' })),
    startNextInterval: vi.fn().mockResolvedValue(makeSession()),
    skipBreak: vi.fn().mockResolvedValue(makeSession()),
    ...overrides,
  }
}

let latestEventOptions: SessionEventClientOptions | undefined
let eventClient: { start: ReturnType<typeof vi.fn>; reconcile: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn> }

function fakeEventClientFactory(options: SessionEventClientOptions) {
  latestEventOptions = options
  eventClient = {
    start: vi.fn(),
    reconcile: vi.fn(() => {
      void options.api.getActiveSession().then(options.onSnapshot).catch(options.onError)
    }),
    stop: vi.fn(),
  }
  return eventClient
}

function Harness({ api }: { api: SessionApi }) {
  const timer = useTimerSession({ api, eventClientFactory: fakeEventClientFactory })
  return (
    <div>
      <output data-testid="session">{timer.session?.state ?? 'NONE'}</output>
      <output data-testid="seconds">{timer.remainingSeconds ?? 'none'}</output>
      <output data-testid="loading">{String(timer.isLoading)}</output>
      {timer.error && <div role="alert">{timer.error}</div>}
      <button type="button" onClick={() => void timer.runAction('start')}>Start</button>
      <button type="button" onClick={() => void timer.runAction('pause')}>Pause</button>
      <button type="button" onClick={() => void timer.runAction('stop')}>Stop</button>
      <button type="button" onClick={timer.retry}>Retry</button>
    </div>
  )
}

async function flushPromises() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

describe('timer session controller', () => {
  it('recovers the active session, counts down locally, polls at 30 seconds, and cleans up', async () => {
    vi.useFakeTimers()
    const api = makeApi({ getActiveSession: vi.fn().mockResolvedValue(makeSession({ remainingSeconds: 4 })) })
    const view = render(<Harness api={api} />)
    await flushPromises()
    expect(screen.getByTestId('session').textContent).toBe('RUNNING')
    expect(screen.getByTestId('seconds').textContent).toBe('4')
    expect(eventClient.start).toHaveBeenCalledOnce()
    expect(eventClient.reconcile).toHaveBeenCalledOnce()

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })
    expect(screen.getByTestId('seconds').textContent).toBe('3')
    expect(api.getActiveSession).toHaveBeenCalledTimes(1)

    await act(async () => { await vi.advanceTimersByTimeAsync(29_000) })
    expect(api.getActiveSession).toHaveBeenCalledTimes(2)
    view.unmount()
    expect(eventClient.stop).toHaveBeenCalledOnce()
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
    expect(api.getActiveSession).toHaveBeenCalledTimes(2)
  })

  it('dispatches actions through the API and reconciles after a successful command', async () => {
    const api = makeApi({
      getActiveSession: vi.fn()
        .mockResolvedValueOnce(makeSession())
        .mockResolvedValue(makeSession({ state: 'PAUSED', remainingSeconds: 42 })),
      pauseSession: vi.fn().mockResolvedValue(makeSession({ state: 'PAUSED', remainingSeconds: 42 })),
    })
    render(<Harness api={api} />)
    await flushPromises()
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    await flushPromises()
    expect(api.pauseSession).toHaveBeenCalledWith('session-1')
    expect(screen.getByTestId('session').textContent).toBe('PAUSED')
    expect(eventClient.reconcile).toHaveBeenCalledTimes(2)
  })

  it('holds at zero for the backend transition and freezes time while paused or waiting', async () => {
    vi.useFakeTimers()
    const api = makeApi({
      getActiveSession: vi.fn().mockResolvedValue(makeSession({ remainingSeconds: 1 })),
    })
    render(<Harness api={api} />)
    await flushPromises()

    await act(async () => { await vi.advanceTimersByTimeAsync(2_000) })
    expect(screen.getByTestId('seconds').textContent).toBe('0')
    expect(screen.getByTestId('session').textContent).toBe('RUNNING')
    expect(api.startNextInterval).not.toHaveBeenCalled()

    act(() => latestEventOptions?.onSnapshot(makeSession({ state: 'PAUSED', remainingSeconds: 17 })))
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(screen.getByTestId('seconds').textContent).toBe('17')

    act(() => latestEventOptions?.onSnapshot(makeSession({
      state: 'WAITING_FOR_NEXT_INTERVAL', intervalType: 'SHORT_BREAK', intervalState: 'PENDING',
      intervalSequence: 2, remainingSeconds: 300, plannedDurationSeconds: 300,
    })))
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(screen.getByTestId('seconds').textContent).toBe('300')
  })

  it('does not apply an old command response over a newer WebSocket snapshot', async () => {
    let resolvePause!: (snapshot: SessionResponse) => void
    const api = makeApi({
      getActiveSession: vi.fn().mockResolvedValue(makeSession({ state: 'COMPLETED', intervalState: 'COMPLETED' })),
      pauseSession: vi.fn(() => new Promise((resolve) => { resolvePause = resolve })),
    })
    render(<Harness api={api} />)
    await flushPromises()
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    act(() => latestEventOptions?.onSnapshot(makeSession({ state: 'COMPLETED', intervalState: 'COMPLETED' })))
    resolvePause(makeSession({ state: 'PAUSED' }))
    await flushPromises()
    expect(screen.getByTestId('session').textContent).toBe('COMPLETED')
  })

  it('keeps a terminal command response visible when active-session reconciliation returns 204', async () => {
    const terminal = makeSession({ state: 'ABANDONED', intervalState: 'COMPLETED', endedAt: '2026-11-11T00:01:00Z' })
    const api = makeApi({
      getActiveSession: vi.fn<() => Promise<ActiveSession>>()
        .mockResolvedValueOnce(makeSession())
        .mockResolvedValueOnce(null),
      stopSession: vi.fn().mockResolvedValue(terminal),
    })
    render(<Harness api={api} />)
    await flushPromises()
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
    await flushPromises()
    expect(screen.getByTestId('session').textContent).toBe('ABANDONED')
  })

  it('surfaces command errors and still attempts state reconciliation', async () => {
    const api = makeApi({
      getActiveSession: vi.fn().mockResolvedValue(makeSession()),
      pauseSession: vi.fn().mockRejectedValue(new Error('Conflict')),
    })
    render(<Harness api={api} />)
    await flushPromises()
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    await flushPromises()
    expect(screen.getByRole('alert').textContent).toContain('Could not synchronize')
    expect(eventClient.reconcile).toHaveBeenCalledTimes(2)
    expect(screen.getByTestId('session').textContent).toBe('RUNNING')
  })
})
