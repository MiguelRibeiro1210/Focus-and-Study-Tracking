import type { AnalyticsSummary } from '../api/sessionTypes'

export interface HistoryDashboardDay {
  date: Date
  focusSeconds: number
  sessionCount: number
}

export interface HistoryDashboardSummary {
  focusSeconds: number
  completedSessions: number
  distractionsBlocked: number
  week: HistoryDashboardDay[]
  bestDay: HistoryDashboardDay | null
}

function localDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function mapAnalyticsSummary(summary: AnalyticsSummary): HistoryDashboardSummary {
  const week = summary.daily.slice(-7).map((day) => ({
    date: localDate(day.date),
    focusSeconds: day.focusSeconds,
    sessionCount: day.completedSessions,
  }))
  const bestDay = summary.bestDay ? {
    date: localDate(summary.bestDay.date),
    focusSeconds: summary.bestDay.focusSeconds,
    sessionCount: summary.bestDay.completedSessions,
  } : null
  return {
    focusSeconds: summary.totalFocusSeconds,
    completedSessions: summary.completedSessions,
    distractionsBlocked: 0,
    week,
    bestDay,
  }
}

export function formatDashboardDuration(totalSeconds: number): string {
  const totalMinutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours > 0) return `${hours} h ${minutes} min`
  return `${totalMinutes} min`
}
