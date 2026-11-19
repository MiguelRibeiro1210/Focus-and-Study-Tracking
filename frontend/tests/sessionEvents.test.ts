import { afterEach, describe, expect, it, vi } from 'vitest'
import { createSessionEventClient } from '../src/api/sessionEvents'

const snapshot = {
  sessionId: 'session-1', projectName: 'Exam preparation', sessionType: 'WORK', state: 'RUNNING', intervalType: 'WORK',
  intervalState: 'RUNNING', intervalSequence: 1, plannedDurationSeconds: 1500,
  actualDurationSeconds: 0, actualFocusDurationSeconds: 0, remainingSeconds: 1500,
  createdAt: '2026-11-11T00:00:00Z', startedAt: '2026-11-11T00:00:00Z', endedAt: null,
}

class FakeSocket {
  onopen: (() => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  close = vi.fn(() => this.onclose?.())
  message(data: unknown) { this.onmessage?.({ data } as MessageEvent) }
  open() { this.onopen?.() }
}

function envelope(overrides: object = {}) {
  return JSON.stringify({ eventId: 'event-1', version: 1, type: 'session.started', occurredAt: 'now', payload: snapshot, ...overrides })
}

afterEach(() => vi.useRealTimers())

describe('session WebSocket client', () => {
  it('reconciles after connect and applies each recognized event id only once', async () => {
    const socket = new FakeSocket()
    const getActiveSession = vi.fn().mockResolvedValue(snapshot)
    const onSnapshot = vi.fn()
    const client = createSessionEventClient({
      api: { getActiveSession }, onSnapshot, createSocket: () => socket, getUrl: () => 'ws://local/ws',
    })
    client.start()
    socket.open()
    await vi.waitFor(() => expect(onSnapshot).toHaveBeenCalledWith(snapshot))
    onSnapshot.mockClear()
    socket.message(envelope())
    socket.message(envelope())
    expect(onSnapshot).toHaveBeenCalledTimes(1)
    expect(getActiveSession).toHaveBeenCalledTimes(1)
    client.stop()
  })

  it('reconciles on malformed/unknown events and reconnects with a bounded delay', async () => {
    vi.useFakeTimers()
    const sockets: FakeSocket[] = []
    const getActiveSession = vi.fn().mockResolvedValue(null)
    const client = createSessionEventClient({
      api: { getActiveSession }, onSnapshot: vi.fn(),
      createSocket: () => { const socket = new FakeSocket(); sockets.push(socket); return socket },
      getUrl: () => 'ws://local/ws',
    })
    client.start()
    sockets[0].message('{bad')
    sockets[0].message(envelope({ type: 'new.event', eventId: 'unknown' }))
    expect(getActiveSession).toHaveBeenCalledTimes(2)
    sockets[0].onclose?.()
    await vi.advanceTimersByTimeAsync(250)
    expect(sockets).toHaveLength(2)
    sockets[1].onclose?.()
    await vi.advanceTimersByTimeAsync(500)
    expect(sockets).toHaveLength(3)
    client.stop()
    expect(sockets[2].close).toHaveBeenCalled()
  })

  it('does not let an older reconciliation overwrite a newer WebSocket snapshot', async () => {
    let resolveActive!: (value: typeof snapshot) => void
    const getActiveSession = vi.fn(() => new Promise<typeof snapshot>((resolve) => { resolveActive = resolve }))
    const socket = new FakeSocket()
    const onSnapshot = vi.fn()
    const client = createSessionEventClient({ api: { getActiveSession }, onSnapshot, createSocket: () => socket, getUrl: () => 'ws://local/ws' })
    client.start()
    socket.open()
    socket.message(envelope({ eventId: 'newer' }))
    resolveActive(snapshot)
    await Promise.resolve()
    expect(onSnapshot).toHaveBeenCalledTimes(1)
    client.stop()
  })

  it('notifies once for each unique interval completion before applying its snapshot', () => {
    const socket = new FakeSocket()
    const calls: string[] = []
    const client = createSessionEventClient({
      api: { getActiveSession: vi.fn().mockResolvedValue(snapshot) },
      onIntervalCompleted: (type) => calls.push(`notify:${type}`),
      onSnapshot: () => calls.push('snapshot'),
      createSocket: () => socket,
      getUrl: () => 'ws://local/ws',
    })
    client.start()
    socket.message(envelope({ type: 'interval.completed', eventId: 'completed-1', completedIntervalType: 'WORK' }))
    socket.message(envelope({ type: 'interval.completed', eventId: 'completed-1', completedIntervalType: 'WORK' }))
    socket.message(envelope({ type: 'session.completed', eventId: 'session-ended' }))

    expect(calls).toEqual(['notify:WORK', 'snapshot', 'snapshot'])
    client.stop()
  })

  it('still delivers a completion from an older backend without the completed type field', () => {
    const socket = new FakeSocket()
    const onIntervalCompleted = vi.fn()
    const client = createSessionEventClient({
      api: { getActiveSession: vi.fn().mockResolvedValue(snapshot) },
      onIntervalCompleted,
      onSnapshot: vi.fn(),
      createSocket: () => socket,
      getUrl: () => 'ws://local/ws',
    })
    client.start()
    socket.message(envelope({ type: 'interval.completed', eventId: 'legacy-completed' }))

    expect(onIntervalCompleted).toHaveBeenCalledWith(undefined, snapshot)
    client.stop()
  })
})
