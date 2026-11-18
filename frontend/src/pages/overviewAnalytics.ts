import type { AnalyticsSummary } from '../api/sessionTypes'

export interface OverviewAnalytics {
  focusSecondsToday: number
  focusSecondsYesterday: number
  completedSessionsToday: number
}

export function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function mapOverviewAnalytics(summary: AnalyticsSummary, today: Date): OverviewAnalytics {
  const todayKey = localDateKey(today)
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayKey = localDateKey(yesterday)
  const todaySummary = summary.daily.find((day) => day.date === todayKey)
  const yesterdaySummary = summary.daily.find((day) => day.date === yesterdayKey)

  return {
    focusSecondsToday: todaySummary?.focusSeconds ?? 0,
    focusSecondsYesterday: yesterdaySummary?.focusSeconds ?? 0,
    completedSessionsToday: todaySummary?.completedSessions ?? 0,
  }
}
