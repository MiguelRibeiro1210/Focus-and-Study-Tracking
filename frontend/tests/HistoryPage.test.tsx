// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { HistoryPage } from '../src/pages/HistoryPage'

afterEach(cleanup)

describe('history page controls', () => {
  it('selects a date range without inventing history data', async () => {
    const user = userEvent.setup()
    render(<HistoryPage />)

    const sevenDays = screen.getByRole('button', { name: '7 days' })
    const thirtyDays = screen.getByRole('button', { name: '30 days' })
    expect(sevenDays.getAttribute('aria-pressed')).toBe('true')
    await user.click(thirtyDays)
    expect(thirtyDays.getAttribute('aria-pressed')).toBe('true')
    expect(sevenDays.getAttribute('aria-pressed')).toBe('false')
    expect(screen.getByText('Completed sessions will appear here when history is available.')).toBeTruthy()
  })

  it('explains which history actions are not implemented yet', async () => {
    const user = userEvent.setup()
    render(<HistoryPage />)

    await user.click(screen.getByRole('button', { name: /Choose date range/ }))
    expect(screen.getByRole('status').textContent).toBe('Custom date range: Not implemented yet.')
    await user.click(screen.getByRole('button', { name: /Export CSV/ }))
    expect(screen.getByRole('status').textContent).toBe('CSV export: Not implemented yet.')
    await user.click(screen.getByRole('button', { name: /All statuses/ }))
    expect(screen.getByRole('status').textContent).toBe('Status filter: Not implemented yet.')
  })
})
