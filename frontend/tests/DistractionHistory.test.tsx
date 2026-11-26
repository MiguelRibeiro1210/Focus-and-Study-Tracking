// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { DistractionHistory } from '../src/pages/DistractionHistory'
import type { DistractionLogEntry } from '../src/api/distractionLogApi'

afterEach(cleanup)
const entry: DistractionLogEntry = { detectionId: 'one', sessionId: 'session', ruleId: 'rule', target: { type: 'WEBSITE', domain: 'example.com' },
  observationType: 'WEBSITE', application: null, host: 'music.example.com', category: 'OTHER', action: 'BLOCK',
  occurredAt: '2026-01-01T10:00:00Z', outcome: 'PENDING', outcomeAt: null, blocked: false, failureReason: null }
it('distinguishes pending, failed, confirmed and log-only observations', async () => {
  const list = vi.fn().mockResolvedValue({ items: [entry,
    { ...entry, detectionId: 'two', outcome: 'FAILED', failureReason: 'Permission denied' },
    { ...entry, detectionId: 'three', outcome: 'BLOCKED', blocked: true },
    { ...entry, detectionId: 'four', action: 'LOG' }], total: 4, page: 0, size: 20 })
  render(<DistractionHistory api={{ list }} />)
  expect(await screen.findByText('Awaiting confirmation')).toBeTruthy()
  expect(screen.getByText('Blocked')).toBeTruthy()
  expect(screen.getByText('Blocking failed')).toBeTruthy()
  expect(screen.getByText('Permission denied')).toBeTruthy()
  expect(screen.getByText('Logged')).toBeTruthy()
})
it('keeps filters on read failure and retries without inventing an empty result', async () => {
  const user = userEvent.setup()
  const list = vi.fn().mockResolvedValueOnce({ items: [entry], total: 1, page: 0, size: 20 })
    .mockRejectedValueOnce(new Error('History unavailable'))
    .mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 20 })
  render(<DistractionHistory api={{ list }} />)
  await screen.findByText('Awaiting confirmation')
  await user.type(screen.getByLabelText('Distraction history from'), '2026-01-01')
  await user.click(screen.getByRole('button', { name: 'Apply dates' }))
  expect((await screen.findByRole('alert')).textContent).toContain('History unavailable')
  expect(screen.queryByText('No distraction observations match these filters.')).toBeNull()
  expect((screen.getByLabelText('Distraction history from') as HTMLInputElement).value).toBe('2026-01-01')
  await user.click(screen.getByRole('button', { name: 'Retry history' }))
  expect(await screen.findByText('No distraction observations match these filters.')).toBeTruthy()
  expect(list.mock.calls.at(-1)?.[0]).toMatchObject({ from: '2026-01-01', page: 0, size: 20 })
})
it('paginates and filters a selected session with a reset to the first page', async () => {
  const user = userEvent.setup()
  const list = vi.fn().mockImplementation(async (query) => ({ items: [entry], total: 21, page: query.page, size: 20 }))
  render(<DistractionHistory api={{ list }} />)
  await screen.findByText('Awaiting confirmation')
  await user.click(screen.getByRole('button', { name: 'Next observations' }))
  await screen.findByText(/Page 2 of 2/)
  await user.click(within(screen.getByRole('table')).getByRole('button', { name: 'Only this session' }))
  await screen.findByText('Showing one session.')
  expect(list.mock.calls.at(-1)?.[0]).toMatchObject({ sessionId: 'session', page: 0 })
  await user.click(screen.getByRole('button', { name: 'Show all sessions' }))
  await screen.findByText('Awaiting confirmation')
  expect(list.mock.calls.at(-1)?.[0].sessionId).toBeUndefined()
})
