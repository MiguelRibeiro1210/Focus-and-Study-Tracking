// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { ProtectionFeedback } from '../src/app/ProtectionFeedback'
import type { BlockingRulesApi } from '../src/api/blockingRulesApi'

afterEach(cleanup)
it('shows a current local protection failure on every page and allows dismissal', async () => {
  const api = { status: vi.fn().mockResolvedValue({ notice: { id: 'failure', kind: 'ERROR',
    message: 'Application suspension failed; check permissions.', occurredAt: new Date().toISOString() }, ownedRestrictions: 0 }) } as unknown as BlockingRulesApi
  render(<ProtectionFeedback api={api} />)
  expect((await screen.findByRole('alert')).textContent).toContain('check permissions')
  await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss protection message' }))
  expect(screen.queryByRole('alert')).toBeNull()
})

it('does not repeat old warnings and reports confirmed active restrictions', async () => {
  const api = { status: vi.fn().mockResolvedValue({ notice: { id: 'old', kind: 'WARN', message: 'Old warning',
    occurredAt: new Date(Date.now() - 30000).toISOString() }, ownedRestrictions: 2 }) } as unknown as BlockingRulesApi
  render(<ProtectionFeedback api={api} />)
  expect(await screen.findByText('2 applications suspended during focus.')).toBeTruthy()
  expect(screen.queryByText('Old warning')).toBeNull()
})
