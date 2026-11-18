import { invoke } from '@tauri-apps/api/core'
import { isDesktopShell } from '../api/nativeTransport'
import type { SessionHistoryItem } from '../api/sessionTypes'

export function buildHistoryCsv(items: SessionHistoryItem[]): string {
  const escapeCsv = (value: string) => `"${value.replaceAll('"', '""')}"`
  const rows = [
    ['Project', 'Started at', 'Focus time (seconds)', 'Status'],
    ...items.map((item) => [item.projectName, item.startedAt, String(item.actualFocusDurationSeconds), item.status]),
  ]
  return rows.map((row) => row.map(escapeCsv).join(',')).join('\r\n')
}

export async function saveHistoryCsv(csv: string): Promise<boolean> {
  if (isDesktopShell()) return invoke<boolean>('save_history_csv', { csv })

  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'focus-history.csv'
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
  return true
}
