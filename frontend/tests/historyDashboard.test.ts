import { describe, expect, it } from 'vitest'
import type { SessionHistoryItem } from '../src/api/sessionTypes'
import { formatDashboardDuration, summarizeHistory } from '../src/pages/historyDashboard'

function session(overrides: Partial<SessionHistoryItem> = {}): SessionHistoryItem {
  return {
    sessionId: 'one', projectName: 'Study', sessionType: 'WORK', status: 'COMPLETED',
    startedAt: '2026-11-15T10:00:00', endedAt: '2026-11-15T10:25:00', actualFocusDurationSeconds: 1500,
    ...overrides,
  }
}

describe('history dashboard aggregation', () => {
  const today = new Date(2026, 10, 17, 12)

  it('totals selected-range focus time, completed sessions, weekly bars, and best day', () => {
    const summary = summarizeHistory([
      session({ startedAt: '2026-11-16T10:00:00', actualFocusDurationSeconds: 1800 }),
      session({ sessionId: 'two', startedAt: '2026-11-16T11:00:00', status: 'ABANDONED', actualFocusDurationSeconds: 900 }),
      session({ sessionId: 'three', startedAt: '2026-11-10T10:00:00', actualFocusDurationSeconds: 6000 }),
    ], '7 days', today)

    expect(summary.focusSeconds).toBe(2700)
    expect(summary.completedSessions).toBe(1)
    expect(summary.distractionsBlocked).toBe(0)
    expect(summary.week.find((day) => day.date.getDate() === 16)?.focusSeconds).toBe(2700)
    expect(summary.bestDay?.date.getDate()).toBe(16)
    expect(summary.bestDay?.sessionCount).toBe(2)
  })

  it('returns zero totals and no best day for an empty range', () => {
    const summary = summarizeHistory([], '30 days', today)
    expect(summary.focusSeconds).toBe(0)
    expect(summary.completedSessions).toBe(0)
    expect(summary.bestDay).toBeNull()
    expect(summary.week.every((day) => day.focusSeconds === 0)).toBe(true)
  })

  it('formats durations in hours and minutes', () => {
    expect(formatDashboardDuration(6000)).toBe('1 h 40 min')
    expect(formatDashboardDuration(0)).toBe('0 min')
  })
})
