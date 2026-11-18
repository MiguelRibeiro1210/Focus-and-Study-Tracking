import type { SessionHistoryQuery } from '../api/sessionTypes'

export type HistoryRange = '7 days' | '30 days' | 'All time'
export interface LocalDateRange { from: string; to: string }

function localDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function createHistoryQuery(
  range: HistoryRange,
  status: 'COMPLETED' | 'ABANDONED' | undefined,
  page: number,
  today = new Date(),
): SessionHistoryQuery {
  const query: SessionHistoryQuery = { page, size: 20 }
  if (status) query.status = status
  const dates = getHistoryDateRange(range, today)
  if (dates) Object.assign(query, dates)
  return query
}

export function getHistoryDateRange(range: HistoryRange, today = new Date()): LocalDateRange | null {
  if (range === 'All time') return null
  const to = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const from = new Date(to)
  from.setDate(from.getDate() - (range === '7 days' ? 6 : 29))
  return { from: localDateString(from), to: localDateString(to) }
}

export function validateLocalDateRange(from: string, to: string): string | null {
  if (!from || !to) return 'Choose both a start date and an end date.'
  const fromDate = new Date(`${from}T00:00:00`)
  const toDate = new Date(`${to}T00:00:00`)
  if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime())
    || localDateString(fromDate) !== from || localDateString(toDate) !== to) {
    return 'Enter valid start and end dates.'
  }
  if (from > to) return 'The start date must be on or before the end date.'
  const inclusiveDays = Math.round((Date.UTC(toDate.getFullYear(), toDate.getMonth(), toDate.getDate())
    - Date.UTC(fromDate.getFullYear(), fromDate.getMonth(), fromDate.getDate())) / 86_400_000) + 1
  if (inclusiveDays > 366) return 'Choose a date range of 366 days or less.'
  return null
}

export function formatHistoryDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatFocusDuration(totalSeconds: number): string {
  const totalMinutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours > 0) return `${hours} h ${String(minutes).padStart(2, '0')} min`
  if (totalMinutes > 0) return `${totalMinutes} min`
  return `${totalSeconds} sec`
}
