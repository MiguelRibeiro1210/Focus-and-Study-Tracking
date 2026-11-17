import type { SessionHistoryQuery } from '../api/sessionTypes'

export type HistoryRange = '7 days' | '30 days' | 'All time'

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
  if (range !== 'All time') {
    const to = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    const from = new Date(to)
    from.setDate(from.getDate() - (range === '7 days' ? 6 : 29))
    query.from = localDateString(from)
    query.to = localDateString(to)
  }
  return query
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
