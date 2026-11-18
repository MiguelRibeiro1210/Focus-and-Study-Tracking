// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invoke } from '@tauri-apps/api/core'
import { isDesktopShell } from '../src/api/nativeTransport'
import { buildHistoryCsv, saveHistoryCsv } from '../src/pages/historyCsv'
import type { SessionHistoryItem } from '../src/api/sessionTypes'

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn() }))
vi.mock('../src/api/nativeTransport', () => ({ isDesktopShell: vi.fn(), nativeFetch: vi.fn() }))

const item: SessionHistoryItem = {
  sessionId: 'one', projectName: 'Exam, "Final"', sessionType: 'WORK', status: 'COMPLETED',
  startedAt: '2026-11-11T00:00:00Z', endedAt: '2026-11-11T00:25:00Z', actualFocusDurationSeconds: 1500,
}

describe('history CSV export', () => {
  beforeEach(() => vi.clearAllMocks())

  it('escapes CSV fields and preserves the existing export columns', () => {
    expect(buildHistoryCsv([item])).toBe([
      '"Project","Started at","Focus time (seconds)","Status"',
      '"Exam, ""Final""","2026-11-11T00:00:00Z","1500","COMPLETED"',
    ].join('\r\n'))
  })

  it('saves through the native Tauri command and returns its cancellation result', async () => {
    vi.mocked(isDesktopShell).mockReturnValue(true)
    vi.mocked(invoke).mockResolvedValueOnce(true).mockResolvedValueOnce(false)

    await expect(saveHistoryCsv('csv-data')).resolves.toBe(true)
    await expect(saveHistoryCsv('csv-data')).resolves.toBe(false)
    expect(invoke).toHaveBeenNthCalledWith(1, 'save_history_csv', { csv: 'csv-data' })
  })

  it('keeps browser downloads working when running outside the desktop shell', async () => {
    vi.mocked(isDesktopShell).mockReturnValue(false)
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:history')
    const revokeUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const click = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '', download: '', click,
    } as unknown as HTMLAnchorElement)

    await expect(saveHistoryCsv('csv-data')).resolves.toBe(true)

    expect(createUrl).toHaveBeenCalledWith(expect.any(Blob))
    expect(click).toHaveBeenCalledOnce()
    expect(invoke).not.toHaveBeenCalled()
    await new Promise((resolve) => window.setTimeout(resolve, 0))
    expect(revokeUrl).toHaveBeenCalledWith('blob:history')
  })
})
