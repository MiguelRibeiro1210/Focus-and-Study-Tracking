import { describe, expect, it } from 'vitest'
import type { AnalyticsSummary } from '../src/api/sessionTypes'
import { formatDashboardDuration, mapAnalyticsSummary } from '../src/pages/historyDashboard'

function summary(overrides: Partial<AnalyticsSummary> = {}): AnalyticsSummary {
  return {
    from: '2026-11-11', to: '2026-11-17', totalFocusSeconds: 2700, totalBreakSeconds: 0,
    completedSessions: 1, averageSessionSeconds: 2700, focusScore: 100,
    bestDay: { date: '2026-11-16', focusSeconds: 2700, completedSessions: 1 },
    daily: [
      { date: '2026-11-11', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
      { date: '2026-11-12', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
      { date: '2026-11-13', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
      { date: '2026-11-14', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
      { date: '2026-11-15', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
      { date: '2026-11-16', focusSeconds: 2700, breakSeconds: 0, completedSessions: 1 },
      { date: '2026-11-17', focusSeconds: 0, breakSeconds: 0, completedSessions: 0 },
    ],
    ...overrides,
  }
}

describe('history dashboard aggregation', () => {
  it('maps the backend summary and its daily buckets to existing dashboard presentation', () => {
    const dashboard = mapAnalyticsSummary(summary())

    expect(dashboard.focusSeconds).toBe(2700)
    expect(dashboard.completedSessions).toBe(1)
    expect(dashboard.distractionsBlocked).toBe(0)
    expect(dashboard.week).toHaveLength(7)
    expect(dashboard.week.find((day) => day.date.getDate() === 16)?.focusSeconds).toBe(2700)
    expect(dashboard.bestDay?.date.getDate()).toBe(16)
    expect(dashboard.bestDay?.sessionCount).toBe(1)
  })

  it('uses only the most recent seven buckets for the existing weekly chart', () => {
    const fullRange = summary({ daily: Array.from({ length: 30 }, (_, index) => ({
      date: `2026-10-${String(index + 1).padStart(2, '0')}`,
      focusSeconds: index + 1, breakSeconds: 0, completedSessions: 1,
    })) })

    const dashboard = mapAnalyticsSummary(fullRange)

    expect(dashboard.week).toHaveLength(7)
    expect(dashboard.week[0].focusSeconds).toBe(24)
    expect(dashboard.week[6].focusSeconds).toBe(30)
  })

  it('keeps the empty-state values when the backend has no completed sessions', () => {
    const dashboard = mapAnalyticsSummary(summary({
      totalFocusSeconds: 0, completedSessions: 0, bestDay: null,
      daily: summary().daily.map((day) => ({ ...day, focusSeconds: 0, completedSessions: 0 })),
    }))
    expect(dashboard.focusSeconds).toBe(0)
    expect(dashboard.completedSessions).toBe(0)
    expect(dashboard.bestDay).toBeNull()
    expect(dashboard.week.every((day) => day.focusSeconds === 0)).toBe(true)
  })

  it('formats durations in hours and minutes', () => {
    expect(formatDashboardDuration(6000)).toBe('1 h 40 min')
    expect(formatDashboardDuration(0)).toBe('0 min')
  })
})
