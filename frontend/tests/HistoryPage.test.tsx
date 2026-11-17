// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SessionHistoryItem, SessionHistoryPage } from '../src/api/sessionTypes'
import { createHistoryQuery } from '../src/pages/historyRange'
import { HistoryPage } from '../src/pages/HistoryPage'

afterEach(cleanup)

function makeItem(overrides: Partial<SessionHistoryItem> = {}): SessionHistoryItem {
  return {
    sessionId: 'session-1',
    projectName: 'Exam preparation',
    sessionType: 'WORK',
    status: 'COMPLETED',
    startedAt: '2026-11-11T00:00:00Z',
    endedAt: '2026-11-11T00:25:00Z',
    actualFocusDurationSeconds: 1500,
    ...overrides,
  }
}

function makePage(items: SessionHistoryItem[] = [], page = 0, total = items.length): SessionHistoryPage {
  return { items, total, page, size: 20 }
}

describe('history page', () => {
  it('loads and renders paged session summaries with local date and focus duration', async () => {
    const api = { getSessionHistory: vi.fn().mockResolvedValue(makePage([makeItem()])) }
    render(<HistoryPage api={api} />)

    expect(screen.getByText('Loading session history…')).toBeTruthy()
    expect(await screen.findByText('Exam preparation')).toBeTruthy()
    expect(screen.getByRole('cell', { name: 'Completed' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '25 min' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'PROJECT' })).toBeTruthy()
    expect(screen.queryByRole('columnheader', { name: 'BLOCKED' })).toBeNull()
    expect(api.getSessionHistory).toHaveBeenCalledWith(expect.objectContaining({
      ...createHistoryQuery('7 days', undefined, 0),
      size: 20,
    }))
    expect(api.getSessionHistory).toHaveBeenCalledWith(expect.objectContaining({
      ...createHistoryQuery('7 days', undefined, 0), size: 100,
    }))
  })

  it('queries the selected date range and status, and resets pagination on filter changes', async () => {
    const user = userEvent.setup()
    const api = { getSessionHistory: vi.fn((query?: { size?: number; page?: number }) => Promise.resolve(
      query?.size === 100 ? makePage([makeItem()], 0, 1) : makePage([makeItem()], query?.page ?? 0, 41),
    )) }
    render(<HistoryPage api={api} />)
    await screen.findByText('Exam preparation')

    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await waitFor(() => expect(api.getSessionHistory).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 })))
    await user.click(screen.getByRole('button', { name: '30 days' }))
    await waitFor(() => expect(api.getSessionHistory).toHaveBeenCalledWith(expect.objectContaining({
      ...createHistoryQuery('30 days', undefined, 0),
      size: 20,
    })))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Session status' }), 'ABANDONED')
    await waitFor(() => expect(api.getSessionHistory).toHaveBeenCalledWith(expect.objectContaining({
      ...createHistoryQuery('30 days', 'ABANDONED', 0),
      size: 20,
    })))
    await user.click(screen.getByRole('button', { name: 'All time' }))
    await waitFor(() => expect(api.getSessionHistory).toHaveBeenCalledWith({ status: 'ABANDONED', page: 0, size: 20 }))
  })

  it('shows the first-use empty history state when no sessions exist at all', async () => {
    const user = userEvent.setup()
    const api = { getSessionHistory: vi.fn().mockResolvedValue(makePage()) }
    render(<HistoryPage api={api} />)
    expect(await screen.findByText('No sessions recorded yet.')).toBeTruthy()

    await user.click(screen.getByRole('button', { name: 'All time' }))
    expect(await screen.findByText('No sessions recorded yet.')).toBeTruthy()
  })

  it('keeps the custom date-range control available with a clear placeholder', async () => {
    const user = userEvent.setup()
    const api = { getSessionHistory: vi.fn().mockResolvedValue(makePage()) }
    render(<HistoryPage api={api} />)
    await user.click(screen.getByRole('button', { name: /Choose date range/ }))
    expect(screen.getByText('Custom date range: Not implemented yet.')).toBeTruthy()
  })

  it('distinguishes a date or status filter with no matches from an empty history', async () => {
    const user = userEvent.setup()
    const api = {
      getSessionHistory: vi.fn((query?: { from?: string; status?: string }) =>
        Promise.resolve(query?.from || query?.status ? makePage() : makePage([makeItem()]))),
    }
    render(<HistoryPage api={api} />)
    expect(await screen.findByText('No sessions match the selected range and status.')).toBeTruthy()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Session status' }), 'ABANDONED')
    expect(await screen.findByText('No sessions match the selected range and status.')).toBeTruthy()
  })

  it('shows a retryable error and retries the history request', async () => {
    const user = userEvent.setup()
    const apiResponses = [
      Promise.reject(new Error('offline')),
      Promise.resolve(makePage([makeItem()])),
      Promise.resolve(makePage([makeItem()])),
    ]
    const api = {
      getSessionHistory: vi.fn((query?: { size?: number }) => query?.size === 100
        ? Promise.resolve(makePage())
        : apiResponses.shift()!),
    }
    render(<HistoryPage api={api} />)
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('Could not load session history')
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Exam preparation')).toBeTruthy()
    expect(api.getSessionHistory.mock.calls.filter(([query]) => query?.size === 20)).toHaveLength(2)
  })
})
