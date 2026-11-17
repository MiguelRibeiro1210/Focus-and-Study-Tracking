import type { SessionHistoryItem } from '../api/sessionTypes'
import type { HistoryRange } from './historyRange'

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

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

export function summarizeHistory(
  items: SessionHistoryItem[],
  range: HistoryRange,
  today = new Date(),
): HistoryDashboardSummary {
  const daily = new Map<string, HistoryDashboardDay>()
  const allDays = new Map<string, HistoryDashboardDay>()
  const weekStart = startOfDay(today)
  weekStart.setDate(weekStart.getDate() - 6)
  const week: HistoryDashboardDay[] = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart)
    date.setDate(date.getDate() + index)
    const day = { date, focusSeconds: 0, sessionCount: 0 }
    daily.set(dayKey(date), day)
    return day
  })
  const rangeStart = startOfDay(today)
  if (range === '7 days') rangeStart.setDate(rangeStart.getDate() - 6)
  if (range === '30 days') rangeStart.setDate(rangeStart.getDate() - 29)
  const rangeEnd = startOfDay(today)
  let focusSeconds = 0
  let completedSessions = 0

  for (const item of items) {
    const startedAt = new Date(item.startedAt)
    const dayDate = startOfDay(startedAt)
    if (range !== 'All time' && (dayDate < rangeStart || dayDate > rangeEnd)) continue
    focusSeconds += item.actualFocusDurationSeconds
    if (item.status === 'COMPLETED') completedSessions += 1
    const key = dayKey(dayDate)
    const allDay = allDays.get(key) ?? { date: dayDate, focusSeconds: 0, sessionCount: 0 }
    allDay.focusSeconds += item.actualFocusDurationSeconds
    allDay.sessionCount += 1
    allDays.set(key, allDay)
    const weekDay = daily.get(key)
    if (weekDay) {
      weekDay.focusSeconds += item.actualFocusDurationSeconds
      weekDay.sessionCount += 1
    }
  }

  const bestDay = [...allDays.values()].reduce<HistoryDashboardDay | null>(
    (best, day) => !best || day.focusSeconds > best.focusSeconds ? day : best,
    null,
  )

  return { focusSeconds, completedSessions, distractionsBlocked: 0, week, bestDay }
}

export function formatDashboardDuration(totalSeconds: number): string {
  const totalMinutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours > 0) return `${hours} h ${minutes} min`
  return `${totalMinutes} min`
}
